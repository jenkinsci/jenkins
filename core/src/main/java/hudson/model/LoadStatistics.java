/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Seiji Sogabe
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.model;

import hudson.Extension;
import jenkins.util.SystemProperties;
import hudson.model.MultiStageTimeSeries.TimeScale;
import hudson.model.MultiStageTimeSeries.TrendChart;
import hudson.model.queue.SubTask;
import hudson.model.queue.Tasks;
import hudson.util.ColorPalette;
import hudson.util.NoOverlapCategoryAxis;
import jenkins.model.Jenkins;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.ui.RectangleInsets;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Exported;

import java.awt.*;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import javax.annotation.CheckForNull;

/**
 * Utilization statistics for a node or a set of nodes.
 *
 * <h2>Implementation Note</h2>
 * <p>
 * Instances of this class is not capable of updating the statistics itself
 * &mdash; instead, it's done by the {@link LoadStatisticsUpdater} timer.
 * This is more efficient (as it allows us a single pass to update all stats),
 * but it's not clear to me if the loss of autonomy is worth it.
 *
 * @author Kohsuke Kawaguchi
 * @see Label#loadStatistics
 * @see Jenkins#overallLoad
 * @see Jenkins#unlabeledLoad
 */
@ExportedBean
public abstract class LoadStatistics {
    /**
     * {@code true} if and only if the new way of building statistics has been implemented by this class.
     * @since 1.607
     */
    private final boolean modern;

    /**
     * Number of executors defined for Jenkins and how it changes over time.
     * @since 1.607
     */
    @Exported
    public final MultiStageTimeSeries definedExecutors;

    /**
     * Number of executors on-line and how it changes over time. Replaces {@link #totalExecutors}
     * @since 1.607
     */
    @Exported
    public final MultiStageTimeSeries onlineExecutors;

    /**
     * Number of executors in the process of coming on-line and how it changes over time.
     * @since 1.607
     */
    @Exported
    public final MultiStageTimeSeries connectingExecutors;

    /**
     * Number of busy executors and how it changes over time.
     */
    @Exported
    public final MultiStageTimeSeries busyExecutors;

    /**
     * Number of executors not executing and how it changes over time. Note the these executors may not be able to
     * take work, see {@link #availableExecutors}.
     * @since 1.607
     */
    @Exported
    public final MultiStageTimeSeries idleExecutors;

    /**
     * Number of executors not executing and available to take work and how it changes over time.
     * @since 1.607
     */
    @Exported
    public final MultiStageTimeSeries availableExecutors;

    /**
     * Number of total executors and how it changes over time.
     * @deprecated use {@link #onlineExecutors}. Note {@code totalExecutors==onlineExecutors} for backward
     * compatibility support.
     */
    @Exported
    @Deprecated
    public final MultiStageTimeSeries totalExecutors;

    /**
     * Number of {@link hudson.model.Queue.BuildableItem}s that can run on any node in this node set but blocked.
     */
    @Exported
    public final MultiStageTimeSeries queueLength;


    protected LoadStatistics(int initialOnlineExecutors, int initialBusyExecutors) {
        this.definedExecutors = new MultiStageTimeSeries(Messages._LoadStatistics_Legends_DefinedExecutors(),
                ColorPalette.YELLOW, initialOnlineExecutors, DECAY);
        this.onlineExecutors = new MultiStageTimeSeries(
                Messages._LoadStatistics_Legends_OnlineExecutors(), ColorPalette.BLUE, initialOnlineExecutors,DECAY);
        this.connectingExecutors = new MultiStageTimeSeries(Messages._LoadStatistics_Legends_ConnectingExecutors(),
                ColorPalette.YELLOW, 0, DECAY);
        this.busyExecutors = new MultiStageTimeSeries(
                Messages._LoadStatistics_Legends_BusyExecutors(), ColorPalette.RED, initialBusyExecutors,DECAY);
        this.idleExecutors = new MultiStageTimeSeries(Messages._LoadStatistics_Legends_IdleExecutors(),
                ColorPalette.YELLOW, initialOnlineExecutors - initialBusyExecutors, DECAY);
        this.availableExecutors = new MultiStageTimeSeries(Messages._LoadStatistics_Legends_AvailableExecutors(),
                ColorPalette.YELLOW, initialOnlineExecutors - initialBusyExecutors, DECAY);
        this.queueLength = new MultiStageTimeSeries(
                Messages._LoadStatistics_Legends_QueueLength(),ColorPalette.GREY, 0, DECAY);
        this.totalExecutors = onlineExecutors;
        modern = isModern(getClass());
    }

