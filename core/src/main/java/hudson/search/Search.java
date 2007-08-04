package hudson.search;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Collections;

import hudson.util.EditDistance;

/**
 * @author Kohsuke Kawaguchi
 */
public class Search {
    //public static List<SearchItem> find(SearchIndex index, String tokenList) {
    //    List<SearchItem> result = new ArrayList<SearchItem>();
    //    StringTokenizer tokens = new StringTokenizer(tokenList);
    //
    //    while(tokens.hasMoreTokens()) {
    //        String token = tokens.nextToken();
    //
    //        result.clear();
    //        index.find(token,result);
    //
    //        index = SearchIndex.EMPTY;
    //        for (SearchItem r : result)
    //            index = UnionSearchIndex.combine(index,r.getSearchIndex());
    //    }
    //
    //    return result;
    //}

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

    public static SuggestedItem find(SearchIndex index, String tokenList) {
        List<SuggestedItem> r = find(Mode.FIND, index, tokenList);
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
