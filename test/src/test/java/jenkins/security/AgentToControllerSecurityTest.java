package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import hudson.FilePath;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import jenkins.SlaveToMasterFileCallable;
import jenkins.agents.AgentComputerUtil;
import jenkins.util.JenkinsJVM;
import org.jenkinsci.remoting.RoleChecker;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.jvnet.hudson.test.JenkinsRule;

public class AgentToControllerSecurityTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    // ----- try to run a legacy callable

    @Test
    public void testLegacyCallable() {
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
    public void testFilePaths() {
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
    public void testAgentToControllerFileCallable() throws Exception {
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

    // ----- Utility methods

    public static SecurityException assertThrowsIOExceptionCausedBySecurityException(ThrowingRunnable runnable) {
        return assertThrowsIOExceptionCausedBy(SecurityException.class, runnable);
    }

    public static <X extends Throwable> X assertThrowsIOExceptionCausedBy(Class<X> causeClass, ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (IOException ex) {
            final Throwable cause = ex.getCause();
            assertTrue("IOException with message: '" + ex.getMessage() + "' wasn't caused by " + causeClass + ": " + (cause == null ? "(null)" : (cause.getClass().getName() + ": " + cause.getMessage())),
                    cause != null && causeClass.isAssignableFrom(cause.getClass()));
            return causeClass.cast(cause);
        } catch (Throwable t) {
            fail("Threw other Throwable: " + t.getClass() + " with message " + t.getMessage());
        }
        fail("Expected exception but passed");
        return null;
    }
}
