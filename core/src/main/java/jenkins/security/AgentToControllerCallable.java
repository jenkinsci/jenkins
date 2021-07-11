package jenkins.security;

import hudson.remoting.Callable;
import org.jenkinsci.remoting.RoleChecker;

/**
 * Convenient {@link Callable} that are meant to run on the controller (sent by agent/CLI/etc).
 * Note that any serializable fields must either be defined in your plugin or included in the stock JEP-200 whitelist.
 *
 * @since TODO
 */
public abstract class AgentToControllerCallable<V, T extends Throwable> implements Callable<V, T> {
    private static final long serialVersionUID = 1L;

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        checker.check(this, Roles.CONTROLLER);
    }
}
