/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jenkins.widgets;

import hudson.model.Job;
import hudson.model.MockItem;
import hudson.model.ModelObject;
import hudson.model.Result;
import hudson.model.Run;
import jenkins.widgets.HistoryPageEntry;
import jenkins.widgets.HistoryPageFilter;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class HistoryPageFilterTest {

    /**
     * No items.
     */
    @Test
    public void test_latest_empty_page() {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, null, null);
        List<ModelObject> itemList = new ArrayList<ModelObject>();

        historyPageFilter.add(itemList);
        Assert.assertEquals(false, historyPageFilter.hasUpPage);
        Assert.assertEquals(false, historyPageFilter.hasDownPage);
        Assert.assertEquals(true, historyPageFilter.queueItems.isEmpty());
        Assert.assertEquals(true, historyPageFilter.runs.isEmpty());
    }

    /**
     * Latest/top page where total number of items < the max page size.
     */
    @Test
    public void test_latest_partial_page() throws IOException {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, null, null);
        List<ModelObject> itemList = new ArrayList<ModelObject>();

        itemList.addAll(newRuns(1, 2));
        itemList.addAll(newQueueItems(3, 4));

        // want to make sure the list items are ordered by id in descending order
        Assert.assertEquals(1, HistoryPageEntry.getEntryId(itemList.get(0)));
        historyPageFilter.add(itemList);
        Assert.assertEquals(4, HistoryPageEntry.getEntryId(itemList.get(0)));

        Assert.assertEquals(false, historyPageFilter.hasUpPage);
        Assert.assertEquals(false, historyPageFilter.hasDownPage);
        Assert.assertEquals(2, historyPageFilter.queueItems.size());
        Assert.assertEquals(2, historyPageFilter.runs.size());

        Assert.assertEquals(4, historyPageFilter.queueItems.get(0).getEntryId());
        Assert.assertEquals(4, historyPageFilter.newestOnPage);
        Assert.assertEquals(3, historyPageFilter.queueItems.get(1).getEntryId());
        Assert.assertEquals(2, historyPageFilter.runs.get(0).getEntryId());
        Assert.assertEquals(1, historyPageFilter.runs.get(1).getEntryId());
        Assert.assertEquals(1, historyPageFilter.oldestOnPage);
    }

    /**
     * Latest/top page where total number of items > the max page size.
     */
    @Test
    public void test_latest_longer_list() throws IOException {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, null, null);
        List<ModelObject> itemList = new ArrayList<ModelObject>();

        itemList.addAll(newRuns(1, 10));
        itemList.addAll(newQueueItems(11, 12));

        historyPageFilter.add(itemList);

        Assert.assertEquals(false, historyPageFilter.hasUpPage);
        Assert.assertEquals(true, historyPageFilter.hasDownPage);
        Assert.assertEquals(2, historyPageFilter.queueItems.size());
        Assert.assertEquals(3, historyPageFilter.runs.size());

        Assert.assertEquals(12, historyPageFilter.queueItems.get(0).getEntryId());
        Assert.assertEquals(12, historyPageFilter.newestOnPage);
        Assert.assertEquals(10, historyPageFilter.runs.get(0).getEntryId());
    }

    /**
     * Test olderThan (page down) when set to id greater than newest (should never happen). Should be same as not
     * specifying newerThan/olderThan.
     */
    @Test
    public void test_olderThan_gt_newest() throws IOException {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, null, 11L);
        List<ModelObject> itemList = new ArrayList<ModelObject>();

        itemList.addAll(newRuns(1, 10));
        historyPageFilter.add(itemList);

        Assert.assertEquals(false, historyPageFilter.hasUpPage);
        Assert.assertEquals(true, historyPageFilter.hasDownPage);
        Assert.assertEquals(5, historyPageFilter.runs.size());

        Assert.assertEquals(10, historyPageFilter.newestOnPage);
        Assert.assertEquals(6, historyPageFilter.oldestOnPage);
    }

    /**
     * Test olderThan (page down) when set to id less than the oldest (should never happen). Should just give an
     * empty list of builds.
     */
    @Test
    public void test_olderThan_lt_oldest() throws IOException {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, null, 0L);
        List<ModelObject> itemList = new ArrayList<ModelObject>();

        itemList.addAll(newRuns(1, 10));
        historyPageFilter.add(itemList);

        Assert.assertEquals(true, historyPageFilter.hasUpPage);
        Assert.assertEquals(false, historyPageFilter.hasDownPage);
        Assert.assertEquals(0, historyPageFilter.runs.size());
    }

    /**
     * Test olderThan (page down) when set to an id close to the oldest in the list (where
     * there's less than a full page older than the supplied olderThan arg).
     */
    @Test
    public void test_olderThan_leaving_part_page() throws IOException {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, null, 4L);
        List<ModelObject> itemList = new ArrayList<ModelObject>();

        itemList.addAll(newRuns(1, 10));
        historyPageFilter.add(itemList);

        Assert.assertEquals(true, historyPageFilter.hasUpPage);
        Assert.assertEquals(false, historyPageFilter.hasDownPage);

        // Should only be 3 runs on the page (oldest 3)
        Assert.assertEquals(3, historyPageFilter.runs.size());

        Assert.assertEquals(3, historyPageFilter.newestOnPage);
        Assert.assertEquals(1, historyPageFilter.oldestOnPage);
    }

    /**
     * Test olderThan (page down) when set to an id in the middle. Should be a page up and a page down.
     */
    @Test
    public void test_olderThan_mid_page() throws IOException {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, null, 8L);
        List<ModelObject> itemList = new ArrayList<ModelObject>();

        itemList.addAll(newRuns(1, 10));
        historyPageFilter.add(itemList);

        Assert.assertEquals(true, historyPageFilter.hasUpPage);
        Assert.assertEquals(true, historyPageFilter.hasDownPage);
        Assert.assertEquals(5, historyPageFilter.runs.size());

        Assert.assertEquals(7, historyPageFilter.newestOnPage);
        Assert.assertEquals(3, historyPageFilter.oldestOnPage);
    }

    /**
     * Test newerThan (page up) when set to id greater than newest (should never happen). Should be an empty list.
     */
    @Test
    public void test_newerThan_gt_newest() throws IOException {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, 11L, null);
        List<ModelObject> itemList = new ArrayList<ModelObject>();

        itemList.addAll(newRuns(1, 10));
        historyPageFilter.add(itemList);

        Assert.assertEquals(false, historyPageFilter.hasUpPage);
        Assert.assertEquals(true, historyPageFilter.hasDownPage);
        Assert.assertEquals(0, historyPageFilter.runs.size());
    }

    /**
     * Test newerThan (page up) when set to id less than the oldest (should never happen). Should give the oldest
     * set of builds.
     */
    @Test
    public void test_newerThan_lt_oldest() throws IOException {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, 0L, null);
        List<ModelObject> itemList = new ArrayList<ModelObject>();

        itemList.addAll(newRuns(1, 10));
        historyPageFilter.add(itemList);

        Assert.assertEquals(true, historyPageFilter.hasUpPage);
        Assert.assertEquals(false, historyPageFilter.hasDownPage);
        Assert.assertEquals(5, historyPageFilter.runs.size());

        Assert.assertEquals(5, historyPageFilter.newestOnPage);
        Assert.assertEquals(1, historyPageFilter.oldestOnPage);
    }

    /**
     * Test newerThan (page up) mid range nearer the oldest build in the list.
     */
    @Test
    public void test_newerThan_near_oldest() throws IOException {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, 3L, null);
        List<ModelObject> itemList = new ArrayList<ModelObject>();

        itemList.addAll(newRuns(1, 10));
        historyPageFilter.add(itemList);

        Assert.assertEquals(true, historyPageFilter.hasUpPage);
        Assert.assertEquals(true, historyPageFilter.hasDownPage);
        Assert.assertEquals(5, historyPageFilter.runs.size());

        Assert.assertEquals(8, historyPageFilter.newestOnPage);
        Assert.assertEquals(4, historyPageFilter.oldestOnPage);
    }

    /**
     * Test newerThan (page up) mid range nearer the newest build in the list. This works a little different
     * in that it will put the 2 builds newer than newerThan on the page and then fill the remaining slots on the
     * page with builds equal to and older i.e. it return the newest/latest builds.
     */
    @Test
    public void test_newerThan_near_newest() throws IOException {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, 8L, null);
        List<ModelObject> itemList = new ArrayList<ModelObject>();

        itemList.addAll(newRuns(1, 10));
        historyPageFilter.add(itemList);

        Assert.assertEquals(false, historyPageFilter.hasUpPage);
        Assert.assertEquals(true, historyPageFilter.hasDownPage);
        Assert.assertEquals(5, historyPageFilter.runs.size());

        Assert.assertEquals(10, historyPageFilter.newestOnPage);
        Assert.assertEquals(6, historyPageFilter.oldestOnPage);
    }

    private List<ModelObject> newQueueItems(long startId, long endId) {
        List<ModelObject> items = new ArrayList<ModelObject>();
        for (long queueId = startId; queueId <= endId; queueId++) {
            items.add(new MockItem(queueId));
        }
        return items;
    }

    private List<ModelObject> newRuns(long startId, long endId) throws IOException {
        List<ModelObject> runs = new ArrayList<ModelObject>();
        for (long queueId = startId; queueId <= endId; queueId++) {
            runs.add(new MockRun(queueId));
        }
        return runs;
    }

    private HistoryPageFilter<ModelObject> newPage(int maxEntries, Long newerThan, Long olderThan) {
        HistoryPageFilter<ModelObject> pageFilter = new HistoryPageFilter<ModelObject>(maxEntries);
        if (newerThan != null) {
            pageFilter.setNewerThan(newerThan);
        } else if (olderThan != null) {
            pageFilter.setOlderThan(olderThan);
        }
        return pageFilter;
    }

    @SuppressWarnings("unchecked")
    private static class MockRun extends Run {
        private final long queueId;

        public MockRun(long queueId) throws IOException {
            super(Mockito.mock(Job.class));
            this.queueId = queueId;
        }

        @Override
        public int compareTo(Run o) {
            return 0;
        }

        @Override
        public Result getResult() {
            return result;
        }

        @Override
        public boolean isBuilding() {
            return false;
        }

        @Override
        public long getQueueId() {
            return queueId;
        }
    }
}
