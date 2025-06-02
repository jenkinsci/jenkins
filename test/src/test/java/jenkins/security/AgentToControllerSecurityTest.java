package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import hudson.FilePath;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import jenkins.MasterToSlaveFileCallable;
import jenkins.SlaveToMasterFileCallable;
import jenkins.agents.AgentComputerUtil;
import jenkins.security.s2m.CallableDirectionChecker;
import jenkins.util.JenkinsJVM;
import org.jenkinsci.remoting.RoleChecker;
import org.junit.function.ThrowingRunnable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class AgentToControllerSecurityTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    // ----- try to run a legacy callable

    @Test
    void testLegacyCallable() {
        final SecurityException securityException = assertThrowsIOExceptionCausedBySecurityException(() -> Objects.requireNonNull(j.createOnlineSlave().getChannel()).call(new CallLegacyCallableCallable()));
        assertThat(securityException.getMessage(), containsString("Sending jenkins.security.AgentToControllerSecurityTest$LegacyCallable from agent to controller is prohibited"));
    }

    private static class CallLegacyCallableCallable extends MasterToSlaveCallable<Void, Exception> {
        @Override
        public Void call() throws Exception {
            Objects.requireNonNull(AgentComputerUtil.getChannelToController()).call(new LegacyCallable());
            return null;
        }
    }

    private static class LegacyCallable implements Callable<Void, Exception> {
        @Override
        public Void call() throws Exception {
            fail("LegacyCallable got called");
            return null;
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            throw new AbstractMethodError("pretending to be a legacy Callable");
        }
    }

    // ----- Attempt any file path access using a FilePath method

    @Test
    void testFilePaths() {
        final SecurityException securityException = assertThrowsIOExceptionCausedBySecurityException(() -> Objects.requireNonNull(j.createOnlineSlave().getChannel()).call(new AccessControllerFilePathCallable()));
        assertThat(securityException.getMessage(), containsString("Sending hudson.FilePath$ReadLink from agent to controller is prohibited"));
    }

    private static class AccessControllerFilePathCallable extends MasterToSlaveCallable<Void, Exception> {
        @Override
        public Void call() throws Exception {
            new FilePath(AgentComputerUtil.getChannelToController(), "foo").readLink();
            return null;
        }
    }

    // ----- Agent to controller access is still possible using SlaveToMaster[File]Callable

    @Test
    void testAgentToControllerFileCallable() throws Exception {
        Objects.requireNonNull(j.createOnlineSlave().getChannel()).call(new InvokeAgentToControllerCallables());
    }

    private static class InvokeAgentToControllerCallables extends MasterToSlaveCallable<Void, Exception> {
        @Override
        public Void call() throws Exception {
            assertFalse(JenkinsJVM.isJenkinsJVM());
            final VirtualChannel channelToController = AgentComputerUtil.getChannelToController();
            assertNotNull(channelToController);
            channelToController.call(new A2CCallable());
            new FilePath(channelToController, "foo").act(new A2CFileCallable());
            return null;
        }
    }

    private static class A2CFileCallable extends SlaveToMasterFileCallable<Void> {
        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            assertTrue(JenkinsJVM.isJenkinsJVM());
            return null;
        }
    }

    private static class A2CCallable extends SlaveToMasterCallable<Void, Exception> {
        @Override
        public Void call() throws Exception {
            assertTrue(JenkinsJVM.isJenkinsJVM());
            return null;
        }
    }

    // ----- Agent to controller access control can be disabled using system property (but really shouldn't)
    @Test
    void ensureBypass() throws Exception {
        CallableDirectionChecker.BYPASS = true;
        try {
            Objects.requireNonNull(j.createOnlineSlave().getChannel()).call(new InvokeControllerToAgentCallables());
        } finally {
            CallableDirectionChecker.BYPASS = false;
        }
    }

    private static class InvokeControllerToAgentCallables extends MasterToSlaveCallable<Void, Exception> {
        @Override
        public Void call() throws Exception {
            assertFalse(JenkinsJVM.isJenkinsJVM());
            final VirtualChannel channelToController = AgentComputerUtil.getChannelToController();
            assertNotNull(channelToController);
            channelToController.call(new NoopMasterToSlaveCallable());
            new FilePath(channelToController, "foo").act(new NoopMasterToSlaveFileCallable());
            return null;
        }
    }

    private static class NoopMasterToSlaveCallable extends MasterToSlaveCallable<Void, Exception> {
        @Override
        public Void call() throws Exception {
            return null;
        }
    }

    private static class NoopMasterToSlaveFileCallable extends MasterToSlaveFileCallable<Void> {
        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            return null;
        }
    }

    // --- Ensure local FilePath operations work inside a Callable

    @Test
    @Issue("JENKINS-67189")
    void controllerToControllerTest() throws Exception {
        // Send a callable to the agent, which sends a callable to the controller, which invokes a method of a local FilePath
        Objects.requireNonNull(j.createOnlineSlave().getChannel()).call(new BackToControllerCallable());
    }

    private static class BackToControllerCallable extends MasterToSlaveCallable<String, Exception> {
        @Override
        public String call() throws Exception {
            assertFalse(JenkinsJVM.isJenkinsJVM());
            return Objects.requireNonNull(AgentComputerUtil.getChannelToController()).call(new LocalFileOpCallable(true));
        }
    }

    // Used for both agent-to-agent and controller-to-controller, so make it S2MC
    private static class LocalFileOpCallable extends SlaveToMasterCallable<String, Exception> {
        private final boolean executesOnJenkinsJVM;

        LocalFileOpCallable(boolean executesOnJenkinsJVM) {
            this.executesOnJenkinsJVM = executesOnJenkinsJVM;
        }

        @Override
        public String call() throws Exception {
            assertEquals(executesOnJenkinsJVM, JenkinsJVM.isJenkinsJVM());
            final File tempFile = Files.createTempFile("jenkins-test", null).toFile();
            return new FilePath(tempFile).readToString();
        }
    }

    // but this test works even in 2.319 because no agent side filtering
    @Test
    @Issue("JENKINS-67189")
    void agentToAgentTest() throws Exception {
        Objects.requireNonNull(j.createOnlineSlave().getChannel()).call(new LocalFileOpCallable(false));
    }

    // ----- Utility methods

    public static SecurityException assertThrowsIOExceptionCausedBySecurityException(ThrowingRunnable runnable) {
        return assertThrowsIOExceptionCausedBy(SecurityException.class, runnable);
    }

    public static <X extends Throwable> X assertThrowsIOExceptionCausedBy(Class<X> causeClass, ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (IOException ex) {
            final Throwable cause = ex.getCause();
            assertTrue(cause != null && causeClass.isAssignableFrom(cause.getClass()),
                    "IOException with message: '" + ex.getMessage() + "' wasn't caused by " + causeClass + ": " + (cause == null ? "(null)" : (cause.getClass().getName() + ": " + cause.getMessage())));
            return causeClass.cast(cause);
        } catch (Throwable t) {
            fail("Threw other Throwable: " + t.getClass() + " with message " + t.getMessage());
        }
        fail("Expected exception but passed");
        return null;
    }
}
