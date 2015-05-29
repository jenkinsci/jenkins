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

import hudson.model.MultiStageTimeSeries.TimeScale;
import hudson.model.MultiStageTimeSeries.TrendChart;
import hudson.model.queue.SubTask;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.export.Exported;

/**
 * {@link LoadStatistics} for the entire system (the master and all the slaves combined),
 * and all the jobs that are running on it.
 *
 * @author Kohsuke Kawaguchi
 * @see Jenkins#overallLoad
 * @see jenkins.model.UnlabeledLoadStatistics
 */
public class OverallLoadStatistics extends LoadStatistics {
    /**
     * Number of total {@link hudson.model.Queue.BuildableItem}s that represents blocked builds.
     *
     * @deprecated as of 1.467
     *      Use {@link #queueLength}. Left as an alias here for backward compatibility.
     */
    @Exported
    @Restricted(NoExternalUse.class)
    @Deprecated
    public final MultiStageTimeSeries totalQueueLength = queueLength;

    public OverallLoadStatistics() {
        super(0,0);
    }

    @Override
    public int computeIdleExecutors() {
        return new ComputerSet().getIdleExecutors();
    }

    @Override
    public int computeTotalExecutors() {
        return new ComputerSet().getTotalExecutors();
    }

    @Override
    public int computeQueueLength() {
        return Jenkins.getInstance().getQueue().countBuildableItems();
    }

    @Override
    protected Iterable<Node> getNodes() {
        return Jenkins.getActiveInstance().getNodes();
    }

    @Override
    protected boolean matches(Queue.Item item, SubTask subTask) {
        return true;
    }

    /**
     * When drawing the overall load statistics, use the total queue length,
     * not {@link #queueLength}, which just shows jobs that are to be run on the master.
     */
    protected TrendChart createOverallTrendChart(TimeScale timeScale) {
        return MultiStageTimeSeries.createTrendChart(timeScale,busyExecutors,onlineExecutors,queueLength,availableExecutors);
    }
}
