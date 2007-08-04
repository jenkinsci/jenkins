package hudson.search;

import hudson.model.Build;

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
     *      (within the context path of Hudson.)
     */
    String getSearchUrl();

    /**
     * Returns the {@link SearchIndex} to further search into this item.
     *
     * @return
     *      {@link SearchIndex#EMPTY} if this is a leaf.
     */
    SearchIndex getSearchIndex();
}
