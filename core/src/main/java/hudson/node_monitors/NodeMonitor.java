package hudson.node_monitors;

import hudson.ExtensionPoint;
import hudson.Functions;
import hudson.model.ComputerSet;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

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

    /**
     * All registered {@link NodeMonitor}s.
     */
    public static final List<Descriptor<NodeMonitor>> LIST = new ArrayList<Descriptor<NodeMonitor>>();

    static {
        try {
            LIST.add(ClockMonitor.DESCRIPTOR);
            if(Functions.isMustangOrAbove())
                LIST.add(DiskSpaceMonitor.DESCRIPTOR);
        } catch (Throwable e) {
            Logger.getLogger(NodeMonitor.class.getName()).log(Level.SEVERE, "Failed to load built-in monitors",e);
        }
    }
}
