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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Set of {@link SearchItem}s that are statically known upfront.
 *
 * @author Kohsuke Kawaguchi
 */
public class FixedSet implements SearchIndex {

    private final Collection<? extends SearchItem> items;

    public FixedSet(Collection<? extends SearchItem> items) {
        this.items = items;
    }

    public FixedSet(SearchItem... items) {
        this(Arrays.asList(items));
    }

    @Override
    public void find(String token, List<SearchItem> result) {
        boolean caseInsensitive = UserSearchProperty.isCaseInsensitive();
        for (SearchItem i : items) {
            String name = i.getSearchName();
            if (name != null && (name.equals(token) || (caseInsensitive && name.equalsIgnoreCase(token)))) {
                result.add(i);
            }
        }
    }

    @Override
    public void suggest(String token, List<SearchItem> result) {
        boolean caseInsensitive = UserSearchProperty.isCaseInsensitive();
        for (SearchItem i : items) {
            String name = i.getSearchName();
            if (name != null && (name.contains(token) || (caseInsensitive && name.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT))))) {
                result.add(i);
            }
        }
    }
}
