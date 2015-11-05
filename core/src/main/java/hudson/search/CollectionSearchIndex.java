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

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * {@link SearchIndex} built on a {@link Map}.
 * 
 * @author Kohsuke Kawaguchi
 */
public abstract class CollectionSearchIndex<SMT extends SearchableModelObject> implements SearchIndex {
    /**
     * Gets a single item that exactly matches the given key.
     */
    protected abstract SearchItem get(String key);

    /**
     * Returns all items in the map.
     * The collection can include null items.
     */
    protected abstract Collection<SMT> all();

    public void find(String token, List<SearchItem> result) {
        SearchItem p = get(token);
        if(p!=null)
            result.add(p);
    }

    public void suggest(String token, List<SearchItem> result) {
         Collection<SMT> items = all();
        boolean isCaseSensitive = UserSearchProperty.isCaseInsensitive();
        if(isCaseSensitive){
          token = token.toLowerCase();
        }
        if(items==null)     return;
        for (SMT o : items) {
            String name = getName(o);
            if(isCaseSensitive)
                name=name.toLowerCase();
            if(o!=null && name.contains(token))
                result.add(o);
        }
    }

    protected String getName(SMT o) {
        return o.getDisplayName();
    }
}
