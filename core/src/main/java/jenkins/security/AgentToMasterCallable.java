package jenkins.security;

import hudson.remoting.Callable;
import org.jenkinsci.remoting.RoleChecker;

/**
 * Convenient {@link Callable} that are meant to run on the master (sent by agent/CLI/etc).
 * Note that any serializable fields must either be defined in your plugin or included in the stock JEP-200 whitelist.
 * @author Kohsuke Kawaguchi
 * @since 1.587 / 1.580.1
 */
public abstract class AgentToMasterCallable<V, T extends Throwable> implements Callable<V, T> {
    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        checker.check(this, Roles.MASTER);
    }

    private static final long serialVersionUID = 1L;
}
