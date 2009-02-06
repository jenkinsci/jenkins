/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
