package hudson.model.queue;

import hudson.model.Queue.BuildableItem;

import java.util.Collections;
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
        Collections.sort(buildables,this); // sort is ascending order
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
        return compare(lhs.buildableStartMilliseconds,rhs.buildableStartMilliseconds);
    }

    /**
     * sign(a-b).
     */
    protected static int compare(long a, long b) {
        if (a>b)    return 1;
        if (a<b)    return -1;
        return 0;
    }

    /**
     * sign(a-b).
     */
    protected static int compare(int a, int b) {
        if (a>b)    return 1;
        if (a<b)    return -1;
        return 0;
    }
}
