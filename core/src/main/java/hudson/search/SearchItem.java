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

package hudson.search;

import hudson.model.Build;
import org.jenkins.ui.icon.IconSpec;

/**
 * Represents an item reachable from {@link SearchIndex}.
 *
 * <p>
 * The act of searching in this package is really a traversal of a directed graph.
 * And in that notion, this interface represents an edge, not a node.
 * So it's possible for single entity (let's say {@link Build}) to
 * have multiple {@link SearchItem}s representing it (for example,
 * a 'last successful build' search item and '#123' search item.)
 *
 * @author Kohsuke Kawaguchi
 */
public interface SearchItem {
    /**
     * Name of this item. This is matched against the query.
     */
    String getSearchName();
    /**
     * Returns the URL of this item relative to the parent {@link SearchItem}.
     *
     * @return
     *      URL like "foo" or "foo/bar". The path can end with '/'.
     *      The path that starts with '/' will be interpreted as the absolute path
     *      (within the context path of Jenkins.)
     */

    String getSearchUrl();

    default String getSearchIcon() {
        if (this instanceof IconSpec) {
            return ((IconSpec) this).getIconClassName();
        }

        return "symbol-search";
    }

    default SearchGroup getSearchGroup() {
        return SearchGroup.OTHER;
    }

    /**
     * Returns the {@link SearchIndex} to further search sub items inside this item.
     *
     * @return
     *      {@link SearchIndex#EMPTY} if this is a leaf.
     * @see SearchIndexBuilder
     */
    SearchIndex getSearchIndex();
}
