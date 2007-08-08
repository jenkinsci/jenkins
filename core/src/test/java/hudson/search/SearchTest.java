package hudson.search;

import junit.framework.TestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class SearchTest extends TestCase {
    public void test1() {
        SearchIndex si = new SearchIndexBuilder()
            .add("abc-def-ghi","abc def ghi")
            .add(SearchItems.create("abc","abc",
                new SearchIndexBuilder()
                    .add("def-ghi","def ghi")
                    .make()))
            .make();

        SuggestedItem x = Search.find(si, "abc def ghi");
        assertNotNull(x);
        assertEquals(x.getUrl(),"abc-def-ghi");
    }
}
