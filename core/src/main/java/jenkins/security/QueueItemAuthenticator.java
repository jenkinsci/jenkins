package jenkins.security;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.CauseAction;
import hudson.model.Describable;
import hudson.model.Queue;
import hudson.model.Queue.Item;
import hudson.model.Queue.Task;
import hudson.model.queue.Tasks;
import java.util.Calendar;
import java.util.Collections;
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
public abstract class QueueItemAuthenticator implements Describable<QueueItemAuthenticator>, ExtensionPoint {
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
     *      configured {@link QueueItemAuthenticator} will be given a chance to authenticate
     *      the executor. If everything fails, fall back to {@link Task#getDefaultAuthentication()}.
     * @since 2.266
     */
    public @CheckForNull Authentication authenticate2(Queue.Item item) {
        if (Util.isOverridden(QueueItemAuthenticator.class, getClass(), "authenticate2", Queue.Task.class)) {
            return authenticate2(item.task);
        } else if (Util.isOverridden(QueueItemAuthenticator.class, getClass(), "authenticate", Queue.Task.class)) {
            org.acegisecurity.Authentication a = authenticate(item.task);
            return a != null ? a.toSpring() : null;
        } else if (Util.isOverridden(QueueItemAuthenticator.class, getClass(), "authenticate", Queue.Item.class)) {
            org.acegisecurity.Authentication a = authenticate(item);
            return a != null ? a.toSpring() : null;
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
     *      configured {@link QueueItemAuthenticator} will be given a chance to authenticate
     *      the executor. If everything fails, fall back to {@link Task#getDefaultAuthentication()}.
     * @since 2.266
     */
    public @CheckForNull Authentication authenticate2(Queue.Task task) {
        if (Util.isOverridden(QueueItemAuthenticator.class, getClass(), "authenticate2", Queue.Item.class)) {
            // Need a fake (unscheduled) item. All the other calls assume a BuildableItem but probably it does not matter.
            return authenticate2(new Queue.WaitingItem(Calendar.getInstance(), task, Collections.emptyList()));
        } else if (Util.isOverridden(QueueItemAuthenticator.class, getClass(), "authenticate", Queue.Item.class)) {
            org.acegisecurity.Authentication a = authenticate(new Queue.WaitingItem(Calendar.getInstance(), task, Collections.emptyList()));
            return a != null ? a.toSpring() : null;
        } else if (Util.isOverridden(QueueItemAuthenticator.class, getClass(), "authenticate", Queue.Task.class)) {
            org.acegisecurity.Authentication a = authenticate(task);
            return a != null ? a.toSpring() : null;
        } else {
            throw new AbstractMethodError("you must override at least one of the QueueItemAuthenticator.authenticate2 methods");
        }
    }

    /**
     * @deprecated use {@link #authenticate2(Queue.Item)}
     */
    @Deprecated
    public @CheckForNull org.acegisecurity.Authentication authenticate(Queue.Item item) {
        Authentication a = authenticate2(item);
        return a != null ? org.acegisecurity.Authentication.fromSpring(a) : null;
    }

    /**
     * @deprecated use {@link #authenticate2(Queue.Task)}
     * @since 1.560
     */
    @Deprecated
    public @CheckForNull org.acegisecurity.Authentication authenticate(Queue.Task task) {
        Authentication a = authenticate2(task);
        return a != null ? org.acegisecurity.Authentication.fromSpring(a) : null;
    }

    @Override
    public QueueItemAuthenticatorDescriptor getDescriptor() {
        return (QueueItemAuthenticatorDescriptor) Describable.super.getDescriptor();
    }
}