    /*package*/ static boolean isModern(Class<? extends LoadStatistics> clazz) {
        // cannot use Util.isOverridden as these are protected methods.
        boolean hasGetNodes = false;
        boolean hasMatches = false;
        while (clazz != LoadStatistics.class && clazz != null && !(hasGetNodes && hasMatches)) {
            if (!hasGetNodes) {
                try {
                    final Method getNodes = clazz.getDeclaredMethod("getNodes");
                    hasGetNodes = !Modifier.isAbstract(getNodes.getModifiers());
                } catch (NoSuchMethodException e) {
                    // ignore
                }
            }
            if (!hasMatches) {
                try {
                    final Method getNodes = clazz.getDeclaredMethod("matches", Queue.Item.class, SubTask.class);
                    hasMatches = !Modifier.isAbstract(getNodes.getModifiers());
                } catch (NoSuchMethodException e) {
                    // ignore
                }
            }
            if (!(hasGetNodes && hasMatches) && LoadStatistics.class.isAssignableFrom(clazz.getSuperclass())) {
                clazz = (Class<? extends LoadStatistics>) clazz.getSuperclass();
            }
        }
        return hasGetNodes && hasMatches;
    }

    /**
     * @deprecated use {@link #idleExecutors} directly.
     */
    @Deprecated
    public float getLatestIdleExecutors(TimeScale timeScale) {
        return idleExecutors.pick(timeScale).getLatest();
    }

    /**
     * Computes the # of idle executors right now and obtains the snapshot value.
     * @deprecated use {@link #computeSnapshot()} and then {@link LoadStatisticsSnapshot#getIdleExecutors()}
     */
    @Deprecated
    public abstract int computeIdleExecutors();

    /**
     * Computes the # of total executors right now and obtains the snapshot value.
     * @deprecated use {@link #computeSnapshot()} and then {@link LoadStatisticsSnapshot#getOnlineExecutors()}
     */
    @Deprecated
    public abstract int computeTotalExecutors();

    /**
     * Computes the # of queue length right now and obtains the snapshot value.
     * @deprecated use {@link #computeSnapshot()} and then {@link LoadStatisticsSnapshot#getQueueLength()}
     */
    @Deprecated
    public abstract int computeQueueLength();

    /**
     * Creates a trend chart.
     */
    public JFreeChart createChart(CategoryDataset ds) {
        final JFreeChart chart = ChartFactory.createLineChart(null, // chart title
                null, // unused
                null, // range axis label
                ds, // data
                PlotOrientation.VERTICAL, // orientation
                true, // include legend
                true, // tooltips
                false // urls
                );

        chart.setBackgroundPaint(Color.white);

        final CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlinePaint(null);
        plot.setRangeGridlinesVisible(true);
        plot.setRangeGridlinePaint(Color.black);

        final LineAndShapeRenderer renderer = (LineAndShapeRenderer) plot.getRenderer();
        renderer.setBaseStroke(new BasicStroke(3));
        configureRenderer(renderer);

        final CategoryAxis domainAxis = new NoOverlapCategoryAxis(null);
        plot.setDomainAxis(domainAxis);
        domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
        domainAxis.setLowerMargin(0.0);
        domainAxis.setUpperMargin(0.0);
        domainAxis.setCategoryMargin(0.0);

        final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        // crop extra space around the graph
        plot.setInsets(new RectangleInsets(0, 0, 0, 5.0));

        return chart;
    }

