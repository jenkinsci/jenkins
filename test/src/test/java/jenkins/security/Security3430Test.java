package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import hudson.Functions;
import hudson.model.Computer;
import hudson.remoting.Channel;
import hudson.remoting.Launcher;
import hudson.slaves.SlaveComputer;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.List;
import java.util.logging.LogRecord;
import jenkins.bouncycastle.api.InstallBouncyCastleJCAProvider;
import jenkins.slaves.RemotingVersionInfo;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.InboundAgentExtension;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;
import org.kohsuke.stapler.Stapler;

class Security3430Test {

    @RegisterExtension
    private final RealJenkinsExtension jj = new RealJenkinsExtension();

    @RegisterExtension
    private final InboundAgentExtension agents = new InboundAgentExtension();

    @TempDir
    private File tmp;

    @Test
    void runWithOldestSupportedAgentJar() throws Throwable {
        assumeFalse(Functions.isWindows() && System.getenv("CI") != null, "Low value, high cost on Windows");
        runWithRemoting(RemotingVersionInfo.getMinimumSupportedVersion().toString(), "/old-remoting/remoting-minimum-supported.jar", true);
    }

    @Test
    void runWithCurrentAgentJar() throws Throwable {
        assumeFalse(Functions.isWindows() && System.getenv("CI") != null, "Low value, high cost on Windows");
        runWithRemoting(Launcher.VERSION, null, false);
    }

    private void runWithRemoting(String expectedRemotingVersion, String remotingResourcePath, boolean requestingJarFromAgent) throws Throwable {
        jj.startJenkins();
        final String agentName = "agent1";
        try {
            createAgent(agentName, remotingResourcePath);
            jj.runRemotely(Security3430Test::_run, agentName, expectedRemotingVersion, requestingJarFromAgent);
        } finally {
            agents.stop(jj, agentName);
        }
    }

    private void createAgent(String name, String remotingResourcePath) throws Throwable {
        if (remotingResourcePath != null) {
            var jar = newFile(tmp, name + ".jar");
            FileUtils.copyURLToFile(Security3430Test.class.getResource(remotingResourcePath), jar);
            // TODO awkward, especially as InboundAgentRule.getAgentArguments is private;
            // would be helpful to have an option for a specific agent JAR:
            var opts = InboundAgentExtension.Options.newBuilder().name(name).skipStart().build();
            agents.createAgent(jj, opts);
            agents.start(new InboundAgentExtension.AgentArguments(jar, jj.getUrl().toString(), name, jj.runRemotely(Security3430Test::getJnlpMac, name), 1, List.of()), opts);
        } else {
            agents.createAgent(jj, InboundAgentExtension.Options.newBuilder().name(name).build());
        }
    }

    private static String getJnlpMac(JenkinsRule r, String name) {
        return ((SlaveComputer) r.jenkins.getComputer(name)).getJnlpMac();
    }

