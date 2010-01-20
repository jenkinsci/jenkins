package hudson.views;

import hudson.model.Descriptor;
import hudson.model.ListView;

/**
 * {@link Descriptor} for {@link ListViewColumn}.
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
