package hudson.model.queue;

import hudson.RestrictedSince;
import hudson.model.Queue.BuildableItem;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.Comparator;
import java.util.List;

/**
 * Partial implementation of {@link QueueSorter} in terms of {@link Comparator}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.343
 */
public abstract class AbstractQueueSorterImpl extends QueueSorter implements Comparator<BuildableItem> {
    @Override
    public void sortBuildableItems(List<BuildableItem> buildables) {
        buildables.sort(this); // sort is ascending order
    }

    /**
     * Override this method to provide the ordering of the sort.
     *
     * <p>
     * if lhs should be build before rhs, return a negative value. Or put another way, think of the comparison
     * as a process of converting a {@link BuildableItem} into a number, then doing num(lhs)-num(rhs).
     *
     * <p>
     * The default implementation does FIFO.
     */
    public int compare(BuildableItem lhs, BuildableItem rhs) {
        return Long.compare(lhs.buildableStartMilliseconds,rhs.buildableStartMilliseconds);
    }

    /**
     * @deprecated Use Long.compare instead.
     * sign(a-b).
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("TODO")
    protected static int compare(long a, long b) {
        return Long.compare(a, b);
    }

    /**
     * @deprecated Use Integer.compare instead.
     * sign(a-b).
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("TODO")
    protected static int compare(int a, int b) {
        return Integer.compare(a, b);
    }
}