    protected void configureRenderer(LineAndShapeRenderer renderer) {
        renderer.setSeriesPaint(0, ColorPalette.BLUE);  // online
        renderer.setSeriesPaint(1, ColorPalette.RED);   // busy
        renderer.setSeriesPaint(2, ColorPalette.GREY);  // queue
        renderer.setSeriesPaint(3, ColorPalette.YELLOW);// available
    }

    /**
     * Creates {@link CategoryDataset} which then becomes the basis
     * of the load statistics graph.
     */
    public TrendChart createTrendChart(TimeScale timeScale) {
        return MultiStageTimeSeries.createTrendChart(timeScale,onlineExecutors,busyExecutors,queueLength,availableExecutors);
    }

    /**
     * Generates the load statistics graph.
     */
    public TrendChart doGraph(@QueryParameter String type) throws IOException {
        return createTrendChart(TimeScale.parse(type));
    }

    public Api getApi() {
        return new Api(this);
    }

    /**
     * @deprecated use {@link #updateCounts(LoadStatisticsSnapshot)}
     */
    @Deprecated
    protected void updateExecutorCounts() {
        updateCounts(computeSnapshot());
    }

    /**
     * Updates all the series from the current snapshot.
     * @param current the current snapshot.
     * @since 1.607
     */
    protected void updateCounts(LoadStatisticsSnapshot current) {
        definedExecutors.update(current.getDefinedExecutors());
        onlineExecutors.update(current.getOnlineExecutors());
        connectingExecutors.update(current.getConnectingExecutors());
        busyExecutors.update(current.getBusyExecutors());
        idleExecutors.update(current.getIdleExecutors());
        availableExecutors.update(current.getAvailableExecutors());
        queueLength.update(current.getQueueLength());
    }

    /**
     * Returns the {@link Node} instances that this statistic counts.
     * @return the {@link Node}
     * @since 1.607
     */
    protected abstract Iterable<Node> getNodes();

    /**
     * Returns {@code true} is the specified {@link SubTask} from the {@link Queue} should be counted.
     * @param item the {@link Queue.Item} that the {@link SubTask belongs to}
     * @param subTask the {@link SubTask}
     * @return {@code true} IFF the specified {@link SubTask} from the {@link Queue} should be counted.
     * @since 1.607
     */
    protected abstract boolean matches(Queue.Item item, SubTask subTask);

    /**
     * Computes a self-consistent snapshot of the load statistics.
     *
     * Note: The original method of computing load statistics would compute the total and idle counts independently
     * which could lead to counting errors while jobs started in between the different state counting operations.
     * By returning a {@link LoadStatisticsSnapshot} we get a single consistent view of the counts which was valid
     * for at least one point in time during the execution of this method.
     *
     * @return a self-consistent snapshot of the load statistics.
     * @since 1.607
     */
    public LoadStatisticsSnapshot computeSnapshot() {
        if (modern) {
            return computeSnapshot(Jenkins.getInstance().getQueue().getBuildableItems());
        } else {
            int t = computeTotalExecutors();
            int i = computeIdleExecutors();
            return new LoadStatisticsSnapshot(t, t, Math.max(i-t,0), Math.max(t-i,0), i, i, computeQueueLength());
        }
    }

    /**
     * Computes the self-consistent snapshot with the specified queue items.

     * @param queue the queue items.
     * @return a self-consistent snapshot of the load statistics.
     * @since 1.607
     */
    protected LoadStatisticsSnapshot computeSnapshot(Iterable<Queue.BuildableItem> queue) {
        final LoadStatisticsSnapshot.Builder builder = LoadStatisticsSnapshot.builder();
        final Iterable<Node> nodes = getNodes();
        if (nodes != null) {
            for (Node node : nodes) {
                builder.with(node);
            }
        }
        int q = 0;
        if (queue != null) {
            for (Queue.BuildableItem item : queue) {
                
                for (SubTask st : item.task.getSubTasks()) {
                    if (matches(item, st))
                        q++;
                }
            }
        }
        return builder.withQueueLength(q).build();
    }

