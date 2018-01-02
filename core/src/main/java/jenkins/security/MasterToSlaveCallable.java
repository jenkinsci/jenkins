package jenkins.security;

import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.ChannelClosedException;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;


/**
 * Convenient {@link Callable} meant to be run on agent.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.587 / 1.580.1
 */
public abstract class MasterToSlaveCallable<V, T extends Throwable> implements Callable<V,T> {

    private static final long serialVersionUID = 1L;

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        checker.check(this,Roles.SLAVE);
    }

    //TODO: replace by Callable#getChannelOrFail() once Minimal supported Remoting version is 3.15 or above
    @Restricted(NoExternalUse.class)
    protected static Channel _getChannelOrFail() throws ChannelClosedException {
        final Channel ch = Channel.current();
        if (ch == null) {
            throw new ChannelClosedException("No channel associated with the thread", null);
        }
        return ch;
    }

    //TODO: replace by Callable#getOpenChannelOrFail() once Minimal supported Remoting version is 3.15 or above
    @Restricted(NoExternalUse.class)
    protected static Channel _getOpenChannelOrFail() throws ChannelClosedException {
        final Channel ch = _getChannelOrFail();
        if (ch.isClosingOrClosed()) { // TODO: Since Remoting 2.33, we still need to explicitly declare minimal Remoting version
            throw new ChannelClosedException("The associated channel " + ch + " is closing down or has closed down", ch.getCloseRequestCause());
        }
        return ch;
    }
}
