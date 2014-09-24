package jenkins.security;

import hudson.remoting.Callable;
import org.jenkinsci.remoting.Role;

import java.util.Collection;

/**
 * Convenient {@link Callable} meant to be run on slave.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.THU
 */
public abstract class MasterToSlaveCallable<V, T extends Throwable> implements Callable<V,T> {
    @Override
    public Collection<Role> getRecipients() {
        return Roles.FOR_SLAVE;
    }

    private static final long serialVersionUID = 1L;
}
