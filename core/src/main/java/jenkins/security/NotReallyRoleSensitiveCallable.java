package jenkins.security;

import hudson.remoting.Callable;
import jenkins.util.ThrowingCallable;
import org.jenkinsci.remoting.RoleChecker;

/**
 * @deprecated use {@link ThrowingCallable} instead
 */
public abstract class NotReallyRoleSensitiveCallable<V, T extends Throwable> implements Callable<V, T> {
    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        // not meant to be used where this matters
        throw new UnsupportedOperationException();
    }
}
