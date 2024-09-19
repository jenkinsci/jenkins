package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import hudson.ExtensionList;
import hudson.model.Computer;
import hudson.remoting.Channel;
import hudson.remoting.Launcher;
import hudson.slaves.SlaveComputer;
import hudson.util.RingBufferLogHandler;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import jenkins.bouncycastle.api.InstallBouncyCastleJCAProvider;
import jenkins.security.s2m.JarURLValidatorImpl;
import jenkins.slaves.RemotingVersionInfo;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.InboundAgentRule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;
import org.kohsuke.args4j.Argument;
import org.kohsuke.stapler.Stapler;

public class Security3430Test {
    @Rule
    public RealJenkinsRule jj = new RealJenkinsRule().withLogger(JarURLValidatorImpl.class, Level.FINEST);

    @Rule
    public InboundAgentRule agents = new InboundAgentRule();

    @Test
    public void runWithOldestSupportedAgentJar() throws Throwable {
        runWithRemoting(RemotingVersionInfo.getMinimumSupportedVersion().toString(), "/old-remoting/remoting-minimum-supported.jar", true);
    }

    @Test
    public void runWithPreviousAgentJar() throws Throwable {
        runWithRemoting("3256.v88a_f6e922152", "/old-remoting/remoting-before-SECURITY-3430-fix.jar", true);
    }

    @Ignore("TODO Expected: is an empty collection; but: <[Allowing URL: file:/…/test/target/webroot…/WEB-INF/lib/stapler-1903.v994a_db_314d58.jar, Determined to be core jar: file:/…/test/target/webroot…/WEB-INF/lib/stapler-1903.v994a_db_314d58.jar]>")
    @Test
    public void runWithCurrentAgentJar() throws Throwable {
        runWithRemoting(null, null, false);
    }

    private void runWithRemoting(String expectedRemotingVersion, String remotingResourcePath, boolean requestingJarFromAgent) throws Throwable {
        if (expectedRemotingVersion != null) {
            // TODO brittle; rather call InboundAgentRule.start(AgentArguments, Options) with a known agentJar
            FileUtils.copyURLToFile(Security3430Test.class.getResource(remotingResourcePath), new File(System.getProperty("java.io.tmpdir"), "agent.jar"));
        }

        jj.startJenkins();
        final String agentName = "agent1";
        try {
            agents.createAgent(jj, InboundAgentRule.Options.newBuilder().name(agentName).build());
            jj.runRemotely(Security3430Test::_run, agentName, expectedRemotingVersion, requestingJarFromAgent, true);
        } finally {
            agents.stop(jj, agentName);
        }
        jj.runRemotely(Security3430Test::disableJarURLValidatorImpl);
        final String agentName2 = "agent2";
        try {
            agents.createAgent(jj, InboundAgentRule.Options.newBuilder().name(agentName2).build());
            jj.runRemotely(Security3430Test::_run, agentName2, expectedRemotingVersion, requestingJarFromAgent, false);
        } finally {
            agents.stop(jj, agentName2);
        }
    }

    // This is quite artificial, but demonstrating that without JarURLValidatorImpl we do not allow any calls from the agent:
    private static void disableJarURLValidatorImpl(JenkinsRule j) {
        assertTrue(ExtensionList.lookup(ChannelConfigurator.class).remove(ExtensionList.lookupSingleton(JarURLValidatorImpl.class)));
    }

