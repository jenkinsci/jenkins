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

import junit.framework.TestCase;

import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class SearchTest extends TestCase {
    public void test1() {
        SearchIndex si = new SearchIndexBuilder()
            .add("abc-def-ghi","abc def ghi")
            .add(SearchItems.create("abc","abc",
                new SearchIndexBuilder()
                    .add("def-ghi","def ghixxx")
                    .make()))
            .make();

        SuggestedItem x = Search.find(si, "abc def ghi");
        assertNotNull(x);
        assertEquals(x.getUrl(),"/abc-def-ghi");

        List<SuggestedItem> l = Search.suggest(si, "abc def ghi");
        assertEquals(2,l.size());
        assertEquals("/abc-def-ghi",l.get(0).getUrl());
        assertEquals("/abc/def-ghi",l.get(1).getUrl());
    }
}
