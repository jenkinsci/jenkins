package hudson.node_monitors;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Node;
import hudson.agents.NodeProperty;
import hudson.agents.NodePropertyDescriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link NodeProperty} that allows users to set agent specific disk space thresholds.
 *
 * @since 2.434
 */
public class DiskSpaceMonitorNodeProperty extends NodeProperty<Node> {
    private final String freeDiskSpaceThreshold;
    private final String freeTempSpaceThreshold;

    private final String freeDiskSpaceWarningThreshold;
    private final String freeTempSpaceWarningThreshold;

    @DataBoundConstructor
    public DiskSpaceMonitorNodeProperty(String freeDiskSpaceThreshold, String freeTempSpaceThreshold,
                                        String freeDiskSpaceWarningThreshold, String freeTempSpaceWarningThreshold) {
        this.freeDiskSpaceThreshold = freeDiskSpaceThreshold;
        this.freeTempSpaceThreshold = freeTempSpaceThreshold;
        this.freeDiskSpaceWarningThreshold = freeDiskSpaceWarningThreshold;
        this.freeTempSpaceWarningThreshold = freeTempSpaceWarningThreshold;
    }

    public String getFreeDiskSpaceThreshold() {
        return freeDiskSpaceThreshold;
    }

    public String getFreeTempSpaceThreshold() {
        return freeTempSpaceThreshold;
    }

    public String getFreeDiskSpaceWarningThreshold() {
        return freeDiskSpaceWarningThreshold;
    }

    public String getFreeTempSpaceWarningThreshold() {
        return freeTempSpaceWarningThreshold;
    }

    @Extension
    @Symbol("diskSpaceMonitor")
    public static class DescriptorImpl extends NodePropertyDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.DiskSpaceMonitorNodeProperty_DisplayName();
        }
    }

}
