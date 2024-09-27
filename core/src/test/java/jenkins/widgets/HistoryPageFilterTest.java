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

import hudson.model.Action;
import hudson.model.Build;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.MockItem;
import hudson.model.ModelObject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jenkins.model.queue.QueueItem;
import org.junit.Assert;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.mockito.Mockito;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 *
 * See also HistoryPageFilterInsensitiveSearchTest integration test.
 */
public class HistoryPageFilterTest {

    /**
     * No items.
     */
    @Test
    public void test_latest_empty_page() {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, null, null);
        Iterable<ModelObject> itemList = Collections.emptyList();

        historyPageFilter.add(itemList);
        Assert.assertFalse(historyPageFilter.hasUpPage);
        Assert.assertFalse(historyPageFilter.hasDownPage);
        Assert.assertTrue(historyPageFilter.queueItems.isEmpty());
        Assert.assertTrue(historyPageFilter.runs.isEmpty());
    }

    /**
     * Latest/top page where total number of items less than the max page size.
     */
    @Test
    public void test_latest_partial_page() throws IOException {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, null, null);
        Iterable<ModelObject> runs = newRuns(1, 2);
        var queueItems = newQueueItems(3, 4);

        historyPageFilter.add(runs, queueItems);

        Assert.assertFalse(historyPageFilter.hasUpPage);
        Assert.assertFalse(historyPageFilter.hasDownPage);
        Assert.assertEquals(2, historyPageFilter.queueItems.size());
        Assert.assertEquals(2, historyPageFilter.runs.size());

        Assert.assertEquals(4, historyPageFilter.queueItems.get(0).getEntryId());
        Assert.assertEquals(4, historyPageFilter.newestOnPage);
        Assert.assertEquals(3, historyPageFilter.queueItems.get(1).getEntryId());
        Assert.assertEquals(HistoryPageEntry.getEntryId(2), historyPageFilter.runs.get(0).getEntryId());
        Assert.assertEquals(HistoryPageEntry.getEntryId(1), historyPageFilter.runs.get(1).getEntryId());
        Assert.assertEquals(HistoryPageEntry.getEntryId(1), historyPageFilter.oldestOnPage);
    }

    /**
     * Latest/top page where total number of items greater than the max page size.
     */
    @Test
    public void test_latest_longer_list() throws IOException {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, null, null);
        Iterable<ModelObject> runs = newRuns(1, 10);
        var queueItems = newQueueItems(11, 12);

        historyPageFilter.add(runs, queueItems);

        Assert.assertFalse(historyPageFilter.hasUpPage);
        Assert.assertTrue(historyPageFilter.hasDownPage);
        Assert.assertEquals(2, historyPageFilter.queueItems.size());
        Assert.assertEquals(3, historyPageFilter.runs.size());

        Assert.assertEquals(12, historyPageFilter.queueItems.get(0).getEntryId());
        Assert.assertEquals(12, historyPageFilter.newestOnPage);
        Assert.assertEquals(HistoryPageEntry.getEntryId(10), historyPageFilter.runs.get(0).getEntryId());
    }

    /**
     * Test olderThan (page down) when set to id greater than newest (should never happen). Should be same as not
     * specifying newerThan/olderThan.
     */
    @Test
    public void test_olderThan_gt_newest() throws IOException {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, null, 11L);
        Iterable<ModelObject> itemList = newRuns(1, 10);

        historyPageFilter.add(itemList);

        Assert.assertFalse(historyPageFilter.hasUpPage);
        Assert.assertTrue(historyPageFilter.hasDownPage);
        Assert.assertEquals(5, historyPageFilter.runs.size());

        Assert.assertEquals(HistoryPageEntry.getEntryId(10), historyPageFilter.newestOnPage);
        Assert.assertEquals(HistoryPageEntry.getEntryId(6), historyPageFilter.oldestOnPage);
    }

    /**
     * Test olderThan (page down) when set to id less than the oldest (should never happen). Should just give an
     * empty list of builds.
     */
    @Test
    public void test_olderThan_lt_oldest() throws IOException {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, null, 0L);
        Iterable<ModelObject> itemList = newRuns(1, 10);

        historyPageFilter.add(itemList);

        Assert.assertTrue(historyPageFilter.hasUpPage);
        Assert.assertFalse(historyPageFilter.hasDownPage);
        Assert.assertEquals(0, historyPageFilter.runs.size());
    }

    /**
     * Test olderThan (page down) when set to an id close to the oldest in the list (where
     * there's less than a full page older than the supplied olderThan arg).
     */
    @Test
    public void test_olderThan_leaving_part_page() throws IOException {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, null, 4L);
        Iterable<ModelObject> itemList = newRuns(1, 10);

        historyPageFilter.add(itemList);

        Assert.assertTrue(historyPageFilter.hasUpPage);
        Assert.assertFalse(historyPageFilter.hasDownPage);

        // Should only be 3 runs on the page (oldest 3)
        Assert.assertEquals(3, historyPageFilter.runs.size());

        Assert.assertEquals(HistoryPageEntry.getEntryId(3), historyPageFilter.newestOnPage);
        Assert.assertEquals(HistoryPageEntry.getEntryId(1), historyPageFilter.oldestOnPage);
    }

    /**
     * Test olderThan (page down) when set to an id in the middle. Should be a page up and a page down.
     */
    @Test
    public void test_olderThan_mid_page() throws IOException {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, null, 8L);
        Iterable<ModelObject> itemList = newRuns(1, 10);

        historyPageFilter.add(itemList);

        Assert.assertTrue(historyPageFilter.hasUpPage);
        Assert.assertTrue(historyPageFilter.hasDownPage);
        Assert.assertEquals(5, historyPageFilter.runs.size());

        Assert.assertEquals(HistoryPageEntry.getEntryId(7), historyPageFilter.newestOnPage);
        Assert.assertEquals(HistoryPageEntry.getEntryId(3), historyPageFilter.oldestOnPage);
    }

    /**
     * Test newerThan (page up) when set to id greater than newest (should never happen). Should be an empty list.
     */
    @Test
    public void test_newerThan_gt_newest() throws IOException {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, 11L, null);
        Iterable<ModelObject> itemList = newRuns(1, 10);

        historyPageFilter.add(itemList);

        Assert.assertFalse(historyPageFilter.hasUpPage);
        Assert.assertTrue(historyPageFilter.hasDownPage);
        Assert.assertEquals(0, historyPageFilter.runs.size());
    }

    /**
     * Test newerThan (page up) when set to id less than the oldest (should never happen). Should give the oldest
     * set of builds.
     */
    @Test
    public void test_newerThan_lt_oldest() throws IOException {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, 0L, null);
        Iterable<ModelObject> itemList = newRuns(1, 10);

        historyPageFilter.add(itemList);

        Assert.assertTrue(historyPageFilter.hasUpPage);
        Assert.assertFalse(historyPageFilter.hasDownPage);
        Assert.assertEquals(5, historyPageFilter.runs.size());

        Assert.assertEquals(HistoryPageEntry.getEntryId(5), historyPageFilter.newestOnPage);
        Assert.assertEquals(HistoryPageEntry.getEntryId(1), historyPageFilter.oldestOnPage);
    }

    /**
     * Test newerThan (page up) mid range nearer the oldest build in the list.
     */
    @Test
    public void test_newerThan_near_oldest() throws IOException {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, 3L, null);
        Iterable<ModelObject> itemList = newRuns(1, 10);

        historyPageFilter.add(itemList);

        Assert.assertTrue(historyPageFilter.hasUpPage);
        Assert.assertTrue(historyPageFilter.hasDownPage);
        Assert.assertEquals(5, historyPageFilter.runs.size());

        Assert.assertEquals(HistoryPageEntry.getEntryId(8), historyPageFilter.newestOnPage);
        Assert.assertEquals(HistoryPageEntry.getEntryId(4), historyPageFilter.oldestOnPage);
    }

    /**
     * Test newerThan (page up) mid range nearer the newest build in the list. This works a little different
     * in that it will put the 2 builds newer than newerThan on the page and then fill the remaining slots on the
     * page with builds equal to and older i.e. it return the newest/latest builds.
     */
    @Test
    public void test_newerThan_near_newest() throws IOException {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, 8L, null);
        Iterable<ModelObject> itemList = newRuns(1, 10);

        historyPageFilter.add(itemList);

        Assert.assertFalse(historyPageFilter.hasUpPage);
        Assert.assertTrue(historyPageFilter.hasDownPage);
        Assert.assertEquals(5, historyPageFilter.runs.size());

        Assert.assertEquals(HistoryPageEntry.getEntryId(10), historyPageFilter.newestOnPage);
        Assert.assertEquals(HistoryPageEntry.getEntryId(6), historyPageFilter.oldestOnPage);
    }

    /**
     * Test newerThan (page up) mid range when there are queued builds that are new enough to
     * not show up.
     */
    @Test
    public void test_newerThan_doesntIncludeQueuedItems() throws IOException {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, 5L, null);
        Iterable<ModelObject> runs = newRuns(1, 10);
        var queueItems = newQueueItems(11, 12);

        historyPageFilter.add(runs, queueItems);

        Assert.assertTrue(historyPageFilter.hasUpPage);
        Assert.assertTrue(historyPageFilter.hasDownPage);
        Assert.assertEquals(0, historyPageFilter.queueItems.size());
        Assert.assertEquals(5, historyPageFilter.runs.size());

        Assert.assertEquals(HistoryPageEntry.getEntryId(10), historyPageFilter.runs.get(0).getEntryId());
        Assert.assertEquals(HistoryPageEntry.getEntryId(10), historyPageFilter.newestOnPage);
        Assert.assertEquals(HistoryPageEntry.getEntryId(6), historyPageFilter.oldestOnPage);
    }

    /**
     * Test that later items in the list that are not needed for display are not evaluated at all (for performance).
     */
    @Test
    public void test_laterItemsNotEvaluated() throws IOException {
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, null, null);
        List<ModelObject> itemList = new ArrayList<>();
        for (ModelObject run : newRuns(6, 10)) {
            itemList.add(run);
        }
        for (int queueId = 5; queueId >= 1; queueId--) {
            itemList.add(new ExplodingMockRun(queueId));
        }

        historyPageFilter.add(itemList);

        Assert.assertFalse(historyPageFilter.hasUpPage);
        Assert.assertTrue(historyPageFilter.hasDownPage);
        Assert.assertEquals(5, historyPageFilter.runs.size());

        Assert.assertEquals(HistoryPageEntry.getEntryId(10), historyPageFilter.newestOnPage);
        Assert.assertEquals(HistoryPageEntry.getEntryId(6), historyPageFilter.oldestOnPage);
    }

    @Test
    public void test_search_runs_by_build_number() throws IOException {
        //given
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, null, null);
        Iterable<ModelObject> runs = newRuns(23, 24);
        var queueItems = newQueueItems(25, 26);
        //and
        historyPageFilter.setSearchString("23");

        //when
        historyPageFilter.add(runs, queueItems);

        //then
        Assert.assertEquals(1, historyPageFilter.runs.size());
        Assert.assertEquals(HistoryPageEntry.getEntryId(23), historyPageFilter.runs.get(0).getEntryId());
    }

    @Test
    @Issue("JENKINS-42645")
    public void should_be_case_insensitive_by_default() throws IOException {
        Iterable<ModelObject> runs = Arrays.asList(new MockRun(2, Result.FAILURE), new MockRun(1, Result.SUCCESS));
        assertOneMatchingBuildForGivenSearchStringAndRunItems("failure", runs);
    }

    @Test
    public void should_lower_case_search_string_in_case_insensitive_search() throws IOException {
        Iterable<ModelObject> runs = Arrays.asList(new MockRun(2, Result.FAILURE), new MockRun(1, Result.SUCCESS));
        assertOneMatchingBuildForGivenSearchStringAndRunItems("FAILure", runs);
    }

    @Test
    @Issue("JENKINS-40718")
    public void should_search_builds_by_build_variables() {
        Iterable<ModelObject> runs = Arrays.asList(
                new MockBuild(2).withBuildVariables(Map.of("env", "dummyEnv")),
                new MockBuild(1).withBuildVariables(Map.of("env", "otherEnv")));
        assertOneMatchingBuildForGivenSearchStringAndRunItems("dummyEnv", runs);
    }

    @Test
    @Issue("JENKINS-40718")
    public void should_search_builds_by_build_params() throws IOException {
        Iterable<ModelObject> runs = Arrays.asList(
                new MockBuild(2).withBuildParameters(Map.of("env", "dummyEnv")),
                new MockBuild(1).withBuildParameters(Map.of("env", "otherEnv")));
        assertOneMatchingBuildForGivenSearchStringAndRunItems("dummyEnv", runs);
    }

    @Test
    @Issue("JENKINS-40718")
    public void should_ignore_sensitive_parameters_in_search_builds_by_build_params() throws IOException {
        Iterable<ModelObject> runs = Arrays.asList(
                new MockBuild(2).withBuildParameters(Map.of("plainPassword", "pass1plain")),
                new MockBuild(1).withSensitiveBuildParameters("password", "pass1"));
        assertOneMatchingBuildForGivenSearchStringAndRunItems("pass1", runs);
    }

    private void assertOneMatchingBuildForGivenSearchStringAndRunItems(String searchString, Iterable<ModelObject> runs) {
        //given
        HistoryPageFilter<ModelObject> historyPageFilter = newPage(5, null, null);
        //and
        historyPageFilter.setSearchString(searchString);
        //and
        var queueItems = newQueueItems(3, 4);

        //when
        historyPageFilter.add(runs, queueItems);

        //then
        Assert.assertEquals(1, historyPageFilter.runs.size());
        Assert.assertEquals(HistoryPageEntry.getEntryId(2), historyPageFilter.runs.get(0).getEntryId());
    }

    private List<QueueItem> newQueueItems(long startId, long endId) {
        var items = new ArrayList<QueueItem>();
        for (long queueId = startId; queueId <= endId; queueId++) {
            items.add(new MockItem(queueId));
        }
        return items;
    }

    private Iterable<ModelObject> newRuns(long startId, long endId) throws IOException {
        // Runs should be in reverse order, newest first.
        List<ModelObject> runs = new ArrayList<>();
        for (long queueId = endId; queueId >= startId; queueId--) {
            runs.add(new MockRun(queueId));
        }
        return runs;
    }

    private HistoryPageFilter<ModelObject> newPage(int maxEntries, Long newerThan, Long olderThan) {
        HistoryPageFilter<ModelObject> pageFilter = new HistoryPageFilter<>(maxEntries);
        if (newerThan != null) {
            pageFilter.setNewerThan(HistoryPageEntry.getEntryId(newerThan));
        } else if (olderThan != null) {
            pageFilter.setOlderThan(HistoryPageEntry.getEntryId(olderThan));
        }
        return pageFilter;
    }

    @SuppressWarnings("unchecked")
    private static class MockRun extends Run {
        private final long queueId;

        MockRun(long queueId) throws IOException {
            super(Mockito.mock(Job.class));
            this.queueId = queueId;
        }

        MockRun(long queueId, Result result) throws IOException {
            this(queueId);
            this.result = result;
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

        @Override
        public int getNumber() {
            return (int) queueId;
        }

        @SuppressWarnings("deprecation") // avoid TransientActionFactory
        @Override
        public <T extends Action> T getAction(Class<T> type) {
            for (Action a : getActions()) {
                if (type.isInstance(a)) {
                    return type.cast(a);
                }
            }
            return null;
        }

    }

    // A version of MockRun that will throw an exception if getQueueId or getNumber is called
    private static class ExplodingMockRun extends MockRun {
        ExplodingMockRun(long queueId) throws IOException {
            super(queueId);
        }

        @Override
        public long getQueueId() {
            Assert.fail("Should not get called");
            return super.getQueueId();
        }

        @Override
        public int getNumber() {
            Assert.fail("Should not get called");
            return super.getNumber();
        }
    }

    private static class MockBuild extends Build<FreeStyleProject, FreeStyleBuild> {

        private final int buildNumber;

        private Map<String, String> buildVariables = Collections.emptyMap();
        private Set<String> sensitiveBuildVariables = Collections.emptySet();

        private MockBuild(int buildNumber) {
            super(Mockito.mock(FreeStyleProject.class), Mockito.mock(Calendar.class));
            this.buildNumber = buildNumber;
        }

        @Override
        public int getNumber() {
            return buildNumber;
        }

        @Override
        public Map<String, String> getBuildVariables() {
            return buildVariables;
        }

        @Override
        public Set<String> getSensitiveBuildVariables() {
            return sensitiveBuildVariables; // TODO This is never actually set (bad Mock), actual test in test harness
        }

        MockBuild withBuildVariables(Map<String, String> buildVariables) {
            this.buildVariables = buildVariables;
            return this;
        }

        MockBuild withBuildParameters(Map<String, String> buildParametersAsMap) {
            addAction(new ParametersAction(buildPropertiesMapToParameterValues(buildParametersAsMap), buildParametersAsMap.keySet()));
            return this;
        }

        private List<ParameterValue> buildPropertiesMapToParameterValues(Map<String, String> buildParametersAsMap) {
            return buildParametersAsMap.entrySet().stream()
                    .map(parameter -> new StringParameterValue(parameter.getKey(), parameter.getValue()))
                    .collect(Collectors.toList());
        }

        MockBuild withSensitiveBuildParameters(String paramName, String paramValue) {
            addAction(new ParametersAction(List.of(createSensitiveStringParameterValue(paramName, paramValue)),
                    List.of(paramName)));
            return this;
        }

        @SuppressWarnings("deprecation") // avoid TransientActionFactory
        @Override
        public <T extends Action> T getAction(Class<T> type) {
            for (Action a : getActions()) {
                if (type.isInstance(a)) {
                    return type.cast(a);
                }
            }
            return null;
        }

        private StringParameterValue createSensitiveStringParameterValue(final String paramName, final String paramValue) {
            return new StringParameterValue(paramName, paramValue) {
                @Override
                public boolean isSensitive() {
                    return true;
                }
            };
        }
    }
}
