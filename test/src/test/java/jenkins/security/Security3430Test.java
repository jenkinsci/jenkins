package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import hudson.model.Computer;
import hudson.remoting.Channel;
import hudson.remoting.Launcher;
import hudson.slaves.SlaveComputer;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.logging.LogRecord;
import jenkins.bouncycastle.api.InstallBouncyCastleJCAProvider;
import jenkins.slaves.RemotingVersionInfo;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.InboundAgentRule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;
import org.kohsuke.stapler.Stapler;

public class Security3430Test {
    @Rule
    public RealJenkinsRule jj = new RealJenkinsRule();

    @Rule
    public InboundAgentRule agents = new InboundAgentRule();

    @Test
    public void runWithOldestSupportedAgentJar() throws Throwable {
        runWithRemoting(RemotingVersionInfo.getMinimumSupportedVersion().toString(), "/old-remoting/remoting-minimum-supported.jar");
    }

    @Test
    public void runWithCurrentAgentJar() throws Throwable {
        runWithRemoting(null, null);
    }

    private void runWithRemoting(String expectedRemotingVersion, String remotingResourcePath) throws Throwable {
        if (expectedRemotingVersion != null) {
            FileUtils.copyURLToFile(Security3430Test.class.getResource(remotingResourcePath), new File(jj.getHome(), "agent.jar"));
        }

        jj.startJenkins();
        final String agentName = "agent1";
        try {
            agents.createAgent(jj, InboundAgentRule.Options.newBuilder().name(agentName).build());
            jj.runRemotely(Security3430Test::_run, agentName, expectedRemotingVersion, true);
        } finally {
            agents.stop(jj, agentName);
        }
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
                assertThat(itex.getCause(), instanceOf(IllegalStateException.class));
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
                assertThat(itex.getCause(), instanceOf(IllegalStateException.class));
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

        public Exploit(URL controllerFilePath, String expectedContent) {
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

        public LogMessageContainsString(Matcher<String> stringMatcher) {
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
}
