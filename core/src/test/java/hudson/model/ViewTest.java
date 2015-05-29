package hudson.model;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import hudson.model.Descriptor.FormException;
import hudson.search.SearchIndex;
import hudson.search.SearchIndexBuilder;
import hudson.search.SearchItem;
import hudson.views.ViewsTabBar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.servlet.ServletException;

import org.junit.Test;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
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
        assertEquals(1, result.size());
        SearchItem actual = result.get(0);
        assertEquals(actual.getSearchName(), item1.getDisplayName());
        assertEquals(actual.getSearchUrl(), item1.getSearchUrl());

        // clear the result array for the next search result to test
        result.clear();
        // make sure we can fetch item 2 from the index
        index.find(displayName2, result);
        assertEquals(1, result.size());
        actual = result.get(0);
        assertEquals(actual.getSearchName(), item2.getDisplayName());
        assertEquals(actual.getSearchUrl(), item2.getSearchUrl());
    }

    /*
     * Get all items recursively when View implements ViewGroup at the same time
     */
    @Test
    public void getAllItems() throws Exception {

        final View leftView = Mockito.mock(View.class);
        final View rightView = Mockito.mock(View.class);
        CompositeView rootView = new CompositeView("rootJob", leftView, rightView);

        Mockito.when(leftView.getAllItems()).thenCallRealMethod();
        Mockito.when(rightView.getAllItems()).thenCallRealMethod();

        final TopLevelItem rootJob = createJob("rootJob");
        final TopLevelItem sharedJob = createJob("sharedJob");
        
        rootView = rootView.withJobs(rootJob, sharedJob);
        
        final TopLevelItem leftJob = createJob("leftJob");
        final TopLevelItem rightJob = createJob("rightJob");

        Mockito.when(leftView.getItems()).thenReturn(Arrays.asList(leftJob, sharedJob));
        Mockito.when(rightView.getItems()).thenReturn(Arrays.asList(rightJob));

        final TopLevelItem[] expected = new TopLevelItem[] {rootJob, sharedJob, leftJob, rightJob};

        assertArrayEquals(expected, rootView.getAllItems().toArray());
    }

    private TopLevelItem createJob(String jobName) {
        final TopLevelItem rootJob = Mockito.mock(TopLevelItem.class);
        Mockito.when(rootJob.getDisplayName()).thenReturn(jobName);
        return rootJob;
    }

    public static class CompositeView extends View implements ViewGroup {

        private View[] views;
        private TopLevelItem[] jobs;

        protected CompositeView(final String name, View... views) {
            super(name);
            this.views = views;
        }
        
        private CompositeView withJobs(TopLevelItem... jobs) {
            this.jobs = jobs;
            return this;
        }
        
        @Override
        public Collection<TopLevelItem> getItems() {
            return Arrays.asList(this.jobs);
        }

        @Override
        public Collection<View> getViews() {
            return Arrays.asList(this.views);
        }
        
        @Override
        public boolean canDelete(View view) {
            return false;
        }

        @Override
        public void deleteView(View view) throws IOException {
        }

        @Override
        public View getView(String name) {
            return null;
        }

        @Override
        public View getPrimaryView() {
            return null;
        }

        @Override
        public void onViewRenamed(View view, String oldName, String newName) {
        }

        @Override
        public ViewsTabBar getViewsTabBar() {
            return null;
        }

        @Override
        public ItemGroup<? extends TopLevelItem> getItemGroup() {
            return null;
        }

        @Override
        public List<Action> getViewActions() {
            return null;
        }

        @Override
        public boolean contains(TopLevelItem item) {
            return false;
        }

        @Override
        protected void submit(StaplerRequest req) throws IOException, ServletException, FormException {
        }

        @Override
        public Item doCreateItem(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            return null;
        }
    }
}
