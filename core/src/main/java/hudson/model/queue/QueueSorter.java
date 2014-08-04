package hudson.model.queue;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.init.Initializer;
import jenkins.model.Jenkins;
import hudson.model.LoadBalancer;
import hudson.model.Queue;
import hudson.model.Queue.BuildableItem;

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
     * Sorts the buildable items list. The items at the beginning will be executed
     * before the items at the end of the list.
     *
     * @param buildables
     *      List of buildable items in the queue. Never null.
     */
    public abstract void sortBuildableItems(List<BuildableItem> buildables);

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
