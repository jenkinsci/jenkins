package hudson.node_monitors;

import hudson.Util;
import hudson.model.Computer;
import hudson.remoting.Callable;
import net.sf.json.JSONObject;
import org.jvnet.hudson.MemoryMonitor;
import org.jvnet.hudson.MemoryUsage;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Exported;

import java.io.IOException;

/**
 * Checks the swap space availability.
 *
 * @author Kohsuke Kawaguchi
 * @sine 1.233
 */
public class SwapSpaceMonitor extends NodeMonitor {
    public AbstractNodeMonitorDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    /**
     * Returns the HTML representation of the space.
     */
    public String toHtml(MemoryUsage usage) {
        if(usage.availableSwapSpace==-1)
            return "N/A";

        long free = usage.availableSwapSpace;
        free/=1024L;   // convert to KB
        free/=1024L;   // convert to MB
        if(free>256 || usage.totalSwapSpace/usage.availableSwapSpace<5)
            return free+"MB"; // if we have more than 256MB free or less than 80% filled up, it's OK

        // Otherwise considered dangerously low.
        return Util.wrapToErrorSpan(free+"MB");
    }

    public long toMB(MemoryUsage usage) {
        if(usage.availableSwapSpace==-1)
            return -1;

        long free = usage.availableSwapSpace;
        free/=1024L;   // convert to KB
        free/=1024L;   // convert to MB
        return free;
    }

    public static final AbstractNodeMonitorDescriptor<MemoryUsage> DESCRIPTOR = new AbstractNodeMonitorDescriptor<MemoryUsage>(DiskSpaceMonitor.class) {
        protected MemoryUsage monitor(Computer c) throws IOException, InterruptedException {
            return c.getChannel().call(new MonitorTask());
        }

        public String getDisplayName() {
            return Messages.SwapSpaceMonitor_displayName();
        }

        @Override
        public NodeMonitor newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new SwapSpaceMonitor();
        }
    };

    /**
     * Obtains the string that represents the architecture.
     */
    private static class MonitorTask implements Callable<MemoryUsage,IOException> {
        public MemoryUsage call() throws IOException {
            MemoryMonitor mm;
            try {
                mm = MemoryMonitor.get();
            } catch (IOException e) {
                if(!warned) {
                    // report the problem just once, and avoid filling up the log with the same error. see HUDSON-2194.
                    warned = true;
                    throw e;
                } else {
                    return null;
                }
            }
            return new MemoryUsage2(mm.monitor());
        }

        private static final long serialVersionUID = 1L;

        private static boolean warned = false;
    }

    /**
     * Memory Usage.
     *
     * <p>
     * {@link MemoryUsage} + stapler annotations.
     */
    @ExportedBean
    public static class MemoryUsage2 extends MemoryUsage {
        public MemoryUsage2(MemoryUsage mem) {
            super(mem.totalPhysicalMemory, mem.availablePhysicalMemory, mem.totalSwapSpace, mem.availableSwapSpace);
        }

        /**
         * Total physical memory of the system, in bytes.
         */
        @Exported
        public long getTotalPhysicalMemory() {
            return totalPhysicalMemory;
        }

        /**
         * Of the total physical memory of the system, available bytes.
         */
        @Exported
        public long getAvailablePhysicalMemory() {
            return availablePhysicalMemory;
        }

        /**
         * Total number of swap space in bytes.
         */
        @Exported
        public long getTotalSwapSpace() {
            return totalSwapSpace;
        }

        /**
         * Available swap space in bytes.
         */
        @Exported
        public long getAvailableSwapSpace() {
            return availableSwapSpace;
        }
    }

    static {
        LIST.add(DESCRIPTOR);
    }
}
