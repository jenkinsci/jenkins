package hudson.model.queue;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.init.Initializer;
import jenkins.model.Jenkins;
import hudson.model.LoadBalancer;
import hudson.model.Queue;
import hudson.model.Queue.BuildableItem;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import static hudson.init.InitMilestone.JOB_LOADED;

/**
 * Singleton extension point for sorting buildable items
 *
 * @since 1.343
 */
public abstract class QueueSorter implements ExtensionPoint {
    /**
     * A comparator that sorts {@link Queue.BlockedItem} instances based on how long they have been in the queue.
     * (We want the time since in queue by default as blocking on upstream/downstream considers waiting items
     * also and thus the blocking starts once the task is in the queue not once the task is buildable)
     *
     * @since 1.618
     */
    public static final Comparator<Queue.BlockedItem> DEFAULT_BLOCKED_ITEM_COMPARATOR = new Comparator<Queue.BlockedItem>() {
        @Override
        public int compare(Queue.BlockedItem o1, Queue.BlockedItem o2) {
            return Long.compare(o1.getInQueueSince(), o2.getInQueueSince());
        }
    };

    /**
     * Sorts the buildable items list. The items at the beginning will be executed
     * before the items at the end of the list.
     *
     * @param buildables
     *      List of buildable items in the queue. Never null.
     */
    public abstract void sortBuildableItems(List<BuildableItem> buildables);

    /**
     * Sorts the blocked items list. The items at the beginning will be considered for removal from the blocked state
     * before the items at the end of the list.
     *
     * @param blockedItems
     *      List of blocked items in the queue. Never null.
     * @since 1.618
     */
    public void sortBlockedItems(List<Queue.BlockedItem> blockedItems) {
        Collections.sort(blockedItems, DEFAULT_BLOCKED_ITEM_COMPARATOR);
    }

    /**
     * All registered {@link QueueSorter}s. Only the first one will be picked up,
     * unless explicitly overridden by {@link Queue#setSorter(QueueSorter)}.
     */
    public static ExtensionList<QueueSorter> all() {
        return ExtensionList.lookup(QueueSorter.class);
    }

    /**
     * Installs the default queue sorter.
     *
     * {@link Queue#Queue(LoadBalancer)} is too early to do this
     */
    @Initializer(after=JOB_LOADED)
    public static void installDefaultQueueSorter() {
        ExtensionList<QueueSorter> all = all();
        if (all.isEmpty())  return;

        Queue q = Jenkins.getInstance().getQueue();
        if (q.getSorter()!=null)        return; // someone has already installed something. leave that alone.

        q.setSorter(all.get(0));
        if (all.size()>1)
            LOGGER.warning("Multiple QueueSorters are registered. Only the first one is used and the rest are ignored: "+all);
    }

    private static final Logger LOGGER = Logger.getLogger(QueueSorter.class.getName());
}
