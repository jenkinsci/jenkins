package hudson.search;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;

/**
 * Creates a {@link Search} instance for a {@link SearchableModelObject}.
 *
 * <p>
 * This allows you to plug in different backends to the search, such as full-text search,
 * or more intelligent user-sensitive search, etc. Puts @{@link Extension} annotation
 * on your implementation to have it registered.
 *
 * <p>
 * Right now, there's no user control over which {@link SearchFactory} takes priority,
 * but we may do so later.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.469
 */
public abstract class SearchFactory implements ExtensionPoint {
    /**
     * Creates a {@link Search} object.
     *
     * This method needs to execute quickly (without actually executing any search),
     * since it is created per incoming HTTP response.
     *
     * @param owner
     *      The {@link SearchableModelObject} object for which we are creating the search.
     *      The returned object will provide the search for this object.
     * @return
     *      null if your factory isn't interested in creating a {@link Search} object.
     *      The next factory will get a chance to act on it.
     */
    public abstract Search createFor(SearchableModelObject owner);

    /**
     * Returns all the registered {@link SearchFactory} instances.
     */
    public static ExtensionList<SearchFactory> all() {
        return ExtensionList.lookup(SearchFactory.class);
    }
}
