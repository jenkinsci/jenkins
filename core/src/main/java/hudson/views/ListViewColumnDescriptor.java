package hudson.views;

import hudson.model.Descriptor;
import hudson.model.ListView;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link Descriptor} for {@link ListViewColumn}.
 *
 * <p>
 * {@link ListViewColumnDescriptor} can have the associated {@code config.jelly}, which will be rendered
 * in the view configuration page. When the configuration page is submitted, a new instance of
 * {@link ListViewColumn} will be created based on the submitted form, via {@link DataBoundConstructor}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.342
 */
public abstract class ListViewColumnDescriptor extends Descriptor<ListViewColumn> {
    /**
     * Whether this column will be shown by default on new/existing {@link ListView}s.
     * The default implementation is true.
     *
     * @since 1.342
     */
    public boolean shownByDefault() {
        return true;
    }
}
