package jenkins.security;

import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.ChannelClosedException;
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
public abstract class MasterToSlaveCallable<V, T extends Throwable> implements Callable<V,T> {

    private static final long serialVersionUID = 1L;

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        checker.check(this,Roles.SLAVE);
    }

    //TODO: remove once Minimum supported Remoting version is 3.15 or above
    @Override
    public Channel getChannelOrFail() throws ChannelClosedException {
        final Channel ch = Channel.current();
        if (ch == null) {
            throw new ChannelClosedException(new IllegalStateException("No channel associated with the thread"));
        }
        return ch;
    }

    //TODO: remove Callable#getOpenChannelOrFail() once minimum supported Remoting version is 3.15 or above
    @Override
    public Channel getOpenChannelOrFail() throws ChannelClosedException {
        final Channel ch = getChannelOrFail();
        if (ch.isClosingOrClosed()) { // TODO: Since Remoting 2.33, we still need to explicitly declare minimum Remoting version
            throw new ChannelClosedException(new IllegalStateException("The associated channel " + ch + " is closing down or has closed down", ch.getCloseRequestCause()));
        }
        return ch;
    }
}
