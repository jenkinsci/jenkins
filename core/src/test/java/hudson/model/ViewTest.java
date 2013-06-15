package hudson.model;

import hudson.search.SearchIndex;
import hudson.search.SearchIndexBuilder;
import hudson.search.SearchItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class ViewTest {

    /*
     * This test verifies that urls and displaynames in the TopLevelItem list
     * are added to the SearchIndexBuilder
     */
    @Test
    public void testAddDisplayNamesToSearchIndex() {
        final String url1 = "url1";
        final String displayName1 = "displayName1";
        final String url2 = "url2";
        final String displayName2 = "displayName2";
        
        SearchIndexBuilder sib = new SearchIndexBuilder();
        // mock the items to be indexed
        TopLevelItem item1 = Mockito.mock(TopLevelItem.class);
        Mockito.when(item1.getSearchUrl()).thenReturn(url1);
        Mockito.when(item1.getDisplayName()).thenReturn(displayName1);
        TopLevelItem item2 = Mockito.mock(TopLevelItem.class);
        Mockito.when(item2.getSearchUrl()).thenReturn(url2);
        Mockito.when(item2.getDisplayName()).thenReturn(displayName2);
        Collection<TopLevelItem> items = new ArrayList<TopLevelItem>();
        items.add(item1);
        items.add(item2);
        
        // mock the view class except for the addDisplayNamesToSearchIndex() call as that
        // is what we are testing
        View view = Mockito.mock(View.class);
        Mockito.doCallRealMethod().when(view).addDisplayNamesToSearchIndex(sib, items);

        // now make the actual call to index items
        view.addDisplayNamesToSearchIndex(sib, items);

        // make and index with sib 
        SearchIndex index = sib.make();
        
        // now make sure we can fetch item1 from the index
        List<SearchItem> result = new ArrayList<SearchItem>();
        index.find(displayName1, result);
        Assert.assertEquals(1, result.size());
        SearchItem actual = result.get(0);
        Assert.assertEquals(actual.getSearchName(), item1.getDisplayName());
        Assert.assertEquals(actual.getSearchUrl(), item1.getSearchUrl());

        // clear the result array for the next search result to test
        result.clear();
        // make sure we can fetch item 2 from the index
        index.find(displayName2, result);
        Assert.assertEquals(1, result.size());
        actual = result.get(0);
        Assert.assertEquals(actual.getSearchName(), item2.getDisplayName());
        Assert.assertEquals(actual.getSearchUrl(), item2.getSearchUrl());
    }

    /*
     * Get all items recursively when View implements ViewGroup at the same time
     */
    @Test
    public void getAllItems() throws Exception {

        final CompositeView rootView = Mockito.mock(CompositeView.class);
        final View leftView = Mockito.mock(View.class);
        final View rightView = Mockito.mock(View.class);

        Mockito.when(rootView.getAllItems()).thenCallRealMethod();
        Mockito.when(leftView.getAllItems()).thenCallRealMethod();
        Mockito.when(rightView.getAllItems()).thenCallRealMethod();

        final TopLevelItem rootJob = Mockito.mock(TopLevelItem.class);
        final TopLevelItem leftJob = Mockito.mock(TopLevelItem.class);
        final TopLevelItem rightJob = Mockito.mock(TopLevelItem.class);
        final TopLevelItem sharedJob = Mockito.mock(TopLevelItem.class);

        Mockito.when(rootJob.getDisplayName()).thenReturn("rootJob");
        Mockito.when(leftJob.getDisplayName()).thenReturn("leftJob");
        Mockito.when(rightJob.getDisplayName()).thenReturn("rightJob");
        Mockito.when(sharedJob.getDisplayName()).thenReturn("sharedJob");

        Mockito.when(rootView.getViews()).thenReturn(Arrays.asList(leftView, rightView));
        Mockito.when(rootView.getItems()).thenReturn(Arrays.asList(rootJob, sharedJob));
        Mockito.when(leftView.getItems()).thenReturn(Arrays.asList(leftJob, sharedJob));
        Mockito.when(rightView.getItems()).thenReturn(Arrays.asList(rightJob));

        final TopLevelItem[] expected = new TopLevelItem[] {rootJob, sharedJob, leftJob, rightJob};

        Assert.assertArrayEquals(expected, rootView.getAllItems().toArray());
    }

    public static abstract class CompositeView extends View implements ViewGroup {

        protected CompositeView(final String name) {
            super(name);
        }
    }
}
