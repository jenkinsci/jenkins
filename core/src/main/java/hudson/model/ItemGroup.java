package hudson.model;

import java.util.Collection;

/**
 * Represents a grouping inherent to a kind of {@link Item}s.
 *
 * @author Kohsuke Kawaguchi
 */
public interface ItemGroup<T extends Item> extends PersistenceRoot, ModelObject {

    /**
     * Gets all the items in this collection in a read-only view.
     *
     * TODO: check if this is really needed
     */
    Collection<T> getItems();

    /**
     * Checks if the job is in this collection.
     *
     * TODO: check if this is really needed
     */
    boolean contains(T item);

    /**
     * Returns the path relative to the context root,
     * like "foo/bar/zot/". Note no leading slash but trailing slash.
     */
    String getUrl();

    /**
     * Gets the URL token that prefixes the URLs for child {@link Item}s.
     * Like "job", "item", etc.
     */
    String getUrlChildPrefix();
}
