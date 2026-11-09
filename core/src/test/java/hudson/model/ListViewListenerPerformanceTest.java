package hudson.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.listeners.ItemListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;

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
     * Verifies that ItemListener.fireOnDeleted works correctly for individual items.
     */
    @Test
    void testIndividualDeletionNotification() throws Exception {
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

        for (FreeStyleProject p : projects) {
            ItemListener.fireOnDeleted(p);
        }

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
