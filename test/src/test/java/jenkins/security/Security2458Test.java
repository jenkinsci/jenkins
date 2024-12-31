package jenkins.security;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import hudson.ExtensionList;
import hudson.remoting.Callable;
import java.io.IOException;
import java.util.Objects;
import jenkins.agents.AgentComputerUtil;
import jenkins.security.s2m.AdminWhitelistRule;
import org.jenkinsci.remoting.RoleChecker;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.jvnet.hudson.test.JenkinsRule;

public class Security2458Test {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Before
    public void enableAgentToControllerProtections() {
        AdminWhitelistRule rule = ExtensionList.lookupSingleton(AdminWhitelistRule.class);
        rule.setMasterKillSwitch(false);
    }

    @Test
    public void rejectBadCallable() throws Throwable {
        // If the role check is empty, fail
        assertThrowsIOExceptionCausedBySecurityException(() -> Objects.requireNonNull(r.createOnlineAgent().getChannel()).call(new CallableCaller(new BadCallable())));

        // If it performs a no-op check, fail. This used to work when required role checks were introduced, but later prohibited.
        assertThrowsIOExceptionCausedBySecurityException(() -> Objects.requireNonNull(r.createOnlineAgent().getChannel()).call(new CallableCaller(new EvilCallable())));

        // Explicit role check.
        Objects.requireNonNull(r.createOnlineAgent().getChannel()).call(new CallableCaller(new GoodCallable()));
    }

    private static class CallableCaller extends MasterToAgentCallable<Object, Throwable> {
        private final Callable<?, ?> callable;

        CallableCaller(Callable<?, ?> callable) {
            this.callable = callable;
        }

        @Override
        public Object call() throws Throwable {
            Objects.requireNonNull(AgentComputerUtil.getChannelToMaster()).call(callable);
            return null;
        }
    }

    private static class BadCallable implements Callable<Object, Exception> {
        @Override
        public Object call() throws Exception {
            return null;
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            // Deliberately empty
        }
    }

    private static class EvilCallable implements Callable<Object, Exception> {
        @Override
        public Object call() throws Exception {
            return null;
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            checker.check(this); // Was supported in 2.319 but later dropped
        }
    }

    private static class GoodCallable implements Callable<Object, Exception> {
        @Override
        public Object call() throws Exception {
            return null;
        }

        @Override
        public void checkRoles(RoleChecker checker) throws SecurityException {
            checker.check(this, Roles.MASTER); // Manual S2M
        }
    }


    private static SecurityException assertThrowsIOExceptionCausedBySecurityException(ThrowingRunnable runnable) {
        return assertThrowsIOExceptionCausedBy(SecurityException.class, runnable);
    }

    private static <X extends Throwable> X assertThrowsIOExceptionCausedBy(Class<X> causeClass, ThrowingRunnable runnable) {
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
