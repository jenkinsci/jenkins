package hudson.views;

import hudson.ExtensionPoint;
import hudson.tasks.Publisher;
import hudson.model.Describable;
import hudson.model.ListView;
import hudson.model.Item;
import hudson.util.DescriptorList;
import org.kohsuke.stapler.export.Exported;

/**
 * Extension point for adding a column to {@link ListView}.
 *
 * <p>
 * This object must have the <tt>cell.jelly</tt>. This view
 * is called for each cell of this column. The {@link Item} object
 * is passed in the "job" variable. The view should render
 * the &lt;td> tag.
 *
 * <p>
 * For now, {@link ListView} doesn't allow {@link ListViewColumn}s to be configured
 * (instead it just shows all the columns available in {@link #LIST}),
 * but the intention is eventually make each {@link ListViewColumn} fully configurable
 * like {@link Publisher}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.279
 */
public abstract class ListViewColumn implements ExtensionPoint, Describable<ListViewColumn> {
    /**
     * Returns the name of the column that explains what this column means
     *
     * @return
     *      The convention is to use capitalization like "Foo Bar Zot".
     */
    @Exported
    public String getColumnCaption() {
        return getDescriptor().getDisplayName();
    }

    /**
     * All registered {@link ListViewColumn}s.
     */
    public static final DescriptorList<ListViewColumn> LIST = new DescriptorList<ListViewColumn>();
}
