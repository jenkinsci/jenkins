package hudson.search;

import hudson.util.EditDistance;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Flavor;
import org.kohsuke.stapler.export.DataWriter;

import javax.servlet.ServletException;
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
    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException {
        List<Ancestor> l = req.getAncestors();
        for( int i=l.size()-1; i>=0; i-- ) {
            Ancestor a = l.get(i);
            if (a.getObject() instanceof SearchableModelObject) {
                SearchableModelObject smo = (SearchableModelObject) a.getObject();

                SearchIndex index = smo.getSearchIndex();
                String query = req.getParameter("q");
                SuggestedItem target = find(index, query);
                if(target!=null) {
                    // found
                    rsp.sendRedirect2(a.getUrl()+target.getUrl());
                    return;
                }
            }
        }

        // TODO: go to suggestion page
        throw new UnsupportedOperationException();
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
    public void doSuggestOpenSearch(StaplerRequest req, StaplerResponse rsp, @QueryParameter("q")String query) throws IOException, ServletException {
        DataWriter w = Flavor.JSON.createDataWriter(null, rsp);
        w.startArray();
        w.value(query);

        w.startArray();
        Set<String> paths = new HashSet<String>();  // paths already added, to control duplicates
        for (SuggestedItem item : suggest(makeSuggestIndex(req), query)) {
            if(paths.size()>20) break;
            
            String p = item.getPath();
            if(paths.add(p))
                w.value(p);
        }
        w.endArray();
        w.endArray();
    }

    /**
     * Used by search box auto-completion. Returns JSON array.
     */
    public void doSuggest(StaplerRequest req, StaplerResponse rsp, @QueryParameter("query")String query) throws IOException, ServletException {
        Result r = new Result();
        Set<String> paths = new HashSet<String>();  // paths already added, to control duplicates
        for (SuggestedItem item : suggest(makeSuggestIndex(req), query)) {
            if(paths.size()>20) break;

            String p = item.getPath();
            if(paths.add(p))
                r.suggestions.add(new Item(p));
        }

        rsp.serveExposedBean(req,r,Flavor.JSON);
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
            SuggestedItem item;
            int distance;

            Tag(SuggestedItem i) {
                this.item = i;
                distance = EditDistance.editDistance(i.getPath(),tokenList);
            }

            public int compareTo(Tag that) {
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
