package hudson.node_monitors;

import hudson.model.Computer;
import hudson.node_monitors.DiskSpaceMonitorDescriptor.DiskSpace;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.text.ParseException;
import java.util.logging.Logger;

/**
 * @author Kohsuke Kawaguchi
 * @see DiskSpaceMonitorDescriptor
 */
public abstract class AbstractDiskSpaceMonitor extends NodeMonitor {
    /**
     * The free space threshold, below which the node monitor will be triggered.
     * This is a human readable string representation as entered by the user, so that we can retain the original notation.
     */
    public final String freeSpaceThreshold;

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
    	DiskSpace size = markNodeOfflineIfDiskspaceIsTooLow(c);
    	
    	// mark online (again), if free space is over threshold
        if(size!=null && size.size > getThresholdBytes() && c.isOffline() && c.getOfflineCause() instanceof DiskSpace)
            if(this.getClass().equals(((DiskSpace)c.getOfflineCause()).getTrigger()))
                if(getDescriptor().markOnline(c)) {
                    LOGGER.warning(Messages.DiskSpaceMonitor_MarkedOnline(c.getName()));
                }
        return size;
    }

    /**
     * Marks the given node as offline if free disk space is below the configured threshold.
     * @param c the node
     * @return the free space
     * @since 1.521
     */
    @Restricted(NoExternalUse.class)
    public DiskSpace markNodeOfflineIfDiskspaceIsTooLow(Computer c) {
        DiskSpace size = (DiskSpace) super.data(c);
        if(size!=null && size.size < getThresholdBytes()) {
        	size.setTriggered(this.getClass(), true);
        	if(getDescriptor().markOffline(c,size)) {
        		LOGGER.warning(Messages.DiskSpaceMonitor_MarkedOffline(c.getName()));
        	}
        }
        return size;
    }

    private static final Logger LOGGER = Logger.getLogger(AbstractDiskSpaceMonitor.class.getName());
    private static final long DEFAULT_THRESHOLD = 1024L*1024*1024;
}
