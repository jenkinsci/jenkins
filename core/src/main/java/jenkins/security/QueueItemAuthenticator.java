package jenkins.security;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.CauseAction;
import hudson.model.Queue;
import hudson.model.Queue.Item;
import hudson.model.Queue.Task;
import hudson.security.ACL;
import org.acegisecurity.Authentication;

import javax.annotation.CheckForNull;

/**
 * Extension point to run {@link hudson.model.Queue.Executable}s under a specific identity for better access control.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.520
 * @see QueueItemAuthenticatorConfiguration
 * @see Item#authenticate()
 * @see Task#getDefaultAuthentication()
 */
public abstract class QueueItemAuthenticator extends AbstractDescribableImpl<QueueItemAuthenticator> implements ExtensionPoint {
    /**
     * Determines the identity in which the {@link hudson.model.Queue.Executable} will run as.
     *
     * @param item
     *      The contextual information to assist the authentication.
     *      The primary interest is likely {@link hudson.model.Queue.Item#task}, which is often {@link AbstractProject}.
     *      {@link Action}s associated with the item is also likely of interest, such as {@link CauseAction}.
     *
     * @return
     *      returning non-null will determine the identity. If null is returned, the next
     *      configured {@link QueueItemAuthenticator} will be given a chance to authenticate
     *      the executor. If everything fails, fall back to {@link Task#getDefaultAuthentication()}.
     */
    public abstract @CheckForNull Authentication authenticate(Queue.Item item);

    @Override
    public QueueItemAuthenticatorDescriptor getDescriptor() {
        return (QueueItemAuthenticatorDescriptor)super.getDescriptor();
    }
}
