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

import java.util.List;

/**
 * Union of two sets.
 *
 * @author Kohsuke Kawaguchi
 */
public class UnionSearchIndex implements SearchIndex {
    public static SearchIndex combine(SearchIndex... sets) {
        SearchIndex p=EMPTY;
        for (SearchIndex q : sets) {
            // allow some of the inputs to be null,
            // and also recognize EMPTY
            if (q != null && q != EMPTY) {
                if (p == EMPTY)
                    p = q;
                else
                    p = new UnionSearchIndex(p,q);
            }
        }
        return p;
    }

    private final SearchIndex lhs,rhs;

    public UnionSearchIndex(SearchIndex lhs, SearchIndex rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    public void find(String token, List<SearchItem> result) {
        lhs.find(token,result);
        rhs.find(token,result);
    }

    public void suggest(String token, List<SearchItem> result) {
        lhs.suggest(token,result);
        rhs.suggest(token,result);
    }
}
