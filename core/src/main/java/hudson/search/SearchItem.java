package hudson.search;

/**
 * @author Kohsuke Kawaguchi
 */
public interface SearchItem {
    String getSearchName();
    String getSearchUrlFragment();
    SearchIndex getSearchIndex();
}
