package hudson.node_monitors;

import hudson.Util;
import hudson.model.Computer;
import hudson.remoting.Callable;
import net.sf.json.JSONObject;
import org.jvnet.hudson.MemoryMonitor;
import org.jvnet.hudson.MemoryUsage;
import org.kohsuke.stapler.StaplerRequest;

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
            return "Free Swap Space";
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
            return MemoryMonitor.get().monitor();
        }

        private static final long serialVersionUID = 1L;
    }

    static {
        LIST.add(DESCRIPTOR);
    }
}
