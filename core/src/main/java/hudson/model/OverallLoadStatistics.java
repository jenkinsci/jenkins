package hudson.model;

import hudson.model.MultiStageTimeSeries.TimeScale;
import org.jfree.data.category.DefaultCategoryDataset;

/**
 * {@link LoadStatistics} for the entire system (the master and all the slaves combined.)
 *
 * <p>
 * {@link #computeQueueLength()} and {@link #queueLength} counts those tasks
 * that are unassigned to any node, whereas {@link #totalQueueLength}
 * tracks the queue length including tasks that are assigned to a specific node.
 *
 * @author Kohsuke Kawaguchi
 * @see Hudson#overallLoad
 */
public class OverallLoadStatistics extends LoadStatistics {
    /**
     * Number of total {@link Queue.BuildableItem}s that represents blocked builds.
     */
    public final MultiStageTimeSeries totalQueueLength = new MultiStageTimeSeries(0,DECAY);

    /*package*/ OverallLoadStatistics() {
        super(0,0);
    }

    @Override
    public int computeIdleExecutors() {
        return Hudson.getInstance().getComputer().getIdleExecutors();
    }

    @Override
    public int computeTotalExecutors() {
        return Hudson.getInstance().getComputer().getTotalExecutors();
    }

    @Override
    public int computeQueueLength() {
        return Hudson.getInstance().getQueue().countBuildableItemsFor(null);
    }

    /**
     * When drawing the overall load statistics, use the total queue length,
     * not {@link #queueLength}, which just shows jobs that are to be run on the master. 
     */
    protected DefaultCategoryDataset createOverallDataset(TimeScale timeScale) {
        return createDataset(timeScale,
                new float[][]{
                    busyExecutors.pick(timeScale).getHistory(),
                    totalExecutors.pick(timeScale).getHistory(),
                    totalQueueLength.pick(timeScale).getHistory()
                },
                new String[]{
                    Messages.LoadStatistics_Legends_TotalExecutors(),
                    Messages.LoadStatistics_Legends_BusyExecutors(),
                    Messages.LoadStatistics_Legends_QueueLength()
                });
    }
}