    /**
     *
     * @param agentName the name of the agent we're working with
     * @param expectedRemotingVersion The version expected for remoting, or {@code null} if we're using whatever is bundled with this Jenkins.
     * @param requestingJarFromAgent {@code true} if and only if we expect to go through {@code ClassLoaderProxy#fetchJar}
     * @param hasJenkinsJarURLValidator {@code true} if and only we do not expect {@link jenkins.security.s2m.JarURLValidatorImpl} to be present. Only relevant when {@code requestingJarFromAgent} is {@code true}.
     */
    private static void _run(JenkinsRule j, String agentName, String expectedRemotingVersion, Boolean requestingJarFromAgent, Boolean hasJenkinsJarURLValidator) throws Throwable {
        final RingBufferLogHandler logHandler = new RingBufferLogHandler(50);
        Logger.getLogger(JarURLValidatorImpl.class.getName()).addHandler(logHandler);
        final List<LogRecord> logRecords = logHandler.getView();

        final Computer computer = j.jenkins.getComputer(agentName);
        assertThat(computer, instanceOf(SlaveComputer.class));
        SlaveComputer agent = (SlaveComputer) computer;
        final Channel channel = agent.getChannel();
        if (expectedRemotingVersion != null) {
            final String result = channel.call(new AgentVersionCallable());
            assertThat(result, is(expectedRemotingVersion));
        }

        logHandler.clear();

        { // regular behavior
            if (hasJenkinsJarURLValidator) {
                // it works
                assertTrue(channel.preloadJar(j.jenkins.getPluginManager().uberClassLoader, Stapler.class));
                if (requestingJarFromAgent) {
                    assertThat(logRecords, hasItem(logMessageContainsString("Allowing URL: file:/")));
                } else {
                    assertThat(logRecords.stream().map(LogRecord::getMessage).toList(), is(empty()));
                }

                logHandler.clear();
                assertFalse(channel.preloadJar(j.jenkins.getPluginManager().uberClassLoader, Stapler.class));
                assertThat(logRecords, not(hasItem(logMessageContainsString("Allowing URL"))));
                assertThat(logRecords, not(hasItem(logMessageContainsString("Rejecting URL"))));
            } else {
                // outdated remoting.jar will fail, but up to date one passes
                if (requestingJarFromAgent) {
                    final IOException ex = assertThrows(IOException.class, () -> channel.preloadJar(j.jenkins.getPluginManager().uberClassLoader, Stapler.class));
                    assertThat(ex.getMessage(), containsString("No hudson.remoting.JarURLValidator has been set for this channel, so all #fetchJar calls are rejected. This is likely a bug in Jenkins. As a workaround, try updating the agent.jar file."));
                } else {
                    assertTrue(channel.preloadJar(j.jenkins.getPluginManager().uberClassLoader, Stapler.class));
                    assertThat(logRecords.stream().map(LogRecord::getMessage).toList(), is(empty()));
                }
            }
        }

        logHandler.clear();

        if (hasJenkinsJarURLValidator) { // Start rejecting everything; only applies to JarURLValidatorImpl
            System.setProperty(JarURLValidatorImpl.class.getName() + ".REJECT_ALL", "true");

            // Identify that a jar was already loaded:
            assertFalse(channel.preloadJar(j.jenkins.getPluginManager().uberClassLoader, Stapler.class));
            assertThat(logRecords, not(hasItem(logMessageContainsString("Allowing URL"))));
            assertThat(logRecords, not(hasItem(logMessageContainsString("Rejecting URL"))));

            logHandler.clear();

            // different jar file than before, old remoting will fail due to call through ClassLoaderProxy#fetchJar, new remoting passes
            if (requestingJarFromAgent) {
                final IOException ioException = assertThrows(IOException.class, () -> channel.preloadJar(j.jenkins.getPluginManager().uberClassLoader, Argument.class));
                assertThat(ioException.getMessage(), containsString("all attempts by agents to load jars from the controller are rejected"));
                assertThat(logRecords, not(hasItem(logMessageContainsString("Allowing URL"))));
                assertThat(logRecords, hasItem(logMessageContainsString("Rejecting URL due to configuration: ")));
            } else {
                assertTrue(channel.preloadJar(j.jenkins.getPluginManager().uberClassLoader, org.kohsuke.args4j.Argument.class));
                assertThat(logRecords, not(hasItem(logMessageContainsString("Allowing URL"))));
                assertThat(logRecords, not(hasItem(logMessageContainsString("Rejecting URL"))));
            }
        }

        logHandler.clear();

        if (hasJenkinsJarURLValidator) { // Disable block, only applies to JarURLValidatorImpl
            System.clearProperty(JarURLValidatorImpl.class.getName() + ".REJECT_ALL");
            if (requestingJarFromAgent) {
                // now it works again for old remoting:
                assertTrue(channel.preloadJar(j.jenkins.getPluginManager().uberClassLoader, org.kohsuke.args4j.Argument.class));
                assertThat(logRecords, hasItem(logMessageContainsString("Allowing URL: file:/")));
            } else {
                // new remoting already has it.
                assertFalse(channel.preloadJar(j.jenkins.getPluginManager().uberClassLoader, org.kohsuke.args4j.Argument.class));
                assertThat(logRecords, not(hasItem(logMessageContainsString("Allowing URL"))));
                assertThat(logRecords, not(hasItem(logMessageContainsString("Rejecting URL"))));
            }
            assertThat(logRecords, not(hasItem(logMessageContainsString("Rejecting URL due to configuration: "))));
        }

        logHandler.clear();

        if (hasJenkinsJarURLValidator || !requestingJarFromAgent) { // prepare bouncycastle-api
            assertTrue(j.jenkins.getPluginManager().getPlugin("bouncycastle-api").isActive());
            InstallBouncyCastleJCAProvider.on(channel);
            channel.call(new ConfirmBouncyCastleLibrary());
        }

        logHandler.clear();

        { // Exploitation tests
            final URL secretKeyFile = new File(j.jenkins.getRootDir(), "secret.key").toURI().toURL();
            final String expectedContent = IOUtils.toString(secretKeyFile, StandardCharsets.UTF_8);
            { // Protection is effective when agents request non-jar files:
                final InvocationTargetException itex = assertThrows(InvocationTargetException.class, () -> channel.call(new Exploit(secretKeyFile, expectedContent)));
                assertThat(itex.getCause(), instanceOf(IOException.class));
                if (hasJenkinsJarURLValidator) {
                    assertThat(itex.getCause().getMessage(), containsString("This URL does not point to a jar file allowed to be requested by agents"));
                    assertThat(logRecords, not(hasItem(logMessageContainsString("Allowing URL"))));
                    assertThat(logRecords, hasItem(logMessageContainsString("Rejecting URL: ")));
                } else {
                    assertThat(itex.getCause().getMessage(), containsString("No hudson.remoting.JarURLValidator has been set for this channel, so all #fetchJar calls are rejected. This is likely a bug in Jenkins. As a workaround, try updating the agent.jar file."));
                }
            }

            logHandler.clear();

            { // Disable protection and non-jar files can be accessed:
                System.setProperty(Channel.class.getName() + ".DISABLE_JAR_URL_VALIDATOR", "true");
                channel.call(new Exploit(secretKeyFile, expectedContent));
                if (hasJenkinsJarURLValidator) {
                    assertThat(logRecords, hasItem(logMessageContainsString("Allowing URL due to configuration")));
                    assertThat(logRecords, not(hasItem(logMessageContainsString("Rejecting URL"))));
                }
                System.clearProperty(Channel.class.getName() + ".DISABLE_JAR_URL_VALIDATOR");
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
