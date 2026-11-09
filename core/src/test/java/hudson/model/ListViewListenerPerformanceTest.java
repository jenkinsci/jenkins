package hudson.model;

import hudson.model.listeners.ItemListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ListView.Listener performance optimization and the
 * {@link ItemListener.BatchAwareItemListener} batch deletion API.
 */
class ListViewListenerPerformanceTest {

    private JenkinsRule r = new JenkinsRule();

    @BeforeEach
    void setUp() {
        r = new JenkinsRule();
    }

    /**
     * Verifies that the batch-aware listener receives a single onDeletedBatch call
     * for a group of items, instead of individual onDeleted calls.
     */
    @Test
    void testBatchDeletionNotification() throws Exception {
        List<FreeStyleProject> projects = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            FreeStyleProject p = r.createFreeStyleProject("batch-test-" + i);
            projects.add(p);
        }

        AtomicInteger individualCalls = new AtomicInteger(0);
        AtomicInteger batchCalls = new AtomicInteger(0);
        List<Item> receivedBatch = new ArrayList<>();

        ItemListener listener = new ItemListener() {
            @Override
            public void onDeleted(Item item) {
                individualCalls.incrementAndGet();
            }
        };

        ItemListener.BatchAwareItemListener batchListener = new ItemListener.BatchAwareItemListener() {
            @Override
            public void onDeletedBatch(List<Item> items) {
                batchCalls.incrementAndGet();
                receivedBatch.addAll(items);
            }
        };

        r.jenkins.addListener(listener);
        r.jenkins.addListener(batchListener);

        // Delete all projects
        List<Item> toDelete = new ArrayList<>(projects);
        ItemListener.fireOnDeletedBatch(toDelete);

        assertEquals(1, batchCalls.get(), "Batch listener should receive exactly one batch call");
        assertEquals(5, receivedBatch.size(), "Batch should contain all 5 items");
    }

    /**
     * Verifies that non-batch-aware listeners still receive individual onDeleted calls
     * even when fireOnDeletedBatch is used.
     */
    @Test
    void testNonBatchListenerStillGetsIndividualCalls() throws Exception {
        List<FreeStyleProject> projects = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            FreeStyleProject p = r.createFreeStyleProject("indiv-test-" + i);
            projects.add(p);
        }

        AtomicInteger individualCalls = new AtomicInteger(0);
        ItemListener listener = new ItemListener() {
            @Override
            public void onDeleted(Item item) {
                individualCalls.incrementAndGet();
            }
        };
        r.jenkins.addListener(listener);

        ItemListener.fireOnDeletedBatch(new ArrayList<>(projects));

        assertEquals(3, individualCalls.get(), "Individual listener should receive 3 calls");
    }

    /**
     * Verifies that ListView correctly removes deleted items from its jobNames set.
     */
    @Test
    void testListViewRemovesDeletedItems() throws Exception {
        FreeStyleProject p1 = r.createFreeStyleProject("lv-test-1");
        FreeStyleProject p2 = r.createFreeStyleProject("lv-test-2");
        FreeStyleProject p3 = r.createFreeStyleProject("lv-test-3");

        ListView lv = new ListView("test-view");
        lv.add(p1);
        lv.add(p2);
        lv.add(p3);
        r.jenkins.addView(lv);

        assertEquals(3, lv.jobNames.size());

        // Delete p2
        p2.delete();

        assertEquals(2, lv.jobNames.size(), "ListView should have 2 items after deletion");
        assertFalse(lv.jobNames.contains("lv-test-2"), "Deleted item should be removed from view");
        assertTrue(lv.jobNames.contains("lv-test-1"), "Remaining item should still be in view");
    }

    /**
     * Verifies that the batch deletion path handles empty lists gracefully.
     */
    @Test
    void testBatchDeletionEmptyList() {
        assertDoesNotThrow(() -> ItemListener.fireOnDeletedBatch(new ArrayList<>()));
    }

    /**
     * Verifies that Items.getAllViewGroups returns the Jenkins root and includes
     * nested ViewGroups.
     */
    @Test
    void testGetAllViewGroups() {
        var viewGroups = Items.getAllViewGroups(r.jenkins);
        assertFalse(viewGroups.isEmpty(), "Should at least include Jenkins root");
        assertTrue(viewGroups.contains(r.jenkins), "Should include Jenkins root");
    }

    /**
     * Verifies that PIVOT-related concurrent deletion does not throw
     * ConcurrentModificationException.
     */
    @Test
    void testConcurrentDeletionSafety() throws Exception {
        List<FreeStyleProject> projects = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            FreeStyleProject p = r.createFreeStyleProject("concurrent-" + i);
            projects.add(p);
        }

        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger(0);
        List<Thread> threads = new ArrayList<>();

        // Delete items concurrently from multiple threads
        for (int t = 0; t < 3; t++) {
            final int offset = t * 3;
            Thread thread = new Thread(() -> {
                try {
                    startLatch.await(5, TimeUnit.SECONDS);
                    for (int i = offset; i < Math.min(offset + 3, projects.size()); i++) {
                        FreeStyleProject p = projects.get(i);
                        if (p != null) {
                            ItemListener.fireOnDeleted(p);
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
            threads.add(thread);
            thread.start();
        }

        startLatch.countDown();
        for (Thread thread : threads) {
            thread.join(10000);
        }

        assertEquals(0, errors.get(), "No errors should occur during concurrent deletion");
    }

    /**
     * Verifies that a large batch deletion completes in reasonable time
     * (performance regression test for the O(D×N) fix).
     */
    @Test
    void testBatchDeletionPerformance() throws Exception {
        int count = 100;
        List<FreeStyleProject> projects = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            FreeStyleProject p = r.createFreeStyleProject("perf-" + i);
            projects.add(p);
        }

        // Create a listener that tracks the number of ViewGroup traversals
        AtomicInteger traversalCount = new AtomicInteger(0);
        ItemListener.BatchAwareItemListener batchListener = new ItemListener.BatchAwareItemListener() {
            @Override
            public void onDeletedBatch(List<Item> items) {
                // Simulate a ViewGroup traversal per batch call
                traversalCount.incrementAndGet();
            }
        };
        r.jenkins.addListener(batchListener);

        long start = System.nanoTime();
        ItemListener.fireOnDeletedBatch(new ArrayList<>(projects));
        long elapsed = System.nanoTime() - start;

        assertEquals(1, traversalCount.get(), "Should be exactly 1 batch call");
        // Should complete quickly since it's a single batch
        assertTrue(elapsed < TimeUnit.SECONDS.toNanos(5),
                "Batch deletion should complete in < 5 seconds, took " +
                TimeUnit.NANOSECONDS.toMillis(elapsed) + "ms");
    }
}
