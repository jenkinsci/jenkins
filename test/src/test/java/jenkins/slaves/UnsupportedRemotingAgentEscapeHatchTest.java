package jenkins.slaves;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import hudson.EnvVars;
import hudson.model.FreeStyleProject;
import hudson.model.Slave;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SimpleCommandLauncher;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

/**
 * Test for the escape hatch for unsupported Remoting agent versions
 */
class UnsupportedRemotingAgentEscapeHatchTest {

    @RegisterExtension
    private final JenkinsSessionExtension session = new JenkinsExtensionWithUnsupportedAgent();

    @TempDir
    private File tmpDir;

    private String allowUnsupportedRemotingVersions;

    private static File agentJar;

    @BeforeEach
    void setUp() throws Exception {
        allowUnsupportedRemotingVersions = System.setProperty(SlaveComputer.class.getName() + ".allowUnsupportedRemotingVersions", "true");
        agentJar = new File(tmpDir, "unsupported-agent.jar");
        FileUtils.copyURLToFile(UnsupportedRemotingAgentEscapeHatchTest.class.getResource("/old-remoting/remoting-unsupported.jar"), agentJar);
    }

    @AfterEach
    void tearDown() {
        if (allowUnsupportedRemotingVersions != null) {
            System.setProperty(SlaveComputer.class.getName() + ".allowUnsupportedRemotingVersions", allowUnsupportedRemotingVersions);
        } else {
            System.clearProperty(SlaveComputer.class.getName() + ".allowUnsupportedRemotingVersions");
        }
    }

    @Issue("JENKINS-50211")
    @Test
    void shouldBeAbleToConnectAgentWithUnsupportedVersionWithEscapeHatch() throws Throwable {
        session.then(j -> {
            Slave agent = j.createOnlineSlave();
            assertThat(agent.toComputer().getLog(), containsString("The Remoting version is older than the minimum required version"));
            assertThat(agent.toComputer().getLog(), containsString("The connection will be allowed, but compatibility is NOT guaranteed"));

            // Ensure we are able to run something on the agent
            FreeStyleProject project = j.createFreeStyleProject("foo");
            project.setAssignedLabel(agent.getSelfLabel());
            project.getBuildersList().add(agent.getComputer().isUnix()
                    ? new Shell("echo Hello")
                    : new BatchFile("echo 'hello'"));
            j.buildAndAssertSuccess(project);
        });
    }

    private static class JenkinsExtensionWithUnsupportedAgent extends JenkinsSessionExtension {

        private int port;
        private Description description;

        @Override
        public void beforeEach(ExtensionContext context) {
            super.beforeEach(context);
            description = Description.createTestDescription(
                    context.getTestClass().map(Class::getName).orElse(null),
                    context.getTestMethod().map(Method::getName).orElse(null),
                    context.getTestMethod().map(Method::getAnnotations).orElse(null));
        }

        @Override
        public void then(Step s) throws Throwable {
            CustomJenkinsRule r = new CustomJenkinsRule(getHome(), port);
            r.apply(
                    new Statement() {
                        @Override
                        public void evaluate() throws Throwable {
                            port = r.getPort();
                            s.run(r);
                        }
                    },
                    description
            ).evaluate();
        }

        private static final class CustomJenkinsRule extends JenkinsRule {

            CustomJenkinsRule(File home, int port) {
                with(() -> home);
                localPort = port;
            }

            int getPort() {
                return localPort;
            }

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
}
