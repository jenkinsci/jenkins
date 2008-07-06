package hudson.node_monitors;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Util;
import hudson.model.Computer;
import hudson.remoting.VirtualChannel;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.logging.Logger;

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
            return Util.wrapToErrorSpan(new BigDecimal(space).scaleByPowerOfTen(-3).toPlainString()+"GB");
        }

        return space/1024+"GB";
    }

    public static final AbstractNodeMonitorDescriptor<Long> DESCRIPTOR = new AbstractNodeMonitorDescriptor<Long>(DiskSpaceMonitor.class) {
        protected Long monitor(Computer c) throws IOException, InterruptedException {
            FilePath p = c.getNode().getRootPath();
            if(p==null) return null;

            Long size = p.act(new GetUsableSpace());
            if(size!=null && size!=0 && size/(1024*1024*1024)==0) {
                // TODO: this scheme should be generalized, so that Hudson can remember why it's marking the node
                // as offline, as well as allowing the user to force Hudson to use it.
                if(!c.isTemporarilyOffline()) {
                    LOGGER.warning("Making "+c.getName()+" offline temporarily due to the lack of disk space");
                    c.setTemporarilyOffline(true);
                }
            }
            return size;
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

    private static final Logger LOGGER = Logger.getLogger(DiskSpaceMonitor.class.getName());
}
