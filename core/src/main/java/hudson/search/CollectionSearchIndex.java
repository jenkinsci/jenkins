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
    protected abstract SearchItem get(String key);
    protected abstract Collection<? extends SearchableModelObject> all();

    public void find(String token, List<SearchItem> result) {
        SearchItem p = get(token);
        if(p!=null)
            result.add(p);
    }

    public void suggest(String token, List<SearchItem> result) {
        for (SearchableModelObject o : all()) {
            if(o.getDisplayName().contains(token))
                result.add(o);
        }
    }
}
