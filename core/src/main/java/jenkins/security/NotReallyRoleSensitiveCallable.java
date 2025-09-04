package jenkins.security;

import hudson.remoting.Callable;
import jenkins.util.ThrowingCallable;
import org.jenkinsci.remoting.RoleChecker;

/**
 * {@link Callable} adapter for situations where Callable is not used for remoting but
 * just as a convenient function that has parameterized return value and exception type.
 * Consider using {@link ThrowingCallable} instead.
 * @author Kohsuke Kawaguchi
 * @since 1.587 / 1.580.1
 */
public abstract class NotReallyRoleSensitiveCallable<V, T extends Throwable> implements Callable<V, T> {
    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        // not meant to be used where this matters
        throw new UnsupportedOperationException();
    }
}
