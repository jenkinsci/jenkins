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

/**
 * @author Kohsuke Kawaguchi
 */
public class SearchItems {
    public static SearchItem create(String searchName, String url) {
        return create(searchName,url, SearchIndex.EMPTY);
    }

    public static SearchItem create(final String searchName, final String url, final SearchIndex children) {
        return new SearchItem() {
            public String getSearchName() {
                return searchName;
            }

            public String getSearchUrl() {
                return url;
            }

            public SearchIndex getSearchIndex() {
                return children;
            }
        };
    }

    public static SearchItem create(final String searchName, final String url, final SearchableModelObject searchable) {
        return new SearchItem() {
            public String getSearchName() {
                return searchName;
            }

            public String getSearchUrl() {
                return url;
            }

            public SearchIndex getSearchIndex() {
                return searchable.getSearchIndex();
            }
        };
    }
}