    /**
     * With 0.90 decay ratio for every 10sec, half reduction is about 1 min.
     * 
     * Put differently, the half reduction time is {@code CLOCK*log(0.5)/log(DECAY)}
     */
    public static final float DECAY = Float.parseFloat(SystemProperties.getString(LoadStatistics.class.getName()+".decay","0.9"));
    /**
     * Load statistics clock cycle in milliseconds. Specify a small value for quickly debugging this feature and node provisioning through cloud.
     */
    public static int CLOCK = SystemProperties.getInteger(LoadStatistics.class.getName() + ".clock", 10 * 1000);

    /**
     * Periodically update the load statistics average.
     */
    @Extension
    public static class LoadStatisticsUpdater extends PeriodicWork {
        public long getRecurrencePeriod() {
            return CLOCK;
        }

        protected void doRun() {
            Jenkins j = Jenkins.getInstance();
            List<Queue.BuildableItem> bis = j.getQueue().getBuildableItems();

            // update statistics on slaves
            for( Label l : j.getLabels() ) {
                l.loadStatistics.updateCounts(l.loadStatistics.computeSnapshot(bis));
            }

            // update statistics of the entire system
            j.unlabeledLoad.updateCounts(j.unlabeledLoad.computeSnapshot(bis));

            j.overallLoad.updateCounts(j.overallLoad.computeSnapshot(bis));
        }

        private int count(List<Queue.BuildableItem> bis, Label l) {
            int q=0;
            for (Queue.BuildableItem bi : bis) {
                for (SubTask st : Tasks.getSubTasksOf(bi.task))
                    if (bi.getAssignedLabelFor(st)==l)
                        q++;
            }
            return q;
        }
    }

    /**
     * Holds a snapshot of the current statistics.
     * @since 1.607
     */
    @ExportedBean
    public static class LoadStatisticsSnapshot implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * The total number of executors that Jenkins currently knows, this includes all off-line slaves.
         */
        private final int definedExecutors;
        /**
         * The total number of executors that are currently on-line.
         */
        private final int onlineExecutors;
        /**
         * The total number of executors that are currently in the process of connecting to Jenkins.
         */
        private final int connectingExecutors;
        /**
         * The total number of executors that are currently busy running jobs.
         */
        private final int busyExecutors;
        /**
         * The total number of executors that are currently on-line and idle. This includes executors that are
         * not accepting tasks.
         */
        private final int idleExecutors;
        /**
         * The total number of executors that are currently on-line, idle and accepting tasks.
         */
        private final int availableExecutors;
        /**
         * The number of items in the queue.
         */
        private final int queueLength;

        private LoadStatisticsSnapshot(
                int definedExecutors, int onlineExecutors, int connectingExecutors,
                int busyExecutors, int idleExecutors, int availableExecutors,
                int queueLength) {
            this.definedExecutors = definedExecutors;
            this.onlineExecutors = onlineExecutors;
            this.connectingExecutors = connectingExecutors;
            // assert definedExecutors == onlineExecutors + connectingExecutors;
            this.busyExecutors = busyExecutors;
            this.idleExecutors = idleExecutors;
            // assert onlineExecutors == busyExecutors + idleExecutors;
            this.availableExecutors = availableExecutors;
            // assert availableExecutors <= idleExecutors;
            this.queueLength = queueLength;
        }

        /**
          * The total number of executors that Jenkins currently knows, this includes all off-line slaves.
          */
        @Exported
        public int getDefinedExecutors() {
            return definedExecutors;
        }

        /**
         * The total number of executors that are currently on-line.
         */
        @Exported
        public int getOnlineExecutors() {
            return onlineExecutors;
        }

