package hudson.search;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * {@link SearchIndex} built on a {@link Map}.
 * 
 * @author Kohsuke Kawaguchi
 */
public abstract class CollectionSearchIndex implements SearchIndex {
    /**
     * Gets a single item that exactly matches the given key.
     */
    protected abstract SearchItem get(String key);

    /**
     * Returns all items in the map.
     * The collection can include null items.
     */
    protected abstract Collection<? extends SearchableModelObject> all();

    public void find(String token, List<SearchItem> result) {
        SearchItem p = get(token);
        if(p!=null)
            result.add(p);
    }

    public void suggest(String token, List<SearchItem> result) {
        Collection<? extends SearchableModelObject> items = all();
        if(items==null)     return;
        for (SearchableModelObject o : items) {
            if(o!=null && o.getDisplayName().contains(token))
                result.add(o);
        }
    }
}
