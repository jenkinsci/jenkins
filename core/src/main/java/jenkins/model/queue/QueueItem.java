package jenkins.model.queue;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Cause;
import hudson.model.Queue;
import hudson.model.Run;
import jenkins.model.FullyNamedModelObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Interface used by Jelly views to render a queue item through {@code <t:queue>}.
 * @since 2.405
 */
@Restricted(Beta.class)
public interface QueueItem extends FullyNamedModelObject {
    /**
     * @return true if the item is starving for an executor for too long.
     */
    boolean isStuck();

    /**
     * @return The underlying {@link Queue.Task} currently in queue.
     */
    @NonNull
    Queue.Task getTask();

    /**
     * Checks whether a scheduled item may be canceled.
     * @return by default, the same as {@link Queue.Task#hasAbortPermission}
     */
    default boolean hasCancelPermission() {
        return getTask().hasAbortPermission();
    }

    /**
     * Unique ID (per controller) that tracks the {@link Queue.Task} as it moves through different stages
     * in the queue (each represented by different implementations of {@link QueueItem} and into any subsequent
     * {@link Run} instance (see {@link Run#getQueueId()}).
     */
    long getId();

    /**
     * Convenience method that returns a read only view of the {@link Cause}s associated with this item in the queue as a single string.
     */
    @NonNull
    String getCausesDescription();

    /**
     * Gets a human-readable status message describing why it's in the queue.
     * May return null if there is no cause of blockage.
     */
    @CheckForNull
    String getWhy();

    /**
     * Gets a human-readable message about the parameters of this item
     */
    @NonNull
    String getParams();

    /**
     * Returns a human readable presentation of how long this item is already in the queue.
     * E.g. something like '3 minutes 40 seconds'
     */
    @NonNull
    String getInQueueForString();

    /**
     * @return a display name for this queue item; by default, {@link Queue.Task#getFullDisplayName()}
     */
    @CheckForNull
    @Override
    default String getDisplayName() {
        // TODO review usage of this method and replace with getFullDisplayName() where appropriate
        return getTask().getFullDisplayName();
    }

    /**
     * @return the full display name for this queue item; by default, {@link Queue.Task#getFullDisplayName()}
     */
    @Override
    default String getFullDisplayName() {
        return getTask().getFullDisplayName();
    }
}
