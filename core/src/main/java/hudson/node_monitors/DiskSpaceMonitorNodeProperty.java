package hudson.node_monitors;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Node;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class DiskSpaceMonitorNodeProperty extends NodeProperty<Node> {
    private final String freeDiskSpaceThreshold;
    private final String freeTempSpaceThreshold;

    @DataBoundConstructor
    public DiskSpaceMonitorNodeProperty(String freeDiskSpaceThreshold, String freeTempSpaceThreshold) {
        this.freeDiskSpaceThreshold = freeDiskSpaceThreshold;
        this.freeTempSpaceThreshold = freeTempSpaceThreshold;
    }

    public String getFreeDiskSpaceThreshold() {
        return freeDiskSpaceThreshold;
    }

    public String getFreeTempSpaceThreshold() {
        return freeTempSpaceThreshold;
    }

    @Extension
    @Symbol("diskspaceMonitor")
    public static class DescriptorImpl extends NodePropertyDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.DiskSpaceMonitorNodeProperty_DisplayName();
        }
    }

}