    /**
     *
     * @param agentName the name of the agent we're working with
     * @param expectedRemotingVersion The version expected for remoting, or {@code null} if we're using whatever is bundled with this Jenkins.
     * @param requestingJarFromAgent {@code true} if and only if we expect to go through {@code ClassLoaderProxy#fetchJar}
     */
    private static void _run(JenkinsRule j, String agentName, String expectedRemotingVersion, Boolean requestingJarFromAgent) throws Throwable {
        final Computer computer = j.jenkins.getComputer(agentName);
        assertThat(computer, instanceOf(SlaveComputer.class));
        SlaveComputer agent = (SlaveComputer) computer;
        j.waitOnline(agent.getNode());
        final Channel channel = agent.getChannel();
        if (expectedRemotingVersion != null) {
            final String result = channel.call(new AgentVersionCallable());
            assertThat(result, is(expectedRemotingVersion));
        }

        { // regular behavior
            // it works
            assertTrue(channel.preloadJar(j.jenkins.getPluginManager().uberClassLoader, Stapler.class));
            // Identify that a jar was already loaded:
            assertFalse(channel.preloadJar(j.jenkins.getPluginManager().uberClassLoader, Stapler.class));
        }

        assertTrue(j.jenkins.getPluginManager().getPlugin("bouncycastle-api").isActive());
        InstallBouncyCastleJCAProvider.on(channel);
        channel.call(new ConfirmBouncyCastleLibrary());

        { // Exploitation tests
            final URL secretKeyFile = new File(j.jenkins.getRootDir(), "secret.key").toURI().toURL();
            final String expectedContent = IOUtils.toString(secretKeyFile, StandardCharsets.UTF_8);
            // Protection is effective when agents request non-jar files:
            if (expectedRemotingVersion == null) {
                assertThrows(NoSuchMethodException.class, () -> channel.call(new Exploit(secretKeyFile, expectedContent)));
            } else {
                final InvocationTargetException itex = assertThrows(InvocationTargetException.class, () -> channel.call(new Exploit(secretKeyFile, expectedContent)));
                assertThat(itex.getCause(), instanceOf(IOException.class));
            }
        }
        { // Support for pre-2024-08 remoting has been dropped, so even formerly legitimate jar files cannot be requested anymore:
            final URLClassLoader classLoader = (URLClassLoader) j.jenkins.getPluginManager().getPlugin("bouncycastle-api").classLoader;
            URL safeUrl = classLoader.getURLs()[0];
            final String expectedContent = IOUtils.toString(safeUrl, StandardCharsets.UTF_8);
            if (expectedRemotingVersion == null) {
                assertThrows(NoSuchMethodException.class, () -> channel.call(new Exploit(safeUrl, expectedContent)));
            } else {
                final InvocationTargetException itex = assertThrows(InvocationTargetException.class, () -> channel.call(new Exploit(safeUrl, expectedContent)));
                assertThat(itex.getCause(), instanceOf(IOException.class));
            }
        }
    }

    private static class AgentVersionCallable extends MasterToSlaveCallable<String, Exception> {
        @Override
        public String call() throws Exception {
            return Launcher.VERSION;
        }
    }

    private static class ConfirmBouncyCastleLibrary extends MasterToSlaveCallable<Void, Exception> {
        @Override
        public Void call() throws Exception {
            assertNotNull(Security.getProvider("BC"));
            return null;
        }
    }

    private static class Exploit extends MasterToSlaveCallable<Void, Exception> {
        private final URL controllerFilePath;
        private final String expectedContent;

        Exploit(URL controllerFilePath, String expectedContent) {
            this.controllerFilePath = controllerFilePath;
            this.expectedContent = expectedContent;
        }

        @Override
        public Void call() throws Exception {
            final ClassLoader ccl = Thread.currentThread().getContextClassLoader();
            final Field classLoaderProxyField = ccl.getClass().getDeclaredField("proxy");
            classLoaderProxyField.setAccessible(true);
            final Object theProxy = classLoaderProxyField.get(ccl);
            final Method fetchJarMethod = theProxy.getClass().getDeclaredMethod("fetchJar", URL.class);
            fetchJarMethod.setAccessible(true);
            final byte[] fetchJarResponse = (byte[]) fetchJarMethod.invoke(theProxy, controllerFilePath);
            assertThat(new String(fetchJarResponse, StandardCharsets.UTF_8), is(expectedContent));
            return null;
        }
    }

    // Would be nice if LoggerRule#recorded equivalents existed for use without LoggerRule, meanwhile:
    private static Matcher<LogRecord> logMessageContainsString(String needle) {
        return new LogMessageContainsString(containsString(needle));
    }

    private static final class LogMessageContainsString extends TypeSafeMatcher<LogRecord> {
        private final Matcher<String> stringMatcher;

        LogMessageContainsString(Matcher<String> stringMatcher) {
            this.stringMatcher = stringMatcher;
        }

        @Override
        protected boolean matchesSafely(LogRecord item) {
            return stringMatcher.matches(item.getMessage());
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("a LogRecord with a message matching ");
            stringMatcher.describeTo(description);
        }

        @Override
        protected void describeMismatchSafely(LogRecord item, Description mismatchDescription) {
            mismatchDescription.appendText("a LogRecord with the message: ");
            mismatchDescription.appendText(item.getMessage());
        }
    }

    private static File newFile(File parent, String child) throws IOException {
        File result = new File(parent, child);
        result.createNewFile();
        return result;
    }
}
