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

import hudson.util.EditDistance;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.DataWriter;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Flavor;

import javax.servlet.ServletException;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import java.io.IOException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Web-bound object that serves QuickSilver-like search requests.
 *
 * @author Kohsuke Kawaguchi
 */
public class Search {
    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        List<Ancestor> l = req.getAncestors();
        for( int i=l.size()-1; i>=0; i-- ) {
            Ancestor a = l.get(i);
            if (a.getObject() instanceof SearchableModelObject) {
                SearchableModelObject smo = (SearchableModelObject) a.getObject();

                SearchIndex index = smo.getSearchIndex();
                String query = req.getParameter("q");
                if(query!=null) {
                    SuggestedItem target = find(index, query);
                    if(target!=null) {
                        // found
                        rsp.sendRedirect2(a.getUrl()+target.getUrl());
                        return;
                    }
                }
            }
        }

        // no exact match. show the suggestions
        rsp.setStatus(SC_NOT_FOUND);
        req.getView(this,"search-failed.jelly").forward(req,rsp);
    }

    /**
     * Used by OpenSearch auto-completion. Returns JSON array of the form:
     *
     * <pre>
     * ["queryString",["comp1","comp2",...]]
     * </pre>
     *
     * See http://developer.mozilla.org/en/docs/Supporting_search_suggestions_in_search_plugins
     */
    public void doSuggestOpenSearch(StaplerRequest req, StaplerResponse rsp, @QueryParameter String q) throws IOException, ServletException {
        DataWriter w = Flavor.JSON.createDataWriter(null, rsp);
        w.startArray();
        w.value(q);

        w.startArray();
        for (SuggestedItem item : getSuggestions(req, q))
            w.value(item.getPath());
        w.endArray();
        w.endArray();
    }

    /**
     * Used by search box auto-completion. Returns JSON array.
     */
    public void doSuggest(StaplerRequest req, StaplerResponse rsp, @QueryParameter String query) throws IOException, ServletException {
        Result r = new Result();
        for (SuggestedItem item : getSuggestions(req, query))
            r.suggestions.add(new Item(item.getPath()));

        rsp.serveExposedBean(req,r,Flavor.JSON);
    }

    /**
     * Gets the list of suggestions that match the given query.
     *
     * @return
     *      can be empty but never null. The size of the list is always smaller than
     *      a certain threshold to avoid showing too many options. 
     */
    public List<SuggestedItem> getSuggestions(StaplerRequest req, String query) {
        Set<String> paths = new HashSet<String>();  // paths already added, to control duplicates
        List<SuggestedItem> r = new ArrayList<SuggestedItem>();
        for (SuggestedItem i : suggest(makeSuggestIndex(req), query)) {
            if(r.size()>20) break;
            if(paths.add(i.getPath()))
                r.add(i);
        }
        return r;
    }

    /**
     * Creates merged search index for suggestion.
     */
    private SearchIndex makeSuggestIndex(StaplerRequest req) {
        SearchIndexBuilder builder = new SearchIndexBuilder();
        for (Ancestor a : req.getAncestors()) {
            if (a.getObject() instanceof SearchableModelObject) {
                SearchableModelObject smo = (SearchableModelObject) a.getObject();
                builder.add(smo.getSearchIndex());
            }
        }
        return builder.make();
    }

    @ExportedBean
    public static class Result {
        @Exported
        public List<Item> suggestions = new ArrayList<Item>();
    }

    @ExportedBean(defaultVisibility=999)
    public static class Item {
        @Exported
        public String name;

        public Item(String name) {
            this.name = name;
        }
    }

    private enum Mode {
        FIND {
            void find(SearchIndex index, String token, List<SearchItem> result) {
                index.find(token, result);
            }
        },
        SUGGEST {
            void find(SearchIndex index, String token, List<SearchItem> result) {
                index.suggest(token, result);
            }
        };

        abstract void find(SearchIndex index, String token, List<SearchItem> result);

    }

    /**
     * Performs a search and returns the match, or null if no match was found.
     */
    public static SuggestedItem find(SearchIndex index, String query) {
        List<SuggestedItem> r = find(Mode.FIND, index, query);
        if(r.isEmpty()) return null;
        else            return r.get(0);
    }

    public static List<SuggestedItem> suggest(SearchIndex index, final String tokenList) {

        class Tag implements Comparable<Tag>{
            final SuggestedItem item;
            final int distance;
            /** If the path to this suggestion starts with the token list, 1. Otherwise 0. */
            final int prefixMatch;

            Tag(SuggestedItem i) {
                item = i;
                distance = EditDistance.editDistance(i.getPath(),tokenList);
                prefixMatch = i.getPath().startsWith(tokenList)?1:0;
            }

            public int compareTo(Tag that) {
                int r = this.prefixMatch -that.prefixMatch;
                if(r!=0)    return -r;  // ones with head match should show up earlier
                return this.distance-that.distance;
            }
        }

        List<Tag> buf = new ArrayList<Tag>();
        List<SuggestedItem> items = find(Mode.SUGGEST, index, tokenList);

        // sort them
        for( SuggestedItem i : items)
            buf.add(new Tag(i));
        Collections.sort(buf);
        items.clear();
        for (Tag t : buf)
            items.add(t.item);

        return items;
    }

    static final class TokenList {
        private final String[] tokens;

        public TokenList(String tokenList) {
            tokens = tokenList.split("(?<=\\s)(?=\\S)");
        }

        public int length() { return tokens.length; }

        /**
         * Returns {@link List} such that its <tt>get(end)</tt>
         * returns the concatanation of [token_start,...,token_end]
         * (both end inclusive.)
         */
        public List<String> subSequence(final int start) {
            return new AbstractList<String>() {
                public String get(int index) {
                    StringBuilder buf = new StringBuilder();
                    for(int i=start; i<=start+index; i++ )
                        buf.append(tokens[i]);
                    return buf.toString().trim();
                }

                public int size() {
                    return tokens.length-start;
                }
            };
        }
    }

    private static List<SuggestedItem> find(Mode m, SearchIndex index, String tokenList) {
        TokenList tokens = new TokenList(tokenList);
        if(tokens.length()==0) return Collections.emptyList();   // no tokens given

        List<SuggestedItem>[] paths = new List[tokens.length()+1]; // we won't use [0].
        for(int i=1;i<=tokens.length();i++)
            paths[i] = new ArrayList<SuggestedItem>();

        List<SearchItem> items = new ArrayList<SearchItem>(); // items found in 1 step


        // first token
        int w=1;    // width of token
        for (String token : tokens.subSequence(0)) {
            items.clear();
            m.find(index,token,items);
            for (SearchItem si : items)
                paths[w].add(new SuggestedItem(si));
            w++;
        }

        // successive tokens
        for (int j=1; j<tokens.length(); j++) {
            // for each length
            w=1;
            for (String token : tokens.subSequence(j)) {
                // for each candidate
                for (SuggestedItem r : paths[j]) {
                    items.clear();
                    m.find(r.item.getSearchIndex(),token,items);
                    for (SearchItem i : items)
                        paths[j+w].add(new SuggestedItem(r,i));
                }
                w++;
            }
        }

        return paths[tokens.length()];
    }
}
