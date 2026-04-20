package jenkins.slaves;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.EnvVars;
import hudson.model.Slave;
import hudson.slaves.ComputerLauncher;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SimpleCommandLauncher;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

/**
 * Test for unsupported Remoting agent versions
 */
class UnsupportedRemotingAgentTest {

    @RegisterExtension
    private final JenkinsSessionExtension session = new JenkinsExtensionWithUnsupportedAgent();

    @TempDir
    private File tmpDir;

    private static File agentJar;

    @BeforeEach
    void extractAgent() throws Exception {
        agentJar = new File(tmpDir, "unsupported-agent.jar");
        FileUtils.copyURLToFile(UnsupportedRemotingAgentTest.class.getResource("/old-remoting/remoting-unsupported.jar"), agentJar);
    }

    @Issue("JENKINS-50211")
    @Test
    void shouldNotBeAbleToConnectAgentWithUnsupportedVersion() throws Throwable {
        session.then(j -> {
            Slave agent = j.createSlave();
            ExecutionException e = assertThrows(ExecutionException.class, () -> agent.toComputer().connect(false).get());
            assertThat(e.getCause(), instanceOf(IOException.class));
            assertThat(e.getMessage(), containsString("Agent failed to connect"));
            assertThat(agent.toComputer().getLog(), containsString("Rejecting the connection because the Remoting version is older than the minimum required version"));
        });
    }

    private static class JenkinsExtensionWithUnsupportedAgent extends JenkinsSessionExtension {

        private int port;
        private org.junit.runner.Description description;

        @Override
        public void beforeEach(ExtensionContext context) {
            super.beforeEach(context);
            description = org.junit.runner.Description.createTestDescription(
                    context.getTestClass().map(Class::getName).orElse(null),
                    context.getTestMethod().map(Method::getName).orElse(null),
                    context.getTestMethod().map(Method::getAnnotations).orElse(null));
        }

        @Override
        public void then(Step s) throws Throwable {
            CustomJenkinsRule r = new CustomJenkinsRule(getHome(), port);
            r.apply(
                    new org.junit.runners.model.Statement() {
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
