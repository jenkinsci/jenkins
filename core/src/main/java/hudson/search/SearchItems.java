package hudson.search;

/**
 * @author Kohsuke Kawaguchi
 */
public class SearchItems {
    public static SearchItem create(String searchName, String url) {
        return create(searchName,url, SearchIndex.EMPTY);
    }

    public static SearchItem create(final String searchName, final String url, final SearchIndex children) {
        return new SearchItem() {
            public String getSearchName() {
                return searchName;
            }

            public String getSearchUrl() {
                return url;
            }

            public SearchIndex getSearchIndex() {
                return children;
            }
        };
    }

    public static SearchItem create(final String searchName, final String url, final SearchableModelObject searchable) {
        return new SearchItem() {
            public String getSearchName() {
                return searchName;
            }

            public String getSearchUrl() {
                return url;
            }

            public SearchIndex getSearchIndex() {
                return searchable.getSearchIndex();
            }
        };
    }
}
