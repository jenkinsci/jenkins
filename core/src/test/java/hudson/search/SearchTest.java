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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.Util;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * @author Kohsuke Kawaguchi
 */
class SearchTest {

    @Test
    void findAndSuggest() {
        SearchIndex si = new SearchIndexBuilder()
            .add("abc-def-ghi", "abc def ghi")
            .add(SearchItems.create("abc", "abc",
                new SearchIndexBuilder()
                    .add("def-ghi", "def ghixxx")
                    .make()))
            .make();

        SuggestedItem x = Search.find(si, "abc def ghi");
        assertNotNull(x);
        assertEquals("/abc-def-ghi", x.getUrl());

        List<SuggestedItem> l = Search.suggest(si, "abc def ghi");
        assertEquals(2, l.size());
        assertEquals("/abc-def-ghi", l.get(0).getUrl());
        assertEquals("/abc/def-ghi", l.get(1).getUrl());
    }

    /**
     * This test verifies that if 2 search results are found with the same
     * search name, that the one with the search query in the url is returned
     */
    @Test
    void findClosestSuggestedItem() {
        final String query = "foobar 123";
        final String searchName = "sameDisplayName";

        SearchItem searchItemHit = new SearchItem() {
            @Override
            public SearchIndex getSearchIndex() {
                    return null;
            }

            @Override
            public String getSearchName() {
                return searchName;
            }

            @Override
            public String getSearchUrl() {
                return "/job/" + Util.rawEncode(query) + "/";
            }
        };

        SearchItem searchItemNoHit = new SearchItem() {
            @Override
            public SearchIndex getSearchIndex() {
                    return null;
            }

            @Override
            public String getSearchName() {
                return searchName;
            }

            @Override
            public String getSearchUrl() {
                return "/job/someotherJob/";
            }
        };

        SuggestedItem suggestedHit = new SuggestedItem(searchItemHit);
        SuggestedItem suggestedNoHit = new SuggestedItem(searchItemNoHit);
        ArrayList<SuggestedItem> list = new ArrayList<>();
        list.add(suggestedNoHit);
        list.add(suggestedHit); // make sure the hit is the second item

        SuggestedItem found = Search.findClosestSuggestedItem(list, query);
        assertEquals(searchItemHit, found.item);

        SuggestedItem found2 = Search.findClosestSuggestedItem(list, "abcd");
        assertEquals(searchItemNoHit, found2.item);
    }
}
