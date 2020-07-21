package jenkins.security;

import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.CauseAction;
import hudson.model.Queue;
import hudson.model.Queue.Item;
import hudson.model.Queue.Task;
import hudson.model.queue.Tasks;
import java.util.Calendar;
import java.util.Collections;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import org.springframework.security.core.Authentication;

/**
 * Extension point to run {@link hudson.model.Queue.Executable}s under a specific identity for better access control.
 * You must override either {@link #authenticate2(hudson.model.Queue.Item)}, or {@link #authenticate2(hudson.model.Queue.Task)}, or both.
 * @author Kohsuke Kawaguchi
 * @since 1.520
 * @see QueueItemAuthenticatorConfiguration
 * @see QueueItemAuthenticatorProvider
 * @see Item#authenticate()
 * @see Tasks#getAuthenticationOf
 */
public abstract class QueueItemAuthenticator extends AbstractDescribableImpl<QueueItemAuthenticator> implements ExtensionPoint {
    /**
     * Determines the identity in which the {@link hudson.model.Queue.Executable} will run as.
     * The default implementation delegates to {@link #authenticate2(hudson.model.Queue.Task)}.
     * @param item
     *      The contextual information to assist the authentication.
     *      The primary interest is likely {@link hudson.model.Queue.Item#task}, which is often {@link AbstractProject}.
     *      {@link Action}s associated with the item is also likely of interest, such as {@link CauseAction}.
     *
     * @return
     *      returning non-null will determine the identity. If null is returned, the next
     *      configured {@link QueueItemAuthenticator} will be given a chance to authenticate2
      the executor. If everything fails, fall back to {@link Task#getDefaultAuthentication()}.
     */
    public @CheckForNull Authentication authenticate2(Queue.Item item) {
        if (Util.isOverridden(QueueItemAuthenticator.class, getClass(), "authenticate2", Queue.Task.class)) {
            return authenticate2(item.task);
        } else if (Util.isOverridden(QueueItemAuthenticator.class, getClass(), "authenticate", Queue.Task.class)) {
            return authenticate(item.task).toSpring();
        } else if (Util.isOverridden(QueueItemAuthenticator.class, getClass(), "authenticate", Queue.Item.class)) {
            return authenticate(item).toSpring();
        } else {
            throw new AbstractMethodError("you must override at least one of the QueueItemAuthenticator.authenticate2 methods");
        }
    }

    /**
     * Determines the identity in which the {@link hudson.model.Queue.Executable} will run as.
     * The default implementation delegates to {@link #authenticate2(hudson.model.Queue.Item)} (there will be no associated actions).
     * @param task
     *      Often {@link AbstractProject}.
     *
     * @return
     *      returning non-null will determine the identity. If null is returned, the next
     *      configured {@link QueueItemAuthenticator} will be given a chance to authenticate2
      the executor. If everything fails, fall back to {@link Task#getDefaultAuthentication()}.
     * @since 1.560
     */
    public @CheckForNull Authentication authenticate2(Queue.Task task) {
        if (Util.isOverridden(QueueItemAuthenticator.class, getClass(), "authenticate2", Queue.Item.class)) {
            // Need a fake (unscheduled) item. All the other calls assume a BuildableItem but probably it does not matter.
            return authenticate2(new Queue.WaitingItem(Calendar.getInstance(), task, Collections.emptyList()));
        } else if (Util.isOverridden(QueueItemAuthenticator.class, getClass(), "authenticate", Queue.Item.class)) {
            return authenticate(new Queue.WaitingItem(Calendar.getInstance(), task, Collections.emptyList())).toSpring();
        } else if (Util.isOverridden(QueueItemAuthenticator.class, getClass(), "authenticate", Queue.Task.class)) {
            return authenticate(task).toSpring();
        } else {
            throw new AbstractMethodError("you must override at least one of the QueueItemAuthenticator.authenticate2 methods");
        }
    }

    @Deprecated
    public @CheckForNull org.acegisecurity.Authentication authenticate(Queue.Item item) {
        return org.acegisecurity.Authentication.fromSpring(authenticate2(item));
    }

    @Deprecated
    public @CheckForNull org.acegisecurity.Authentication authenticate(Queue.Task task) {
        return org.acegisecurity.Authentication.fromSpring(authenticate2(task));
    }

    @Override
    public QueueItemAuthenticatorDescriptor getDescriptor() {
        return (QueueItemAuthenticatorDescriptor)super.getDescriptor();
    }
}
