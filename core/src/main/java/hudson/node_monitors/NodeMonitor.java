package hudson.node_monitors;

import hudson.ExtensionPoint;
import hudson.model.ComputerSet;
import hudson.model.Node;

import java.util.List;
import java.util.ArrayList;

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
public abstract class NodeMonitor implements ExtensionPoint {
    /**
     * Returns the name of the column to be added to {@link ComputerSet} index.jelly.
     *
     * @return
     *      null to not render a column. The convention is to use capitalization like "Foo Bar Zot".
     */
    public abstract String getColumnCaption();

    /**
     * All registered {@link NodeMonitor}s.
     */
    public static final List<NodeMonitor> LIST = new ArrayList<NodeMonitor>();

    static {
        LIST.add(new ClockMonitor());
    }
}
