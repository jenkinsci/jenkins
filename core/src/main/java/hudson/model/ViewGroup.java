package hudson.model;

import java.io.IOException;
import java.util.Collection;

/**
 * Container of {@link View}s.
 *
 * <h2>STILL EXPERIMENTAL: DO NOT IMPLEMENT</h2>
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.269
 */
public interface ViewGroup extends Saveable, ModelObject {
    /**
     * Deletes a view in this group.
     */
    void deleteView(View view) throws IOException;

    /**
     * Gets all the views in this group.
     *
     * @return
     *      can be empty but never null.
     */
    Collection<View> getViews();

    /**
     * Gets a view of the given name.
     *
     * This also creates the URL binding for views (in the form of ".../view/FOOBAR/...")
     */
    View getView(String name);

    /**
     * Returns the path of this group, relative to the context root,
     * like "foo/bar/zot/". Note no leading slash but trailing slash.
     */
    String getUrl();

    /**
     * {@link View} calls this method when it's renamed.
     * This method is intended to work as a notification to the {@link ViewGroup}
     * (so that it can adjust its internal data structure, for example.)
     *
     * <p>
     * It is the caller's responsibility to ensure that the new name is a
     * {@linkplain Hudson#checkGoodName(String) legal view name}.
     */
    void onViewRenamed(View view, String oldName, String newName);
}
