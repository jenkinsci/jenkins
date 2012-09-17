/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.diagnosis;

import hudson.util.TimeUnit2;
import hudson.util.ColorPalette;
import hudson.Extension;
import hudson.model.PeriodicWork;
import hudson.model.MultiStageTimeSeries;
import hudson.model.MultiStageTimeSeries.TrendChart;
import hudson.model.MultiStageTimeSeries.TimeScale;

import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;

import org.kohsuke.stapler.QueryParameter;

/**
 * Monitors the memory usage of the system in OS specific way.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public final class MemoryUsageMonitor extends PeriodicWork {
    /**
     * A memory group is conceptually a set of memory pools. 
     */
    public final class MemoryGroup {
        private final List<MemoryPoolMXBean> pools = new ArrayList<MemoryPoolMXBean>();

        /**
         * Trend of the memory usage, after GCs.
         * So this shows the accurate snapshot of the footprint of live objects.
         */
        public final MultiStageTimeSeries used = new MultiStageTimeSeries(Messages._MemoryUsageMonitor_USED(), ColorPalette.RED, 0,0);
        /**
         * Trend of the maximum memory size, after GCs.
         */
        public final MultiStageTimeSeries max = new MultiStageTimeSeries(Messages._MemoryUsageMonitor_TOTAL(), ColorPalette.BLUE, 0,0);

        private MemoryGroup(List<MemoryPoolMXBean> pools, MemoryType type) {
            for (MemoryPoolMXBean pool : pools) {
                if (pool.getType() == type)
                    this.pools.add(pool);
            }
        }

        private void update() {
            long used = 0;
            long max = 0;
//            long cur = 0;
            for (MemoryPoolMXBean pool : pools) {
                MemoryUsage usage = pool.getCollectionUsage();
                if(usage==null) continue;   // not available
                used += usage.getUsed();
                max  += usage.getMax();

//                usage = pool.getUsage();
//                if(usage==null) continue;   // not available
//                cur += usage.getUsed();
            }

            // B -> KB
            used /= 1024;
            max /= 1024;
//            cur /= 1024;

            this.used.update(used);
            this.max.update(max);
//
//            return String.format("%d/%d/%d (%d%%)",used,cur,max,used*100/max);
        }

        /**
         * Generates the memory usage statistics graph.
         */
        public TrendChart doGraph(@QueryParameter String type) throws IOException {
            return MultiStageTimeSeries.createTrendChart(TimeScale.parse(type),used,max);
        }
    }

    public final MemoryGroup heap;
    public final MemoryGroup nonHeap;

    public MemoryUsageMonitor() {
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        heap = new MemoryGroup(pools, MemoryType.HEAP);
        nonHeap = new MemoryGroup(pools, MemoryType.NON_HEAP);
    }

    public long getRecurrencePeriod() {
        return TimeUnit2.SECONDS.toMillis(10);
    }

    protected void doRun() {
        heap.update();
        nonHeap.update();
    }
}
