package jenkins.agents;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThrows;

import hudson.EnvVars;
import hudson.model.Agent;
import hudson.agents.ComputerLauncher;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SimpleCommandLauncher;

/**
 * Test for unsupported Remoting agent versions
 */
public class UnsupportedRemotingAgentTest {

    @Rule public JenkinsRule j = new JenkinsRuleWithUnsupportedAgent();

    @Rule public TemporaryFolder tmpDir = new TemporaryFolder();

    private File agentJar;

    @Before
    public void extractAgent() throws Exception {
        agentJar = new File(tmpDir.getRoot(), "unsupported-agent.jar");
        FileUtils.copyURLToFile(UnsupportedRemotingAgentTest.class.getResource("/old-remoting/remoting-unsupported.jar"), agentJar);
    }

    @Issue("JENKINS-50211")
    @Test
    public void shouldNotBeAbleToConnectAgentWithUnsupportedVersion() throws Exception {
        Agent agent = j.createAgent();
        ExecutionException e = assertThrows(ExecutionException.class, () -> agent.toComputer().connect(false).get());
        assertThat(e.getCause(), instanceOf(IOException.class));
        assertThat(e.getMessage(), containsString("Agent failed to connect"));
        assertThat(agent.toComputer().getLog(), containsString("Rejecting the connection because the Remoting version is older than the minimum required version"));
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
