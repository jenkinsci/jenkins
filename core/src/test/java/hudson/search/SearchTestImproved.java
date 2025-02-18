package hudson.search;

import hudson.Util;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

// A modified version of SearchTest that expands its coverage
public class SearchTestImproved {

    // --------------------- Existing coverage ---------------------
    @Test
    public void findAndSuggest() {
        SearchIndex si = new SearchIndexBuilder()
                .add("abc-def-ghi", "abc def ghi")
                .add(SearchItems.create("abc", "abc",
                        new SearchIndexBuilder()
                                .add("def-ghi", "def ghixxx")
                                .make()))
                .make();

        SuggestedItem x = Search.find(si, "abc def ghi");
        assertNotNull(x);
        assertEquals("/abc-def-ghi", x.getUrl());

        List<SuggestedItem> l = Search.suggest(si, "abc def ghi");
        assertEquals(2, l.size());
        assertEquals("/abc-def-ghi", l.get(0).getUrl());
        assertEquals("/abc/def-ghi", l.get(1).getUrl());
    }

    /**
     * This test verifies that if 2 search results are found with the same
     * search name, the one with the search query in the URL is returned.
     */
    @Test
    public void findClosestSuggestedItem() {
        final String query = "foobar 123";
        final String searchName = "sameDisplayName";

        SearchItem searchItemHit = new SearchItem() {
            @Override
            public SearchIndex getSearchIndex() {
                return null;
            }

            @Override
            public String getSearchName() {
                return searchName;
            }

            @Override
            public String getSearchUrl() {
                return "/job/" + Util.rawEncode(query) + "/";
            }
        };

        SearchItem searchItemNoHit = new SearchItem() {
            @Override
            public SearchIndex getSearchIndex() {
                return null;
            }

            @Override
            public String getSearchName() {
                return searchName;
            }

            @Override
            public String getSearchUrl() {
                return "/job/someotherJob/";
            }
        };

        SuggestedItem suggestedHit = new SuggestedItem(searchItemHit);
        SuggestedItem suggestedNoHit = new SuggestedItem(searchItemNoHit);
        ArrayList<SuggestedItem> list = new ArrayList<>();
        list.add(suggestedNoHit);
        list.add(suggestedHit); // make sure the hit is the second item

        SuggestedItem found = Search.findClosestSuggestedItem(list, query);
        assertEquals(searchItemHit, found.item);

        SuggestedItem found2 = Search.findClosestSuggestedItem(list, "abcd");
        assertEquals(searchItemNoHit, found2.item);
    }

    // --------------------- New coverage ---------------------

    // Covers all the overloads of SearchIndexBuilder to improve branch and line coverage
    @Test
    public void testSearchIndexBuilderOverloads() {
        // Create a dummy SearchableModelObject by implementing it
        // Note: We need this so we can call builder methods like addAllAnnotations() or
        //       add(String url, SearchableModelObject, ...)
        SearchableModelObject dummy = new SearchableModelObject() {
            // Returns the short name used in searches (we chose dummy for simplicity)
            @Override
            public String getSearchName() {
                return "dummy";
            }

            // This is the url that represents this object in Jenkins
            @Override
            public String getSearchUrl() {
                return "/dummy/";
            }

            // This is just the user-facing display name
            @Override
            public String getDisplayName() {
                return "Dummy Display";
            }

            // This method returns another SearchIndex for nested items in a model object.
            // If an object has related or "child" items - like a project that has jobs,
            // or a folder containing files - this method returns a SearchIndex containing
            // those subordinate items (e.g. /dummy/child)
            @Override
            public SearchIndex getSearchIndex() {
                // Return a simple sub-index
                return new SearchIndexBuilder().add("child", "child").make();
            }

            // We don't need specialized search logic for our testing purposes, so we just return null
            @Override
            public Search getSearch() {
                return null;
            }
        };

        // Create a new SearchIndexBuilder that we can populate with various items
        SearchIndexBuilder builder = new SearchIndexBuilder();

        // 1. Cover addAllAnnotations
        builder.addAllAnnotations(dummy);

        // 2. Short form add: add(String urlAsWellAsName)
        //    Adds an item whose search "name" and "URL" fragment are the same string
        //    Verifies that the shorthand overload correctly maps the single string to both URL and name
        //    SHOULD return an item with URL "/short" without needing to specify two identical strings manually
        builder.add("short");

        // 3. add(String url, String name)
        //    Adds an item named "name1" at "/url1"
        builder.add("url1", "name1");

        // 4. add(String url, String... names)
        //    Adds multiple names that all map to "/url2"
        builder.add("url2", "name2", "name3");

        // 5. add(SearchItem item)
        //    We use SearchItems.create() to build a SearchItem ("name4") at "/url4", then add it
        builder.add(SearchItems.create("name4", "url4"));

        // 6. add(String url, SearchableModelObject searchable, String name)
        //    We pass our dummy object and a single name ("name5")
        builder.add("url5", dummy, "name5");

        // 7. add(String url, SearchableModelObject searchable, String... names)
        //    Similar to above, but with multiple names
        builder.add("url6", dummy, "name6", "name7");

        // 8. add(SearchIndex index)
        //    Create a small sub index with one entry, then add it to the main builder
        //    This forces the final index to union multiple SearchIndex objects
        SearchIndex subIndex = new SearchIndexBuilder().add("subUrl", "subName").make();
        builder.add(subIndex);

        // Build the final SearchIndex
        // This merges all items, subIndexes and subBuilders into one union index
        SearchIndex index = builder.make();
        assertNotNull(index);

        // Now we test if we can find an item added with add(String url, String name)
        // Note: "name1" should map to "url1"
        SuggestedItem item1 = Search.find(index, "name1");
        assertNotNull(item1);
        assertEquals("/url1", item1.getUrl());

        // Test add(String url, String... names) by looking for "name3"
        SuggestedItem item2 = Search.find(index, "name3");
        assertNotNull(item2);
        assertEquals("/url2", item2.getUrl());

        // Verify the item added through subBuilder: add(SearchIndexBuilder index)
        SuggestedItem item3 = Search.find(index, "subBuilderName");
        assertNotNull(item3);
        assertEquals("/subBuilderUrl", item3.getUrl());

        // Also, confirm that the nested child item from dummy object's SearchIndex is discoverable
        // Note: It should be located at "/dummy/child"
        SuggestedItem item4 = Search.find(index, "child");
        assertNotNull(item4);
        assertEquals("/dummy/child", item4.getUrl());
    }
}
