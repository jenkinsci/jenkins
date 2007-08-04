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
