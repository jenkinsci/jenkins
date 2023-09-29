package jenkins.security;

import hudson.remoting.Callable;
import jenkins.slaves.RemotingVersionInfo;
import org.jenkinsci.remoting.RoleChecker;

/**
 * Convenient {@link Callable} meant to be run on agent.
 *
 * Note that the logic within {@link #call()} should use API of a minimum supported Remoting version.
 * See {@link RemotingVersionInfo#getMinimumSupportedVersion()}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.587 / 1.580.1
 * @param <V> the return type; note that this must either be defined in your plugin or included in the stock JEP-200 whitelist
 */
public abstract class MasterToSlaveCallable<V, T extends Throwable> implements Callable<V, T> {

    private static final long serialVersionUID = 1L;

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        checker.check(this, Roles.SLAVE);
    }
}
