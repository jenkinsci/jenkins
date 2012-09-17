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

import hudson.model.AbstractModelObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link SearchIndex}.
 *
 * This object is also used to represent partially build search index, much like {@link StringBuilder} is often
 * passed around to cooperatively build search index.
 *
 * @author Kohsuke Kawaguchi
 * @see AbstractModelObject#makeSearchIndex()
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

    public SearchIndexBuilder add(SearchIndexBuilder index) {
        return add(index.make());
    }

    public SearchIndex make() {
        SearchIndex r = new FixedSet(items);
        for (SearchIndex index : indices)
            r = new UnionSearchIndex(r,index);
        return r;
    }
}
