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
import hudson.model.MultiStageTimeSeries.TimeScale;
import hudson.model.MultiStageTimeSeries.TrendChart;
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
import java.util.List;

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
     * Number of busy executors and how it changes over time.
     */
    @Exported
    public final MultiStageTimeSeries busyExecutors;

    /**
     * Number of total executors and how it changes over time.
     */
    @Exported
    public final MultiStageTimeSeries totalExecutors;

    /**
     * Number of {@link hudson.model.Queue.BuildableItem}s that can run on any node in this node set but blocked.
     */
    @Exported
    public final MultiStageTimeSeries queueLength;

    protected LoadStatistics(int initialTotalExecutors, int initialBusyExecutors) {
        this.totalExecutors = new MultiStageTimeSeries(
                Messages._LoadStatistics_Legends_TotalExecutors(), ColorPalette.BLUE, initialTotalExecutors,DECAY);
        this.busyExecutors = new MultiStageTimeSeries(
                Messages._LoadStatistics_Legends_BusyExecutors(), ColorPalette.RED, initialBusyExecutors,DECAY);
        this.queueLength = new MultiStageTimeSeries(
                Messages._LoadStatistics_Legends_QueueLength(),ColorPalette.GREY, 0, DECAY);
    }

    public float getLatestIdleExecutors(TimeScale timeScale) {
        return totalExecutors.pick(timeScale).getLatest() - busyExecutors.pick(timeScale).getLatest();
    }

    /**
     * Computes the # of idle executors right now and obtains the snapshot value.
     */
    public abstract int computeIdleExecutors();

    /**
     * Computes the # of total executors right now and obtains the snapshot value.
     */
    public abstract int computeTotalExecutors();

    /**
     * Computes the # of queue length right now and obtains the snapshot value.
     */
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
        renderer.setSeriesPaint(0, ColorPalette.BLUE);  // total
        renderer.setSeriesPaint(1, ColorPalette.RED);   // busy
        renderer.setSeriesPaint(2, ColorPalette.GREY);  // queue
    }

    /**
     * Creates {@link CategoryDataset} which then becomes the basis
     * of the load statistics graph.
     */
    public TrendChart createTrendChart(TimeScale timeScale) {
        return MultiStageTimeSeries.createTrendChart(timeScale,totalExecutors,busyExecutors,queueLength);
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
     * Updates {@link #totalExecutors} and {@link #busyExecutors} by using
     * the current snapshot value.
     * 
     * {@link #queueLength} is updated separately via {@link LoadStatisticsUpdater} to
     * improve the efficiency because we are counting this for all {@link LoadStatistics} at once.
     */
    protected void updateExecutorCounts() {
        int t = computeTotalExecutors();
        int i = computeIdleExecutors();
        totalExecutors.update(t);
        busyExecutors.update(t-i);
    }

    /**
     * With 0.90 decay ratio for every 10sec, half reduction is about 1 min.
     */
    public static final float DECAY = Float.parseFloat(System.getProperty(LoadStatistics.class.getName()+".decay","0.9"));
    /**
     * Load statistics clock cycle in milliseconds. Specify a small value for quickly debugging this feature and node provisioning through cloud.
     */
    public static int CLOCK = Integer.getInteger(LoadStatistics.class.getName()+".clock",10*1000);

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
                l.loadStatistics.updateExecutorCounts();
                l.loadStatistics.queueLength.update(count(bis, l));
            }

            // update statistics of the entire system
            j.unlabeledLoad.updateExecutorCounts();
            j.unlabeledLoad.queueLength.update(count(bis, null));

            j.overallLoad.updateExecutorCounts();
            j.overallLoad.queueLength.update(bis.size());
        }

        private int count(List<Queue.BuildableItem> bis, Label l) {
            int q=0;
            for (Queue.BuildableItem bi : bis) {
                if(bi.task.getAssignedLabel()==l)
                    q++;
            }
            return q;
        }
    }
}
