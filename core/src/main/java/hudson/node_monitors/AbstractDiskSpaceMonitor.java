package hudson.node_monitors;

import hudson.model.Computer;
import hudson.node_monitors.DiskSpaceMonitorDescriptor.DiskSpace;
import org.kohsuke.stapler.DataBoundConstructor;

import java.text.ParseException;
import java.util.logging.Logger;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractDiskSpaceMonitor extends NodeMonitor {
    /**
     * The free space threshold, below which the node monitor will be triggered.
     * This is a human readable string representation as entered by the user, so that we can retain the original notation.
     */
    public final String freeSpaceThreshold;

    @DataBoundConstructor
    public AbstractDiskSpaceMonitor(String threshold) throws ParseException {
        this.freeSpaceThreshold = threshold;
        DiskSpace.parse(threshold); // make sure it parses
    }

    public AbstractDiskSpaceMonitor() {
        this.freeSpaceThreshold = "1GB";
    }

    public long getThresholdBytes() {
        if (freeSpaceThreshold==null)
            return DEFAULT_THRESHOLD; // backward compatibility with the data format that didn't have 'freeSpaceThreshold'
        try {
            return DiskSpace.parse(freeSpaceThreshold).size;
        } catch (ParseException e) {
            return DEFAULT_THRESHOLD;
        }
    }

    @Override
    public Object data(Computer c) {
    	DiskSpace size = (DiskSpace) super.data(c);
        if(size!=null && size.size < getThresholdBytes()) {
        	size.setTriggered(true);
        	if(getDescriptor().markOffline(c,size)) {
        		LOGGER.warning(Messages.DiskSpaceMonitor_MarkedOffline(c.getName()));
        	}
        }
        return size;
    }

    private static final Logger LOGGER = Logger.getLogger(AbstractDiskSpaceMonitor.class.getName());
    private static final long DEFAULT_THRESHOLD = 1024L*1024*1024;
}
