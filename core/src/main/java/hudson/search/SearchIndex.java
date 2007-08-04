package hudson.search;

import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public interface SearchIndex {
    void find(String token, List<SearchItem> result);

    /**
     *
     * This method returns the superset of {@link #find(String, List)}.
     */
    void suggest(String token, List<SearchItem> result);

    /**
     * Empty set.
     */
    static final SearchIndex EMPTY = new SearchIndex() {
        public void find(String token, List<SearchItem> result) {
            // no item to contribute
        }
        public void suggest(String token, List<SearchItem> result) {
            // nothing to suggest
        }
    };
}
