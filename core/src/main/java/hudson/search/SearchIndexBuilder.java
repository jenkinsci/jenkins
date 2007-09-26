package hudson.search;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public final class SearchIndexBuilder {
    private final List<SearchItem> items = new ArrayList<SearchItem>();

    private final List<SearchIndex> indices = new ArrayList<SearchIndex>();

    /**
     * Adds all {@link QuickSilver}-annotated properties to the search index.
     */
    public SearchIndexBuilder addAllAnnotations(SearchableModelObject o) {
        ParsedQuickSilver.get(o.getClass()).addTo(this,o);
        return this;
    }

    /**
     * Short for {@code add(urlAsWellAsName,urlAsWellAsName)}
     */
    public SearchIndexBuilder add(String urlAsWellAsName) {
        return add(urlAsWellAsName,urlAsWellAsName);        
    }

    /**
     * Adds a search index under the keyword 'name' to the given URL.
     *
     * @param url
     *      Relative URL from the source of the search index. 
     */
    public SearchIndexBuilder add(String url, String name) {
        items.add(SearchItems.create(name,url));
        return this;
    }

    public SearchIndexBuilder add(String url, String... names) {
        for (String name : names)
            add(url,name);
        return this;
    }

    public SearchIndexBuilder add(SearchItem item) {
        items.add(item);
        return this;
    }

    public SearchIndexBuilder add(String url, SearchableModelObject searchable, String name) {
        items.add(SearchItems.create(name,url,searchable));
        return this;
    }

    public SearchIndexBuilder add(String url, SearchableModelObject searchable, String... names) {
        for (String name : names)
            add(url,searchable,name);
        return this;
    }

    public SearchIndexBuilder add(SearchIndex index) {
        this.indices.add(index);
        return this;
    }

    public SearchIndex make() {
        SearchIndex r = new FixedSet(items);
        for (SearchIndex index : indices)
            r = new UnionSearchIndex(r,index);
        return r;
    }
}
