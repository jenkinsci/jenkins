package hudson.model;

import hudson.triggers.SafeTimerTask;

import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.ArrayList;

/**
 * Monitors the memory usage of the system in OS specific way.
 *
 * @author Kohsuke Kawaguchi
 */
public class MemoryUsageMonitor extends SafeTimerTask {
    class MemoryGroup {
        private final List<MemoryPoolMXBean> pools = new ArrayList<MemoryPoolMXBean>();

        MemoryGroup(List<MemoryPoolMXBean> pools, MemoryType type) {
            for (MemoryPoolMXBean pool : pools) {
                if (pool.getType() == type)
                    this.pools.add(pool);
            }
        }

        public String metrics() {
            long used = 0;
            long max = 0;
            long cur = 0;
            for (MemoryPoolMXBean pool : pools) {
                MemoryUsage usage = pool.getCollectionUsage();
                if(usage==null) continue;   // not available
                used += usage.getUsed();
                max  += usage.getMax();

                usage = pool.getUsage();
                if(usage==null) continue;   // not available
                cur += usage.getUsed();
            }

            // B -> KB
            used /= 1024;
            max /= 1024;
            cur /= 1024;

            return String.format("%d/%d/%d (%d%%)",used,cur,max,used*100/max);
        }
    }

    private final MemoryGroup heap;
    private final MemoryGroup nonHeap;

    public MemoryUsageMonitor() {
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        heap = new MemoryGroup(pools, MemoryType.HEAP);
        nonHeap = new MemoryGroup(pools, MemoryType.NON_HEAP);
    }

    protected void doRun() {
        System.out.printf("%s\t%s\n", heap.metrics(), nonHeap.metrics());
    }
}
