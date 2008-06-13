package hudson.node_monitors;

import hudson.FilePath;
import hudson.Util;
import hudson.FilePath.FileCallable;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.remoting.VirtualChannel;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;

import net.sf.json.JSONObject;

/**
 * Checks available disk space of the node.
 * Requres Mustang.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.123
 */
public class DiskSpaceMonitor extends NodeMonitor {
    public AbstractNodeMonitorDescriptor getDescriptor() {
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
            return Util.wrapToErrorSpan(new BigDecimal(space).scaleByPowerOfTen(-3).toPlainString());
        }

        return space/1024+"GB";
    }

    public static final AbstractNodeMonitorDescriptor<Long> DESCRIPTOR = new AbstractNodeMonitorDescriptor<Long>(DiskSpaceMonitor.class) {
        protected Long monitor(Computer c) throws IOException, InterruptedException {
            FilePath p = c.getNode().getRootPath();
            if(p==null) return null;

            return p.act(new GetUsableSpace());
        }

        public String getDisplayName() {
            return Messages.DiskSpaceMonitor_displayName();
        }

        @Override
        public NodeMonitor newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new DiskSpaceMonitor();
        }
    };

    private static final class GetUsableSpace implements FileCallable<Long> {
        public Long invoke(File f, VirtualChannel channel) throws IOException {
            try {
                return f.getUsableSpace();
            } catch (LinkageError e) {
                // pre-mustang
                return null;
            }
        }
        private static final long serialVersionUID = 1L;
    }

    static {
        LIST.add(DESCRIPTOR);
    }
}
