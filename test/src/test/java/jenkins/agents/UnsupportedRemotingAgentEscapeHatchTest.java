package jenkins.agents;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import hudson.EnvVars;
import hudson.model.FreeStyleProject;
import hudson.model.Agent;
import hudson.agents.ComputerLauncher;
import hudson.agents.AgentComputer;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SimpleCommandLauncher;

/**
 * Test for the escape hatch for unsupported Remoting agent versions
 */
public class UnsupportedRemotingAgentEscapeHatchTest {

    @Rule public JenkinsRule j = new JenkinsRuleWithUnsupportedAgent();

    @Rule public TemporaryFolder tmpDir = new TemporaryFolder();

    @Rule
    public TestRule allowUnsupportedRemotingVersions = FlagRule.systemProperty(
            AgentComputer.class.getName() + ".allowUnsupportedRemotingVersions",
            Boolean.toString(true));

    private File agentJar;

    @Before
    public void extractAgent() throws Exception {
        agentJar = new File(tmpDir.getRoot(), "unsupported-agent.jar");
        FileUtils.copyURLToFile(UnsupportedRemotingAgentEscapeHatchTest.class.getResource("/old-remoting/remoting-unsupported.jar"), agentJar);
    }

    @Issue("JENKINS-50211")
    @Test
    public void shouldBeAbleToConnectAgentWithUnsupportedVersionWithEscapeHatch() throws Exception {
        Agent agent = j.createOnlineAgent();
        assertThat(agent.toComputer().getLog(), containsString("The Remoting version is older than the minimum required version"));
        assertThat(agent.toComputer().getLog(), containsString("The connection will be allowed, but compatibility is NOT guaranteed"));

        // Ensure we are able to run something on the agent
        FreeStyleProject project = j.createFreeStyleProject("foo");
        project.setAssignedLabel(agent.getSelfLabel());
        project.getBuildersList().add(agent.getComputer().isUnix()
                ? new Shell("echo Hello")
                : new BatchFile("echo 'hello'"));
        j.buildAndAssertSuccess(project);
    }

    private class JenkinsRuleWithUnsupportedAgent extends JenkinsRule {
        @Override
        public ComputerLauncher createComputerLauncher(EnvVars env) throws URISyntaxException, IOException {
            int sz = this.jenkins.getNodes().size();
            return new SimpleCommandLauncher(
                    String.format(
                            "\"%s/bin/java\" %s -jar \"%s\"",
                            System.getProperty("java.home"),
                            SLAVE_DEBUG_PORT > 0
                                    ? " -Xdebug -Xrunjdwp:transport=dt_socket,server=y,address="
                                            + (SLAVE_DEBUG_PORT + sz)
                                    : "",
                            agentJar.getAbsolutePath()));
        }
    }
}
