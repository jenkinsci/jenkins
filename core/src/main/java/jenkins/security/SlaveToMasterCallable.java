package jenkins.security;

import hudson.remoting.Callable;
import org.jenkinsci.remoting.Role;

import java.util.Collection;

/**
 * Convenient {@link Callable} that are meant to run on the master (sent by slave/CLI/etc).
 *
 * @author Kohsuke Kawaguchi
 * @since 1.THU
 */
public abstract class SlaveToMasterCallable<V, T extends Throwable> implements Callable<V,T> {
    @Override
    public Collection<Role> getRecipients() {
        return Roles.FOR_MASTER;
    }

    private static final long serialVersionUID = 1L;
}
