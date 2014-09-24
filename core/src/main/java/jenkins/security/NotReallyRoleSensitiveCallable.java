package jenkins.security;

import hudson.remoting.Callable;
import org.jenkinsci.remoting.Role;

import java.util.Collection;

/**
 * {@link Callable} adapter for situations where Callable is not used for remoting but
 * just as a convenient function that has parameterized return value and exception type.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.THU
 */
public abstract class NotReallyRoleSensitiveCallable<V,T extends Throwable> implements Callable<V,T> {
    @Override
    public final Collection<Role> getRecipients() {
        // not meant to be used where this matters
        throw new UnsupportedOperationException();
    }
}