        /**
         * The total number of executors that are currently in the process of connecting to Jenkins.
         */
        @Exported
        public int getConnectingExecutors() {
            return connectingExecutors;
        }

        /**
         * The total number of executors that are currently busy running jobs.
         */
        @Exported
        public int getBusyExecutors() {
            return busyExecutors;
        }

        /**
         * The total number of executors that are currently on-line and idle. This includes executors that are
         * not accepting tasks.
         */
        @Exported
        public int getIdleExecutors() {
            return idleExecutors;
        }

        /**
         * The total number of executors that are currently on-line, idle and accepting tasks.
         */
        @Exported
        public int getAvailableExecutors() {
            return availableExecutors;
        }

        /**
         * The number of items in the queue.
         */
        @Exported
        public int getQueueLength() {
            return queueLength;
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            LoadStatisticsSnapshot that = (LoadStatisticsSnapshot) o;

            if (availableExecutors != that.availableExecutors) {
                return false;
            }
            if (busyExecutors != that.busyExecutors) {
                return false;
            }
            if (connectingExecutors != that.connectingExecutors) {
                return false;
            }
            if (definedExecutors != that.definedExecutors) {
                return false;
            }
            if (idleExecutors != that.idleExecutors) {
                return false;
            }
            if (onlineExecutors != that.onlineExecutors) {
                return false;
            }
            if (queueLength != that.queueLength) {
                return false;
            }

            return true;
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            int result = definedExecutors;
            result = 31 * result + onlineExecutors;
            result = 31 * result + connectingExecutors;
            result = 31 * result + busyExecutors;
            result = 31 * result + idleExecutors;
            result = 31 * result + availableExecutors;
            result = 31 * result + queueLength;
            return result;
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("LoadStatisticsSnapshot{");
            sb.append("definedExecutors=").append(definedExecutors);
            sb.append(", onlineExecutors=").append(onlineExecutors);
            sb.append(", connectingExecutors=").append(connectingExecutors);
            sb.append(", busyExecutors=").append(busyExecutors);
            sb.append(", idleExecutors=").append(idleExecutors);
            sb.append(", availableExecutors=").append(availableExecutors);
            sb.append(", queueLength=").append(queueLength);
            sb.append('}');
            return sb.toString();
        }

        /**
         * Use a builder so we can add more stats if needed.
         * Not thread safe
         * @since 1.607
         */
        public static class Builder {
            private int definedExecutors;
            private int onlineExecutors;
            private int connectingExecutors;
            private int busyExecutors;
            private int idleExecutors;
            private int availableExecutors;
            private int queueLength;

            public LoadStatisticsSnapshot build() {
                return new LoadStatisticsSnapshot(
                        definedExecutors, onlineExecutors, connectingExecutors,
                        busyExecutors, idleExecutors, availableExecutors,
                        queueLength
                );
            }

            public Builder withQueueLength(int queueLength) {
                this.queueLength = queueLength;
                return this;
            }

            public Builder with(@CheckForNull Node node) {
                if (node != null) {
                    return with(node.toComputer());
                }
                return this;
            }

            public Builder with(@CheckForNull Computer computer) {
                if (computer == null) {
                    return this;
                }
                if (computer.isOnline()) {
                    final List<Executor> executors = computer.getExecutors();
                    final boolean acceptingTasks = computer.isAcceptingTasks();
                    for (Executor e : executors) {
                        definedExecutors++;
                        onlineExecutors++;
                        if (e.getCurrentWorkUnit() != null) {
                            busyExecutors++;
                        } else {
                            idleExecutors++;
                            if (acceptingTasks) availableExecutors++;
                        }
                    }
                } else {
                    final int numExecutors = computer.getNumExecutors();
                    definedExecutors += numExecutors;
                    if (computer.isConnecting()) {
                        connectingExecutors += numExecutors;
                    }
                }
                return this;
            }

        }

        public static Builder builder() {
            return new Builder();
        }
    }

}
