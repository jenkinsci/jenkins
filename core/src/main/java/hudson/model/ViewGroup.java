/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Tom Huybrechts, Alan Harder
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.model;

import hudson.security.AccessControlled;
import hudson.views.ViewsTabBar;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Container of {@link View}s.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.269
 */
public interface ViewGroup extends Saveable, ModelObject, AccessControlled {
    /**
     * Determine whether a view may be deleted.
     * @since 1.365
     */
    boolean canDelete(View view);

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
     * If the view group renders one view in {@linkplain #getUrl() its own URL} (like Jenkins top page does),
     * then that view is called the primary view. In this case, the hyperlink to the primary view points to
     * the view group itself.
     * <p>
     * If the view group doesn't do such rendering, this method can always return null.
     * <p>
     * This method was added later to {@link ViewGroup}, so old plugins might not be implementing this.
     * To work around this, {@link View}s can use {@link View#getOwnerPrimaryView()}.
     *
     * @since 1.417
     */
    View getPrimaryView();

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
     * {@linkplain jenkins.model.Jenkins#checkGoodName(String) legal view name}.
     */
    void onViewRenamed(View view, String oldName, String newName);

    /**
     * Gets the TabBar for the views.
     *
     * TabBar for views can be provided by extension. Only one TabBar can be active
     * at a given time (Selectable by user in the global Configuration page).
     * Default TabBar is provided by Hudson Platform.
     * @since 1.381
     */
    ViewsTabBar getViewsTabBar();

    /**
     * Returns the {@link ItemGroup} from which the views in this group should render items.
     *
     * <p>
     * Generally speaking, Views render a subset of {@link TopLevelItem}s that belong to this item group.
     * This method was added later to {@link ViewGroup}, so old plugins might not be implementing this.
     * To work around this, {@link View}s can use {@link View#getOwnerItemGroup()}.
     *
     * @return
     *      Never null. Sometimes this is {@link ModifiableItemGroup} (if the container allows arbitrary addition)
     * @since 1.417
     */
    ItemGroup<? extends TopLevelItem> getItemGroup();

    /**
     * Returns actions that should be displayed in views.
     *
     * <p>
     * In this interface, the return value is used read-only. This doesn't prevent subtypes
     * from returning modifiable actions, however.
     *
     * <p>
     * This method was added later to {@link ViewGroup}, so old plugins might not be implementing this.
     * To work around this, {@link View}s can use {@link View#getOwnerViewActions()}.
     *
     * @return
     *      may be empty but never null.
     * @see Actionable#getActions()
     * @since 1.417
     */
    List<Action> getViewActions();
    
}
