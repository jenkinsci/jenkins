package hudson.node_monitors;

import hudson.model.Computer;
import hudson.node_monitors.DiskSpaceMonitorDescriptor.DiskSpace;
import java.text.ParseException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.kohsuke.stapler.DataBoundSetter;

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
    private String freeSpaceWarningThreshold;

    protected AbstractDiskSpaceMonitor(String threshold) throws ParseException {
        this.freeSpaceThreshold = threshold;
        DiskSpace.parse(threshold); // make sure it parses
    }

    protected AbstractDiskSpaceMonitor() {
        this.freeSpaceThreshold = "1GiB";
        this.freeSpaceWarningThreshold = "2GiB";
    }

    public Object readResolve() {
        if (freeSpaceWarningThreshold == null) {
            if (freeSpaceThreshold != null) {
                Pattern p = Pattern.compile("(\\d+)(.*)");
                Matcher m = p.matcher(freeSpaceThreshold);
                if (m.matches()) {
                    String digits = m.group(1);
                    String unit = m.group(2);
                    try {
                        int wt = Integer.parseInt(digits) * 2;
                        freeSpaceWarningThreshold = wt + unit;
                    } catch (NumberFormatException nfe) {
                        // unreachable
                        freeSpaceWarningThreshold = "2GiB";
                    }
                }
            } else {
                freeSpaceWarningThreshold = "2GiB";
            }
        }
        return this;
    }

    @DataBoundSetter
    public void setFreeSpaceWarningThreshold(String freeSpaceWarningThreshold) {
        this.freeSpaceWarningThreshold = freeSpaceWarningThreshold;
    }

    public String getFreeSpaceWarningThreshold() {
        return freeSpaceWarningThreshold;
    }

    public long getThresholdBytes() {
        try {
            return DiskSpace.parse(freeSpaceThreshold).size;
        } catch (ParseException e) {
            return DEFAULT_THRESHOLD;
        }
    }

    protected long getThresholdBytes(Computer c) {
        return getThresholdBytes();
    }

    protected long getWarningThresholdBytes() {
        if (freeSpaceWarningThreshold == null)
            return DEFAULT_THRESHOLD * 2;
        try {
            return DiskSpace.parse(freeSpaceWarningThreshold).size;
        } catch (ParseException e) {
            return DEFAULT_THRESHOLD * 2;
        }
    }

    protected long getWarningThresholdBytes(Computer c) {
        return getWarningThresholdBytes();
    }

    @Override
    public Object data(Computer c) {
        DiskSpace size = (DiskSpace) super.data(c);
        ((DiskSpaceMonitorDescriptor) getDescriptor()).markNodeOfflineOrOnline(c, size, this);
        return size;
    }

    private static final Logger LOGGER = Logger.getLogger(AbstractDiskSpaceMonitor.class.getName());
    private static final long DEFAULT_THRESHOLD = 1024L * 1024 * 1024;
}
