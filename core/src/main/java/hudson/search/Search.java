package hudson.search;

import hudson.util.EditDistance;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
                }
            }
        }

        // TODO: go to suggestion page
        throw new UnsupportedOperationException();
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
