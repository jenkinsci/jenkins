package hudson.search;

import hudson.util.EditDistance;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Flavor;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Web-bound object that serves QuickSilver-like search requests.
 *
 * @author Kohsuke Kawaguchi
 */
public class Search {
    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException {
        for (Ancestor a : req.getAncestors()) {
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
     * Used by search box auto-completion. Returns JSON array.
     */
    public void doSuggest(StaplerRequest req, StaplerResponse rsp, @QueryParameter("query")String query) throws IOException, ServletException {
        SearchIndexBuilder builder = new SearchIndexBuilder();
        for (Ancestor a : req.getAncestors()) {
            if (a.getObject() instanceof SearchableModelObject) {
                SearchableModelObject smo = (SearchableModelObject) a.getObject();
                builder.add(smo.getSearchIndex());
            }
        }

        Result r = new Result();
        Set<String> paths = new HashSet<String>();  // paths already added, to control duplicates
        for (SuggestedItem item : suggest(builder.make(), query)) {
            if(r.suggestions.size()>20) break;

            String p = item.getPath();
            if(paths.add(p))
                r.suggestions.add(new Item(p));
        }

        //// DEBUG
        //r.suggestions = Arrays.asList(new Item("aaa"),new Item("aab"),new Item("bbb"),new Item("bbc"),new Item("bbd"));

        rsp.serveExposedBean(req,r,Flavor.JSON);
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

    private static List<SuggestedItem> find(Mode m, SearchIndex index, String tokenList) {
        List<SuggestedItem> front = new ArrayList<SuggestedItem>();
        List<SuggestedItem> back = new ArrayList<SuggestedItem>();
        List<SearchItem> items = new ArrayList<SearchItem>(); // items found in 1 step

        StringTokenizer tokens = new StringTokenizer(tokenList);
        if(!tokens.hasMoreTokens()) return front;   // no tokens given

        // first token
        m.find(index,tokens.nextToken(),items);
        for (SearchItem i : items)
            front.add(new SuggestedItem(i));

        // successive tokens
        while(tokens.hasMoreTokens()) {
            String token = tokens.nextToken();

            back.clear();
            for (SuggestedItem r : front) {
                items.clear();
                m.find(r.item.getSearchIndex(),token,items);
                for (SearchItem i : items)
                    back.add(new SuggestedItem(r,i));
            }

            {// swap front and back
                List<SuggestedItem> t = front;
                front = back;
                back = t;
            }
        }

        return front;
    }
}
