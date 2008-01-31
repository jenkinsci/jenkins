package hudson.node_monitors;

import hudson.ExtensionPoint;
import hudson.Functions;
import hudson.model.Computer;
import hudson.model.ComputerSet;
import hudson.model.Describable;
import hudson.model.Node;
import hudson.util.DescriptorList;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extension point for managing and monitoring {@link Node}s.
 *
 * <h2>Views</h2>
 * <dl>
 * <dt>column.jelly</dt>
 * <dd>
 * Invoked from {@link ComputerSet} <tt>index.jelly</tt> to render a column.
 * The {@link NodeMonitor} instance is accessible through the "from" variable.
 * Also see {@link #getColumnCaption()}.
 * </dl>
 *
 * @author Kohsuke Kawaguchi
 * @since 1.123
 */
public abstract class NodeMonitor implements ExtensionPoint, Describable<NodeMonitor> {
    /**
     * Returns the name of the column to be added to {@link ComputerSet} index.jelly.
     *
     * @return
     *      null to not render a column. The convention is to use capitalization like "Foo Bar Zot".
     */
    public String getColumnCaption() {
        return getDescriptor().getDisplayName();
    }

    public abstract AbstractNodeMonitorDescriptor<?> getDescriptor();

    public Object data(Computer c) {
        return getDescriptor().get(c);
    }


    /**
     * All registered {@link NodeMonitor}s.
     */
    public static final DescriptorList<NodeMonitor> LIST = new DescriptorList<NodeMonitor>();

    static {
        try {
            LIST.load(ClockMonitor.class);
            if(Functions.isMustangOrAbove())
                LIST.load(DiskSpaceMonitor.class);
            LIST.load(ArchitectureMonitor.class);
        } catch (Throwable e) {
            Logger.getLogger(NodeMonitor.class.getName()).log(Level.SEVERE, "Failed to load built-in monitors",e);
        }
    }
}
