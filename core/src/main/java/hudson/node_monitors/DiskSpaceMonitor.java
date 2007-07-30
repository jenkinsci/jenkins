package hudson.node_monitors;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.remoting.VirtualChannel;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;

/**
 * Checks available disk space of the node.
 * Requres Mustang.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.123
 */
public class DiskSpaceMonitor extends NodeMonitor {
    public Descriptor<NodeMonitor> getDescriptor() {
        return DESCRIPTOR;
    }

    public Long getFreeSpace(Computer c) {
        return DESCRIPTOR.get(c);
    }

    /**
     * Returns the HTML representation of the space.
     */
    public String toHtml(long space) {
        space/=1024L;   // convert to KB
        space/=1024L;   // convert to MB
        if(space<1024) {
            // less than a GB
            return "<span class=error>"+new BigDecimal(space).scaleByPowerOfTen(-3).toPlainString()+"GB<span>";
        }

        return space/1024+"GB";
    }

    public static final AbstractNodeMonitorDescriptor<Long> DESCRIPTOR = new AbstractNodeMonitorDescriptor<Long>(DiskSpaceMonitor.class) {
        protected Long monitor(Computer c) throws IOException, InterruptedException {
            FilePath p = c.getNode().getRootPath();
            if(p==null) return null;

            return p.act(new FileCallable<Long>() {
                public Long invoke(File f, VirtualChannel channel) throws IOException {
                    return f.getUsableSpace();
                }
            });
        }

        public String getDisplayName() {
            return "Free Disk Space";
        }

        public NodeMonitor newInstance(StaplerRequest req) throws FormException {
            return new DiskSpaceMonitor();
        }
    };
}
