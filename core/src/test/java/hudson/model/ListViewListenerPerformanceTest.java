package hudson.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Items#getAllViewGroups}, the targeted ViewGroup traversal
 * used by {@code ListView.Listener} instead of the O(N) {@code allItems()}
 * scan on item deletion and rename notifications.
 */
class ListViewListenerPerformanceTest {

    /** Creates a mock that is both an {@link ItemGroup} and a {@link ViewGroup}. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ItemGroup<Item> mockContainer() {
        return (ItemGroup) mock(ItemGroup.class, withSettings().extraInterfaces(ViewGroup.class));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ItemGroup<Item> mockGroup() {
        return (ItemGroup) mock(ItemGroup.class);
    }

    @SuppressWarnings("unchecked")
    private static ViewGroup asViewGroup(ItemGroup<?> group) {
        return (ViewGroup) group;
    }

    /**
     * The traversal must include the root itself when it is a ViewGroup, visit
     * nested containers, and skip leaf items entirely.
     */
    @Test
    void viewGroupsIncludeRootAndNestedContainers() {
        ItemGroup<Item> root = mockContainer();
        ItemGroup<Item> nested = mockContainer();
        Item leaf = mock(Item.class);
        Item leaf2 = mock(Item.class);
        doReturn(Arrays.asList(nested, leaf)).when(root).getItems();
        doReturn(Collections.singletonList(leaf2)).when(nested).getItems();

        List<ViewGroup> result = Items.getAllViewGroups(root);

        assertEquals(2, result.size(), "leaf items must be skipped");
        assertSame(asViewGroup(root), result.get(0));
        assertSame(asViewGroup(nested), result.get(1));
    }

    /** A plain ItemGroup root that is not a ViewGroup is not in the result, but children are. */
    @Test
    void nonViewGroupRootNotIncludedButChildrenTraversed() {
        ItemGroup<Item> root = mockGroup();
        ItemGroup<Item> nested = mockContainer();
        doReturn(Collections.singletonList(nested)).when(root).getItems();

        List<ViewGroup> result = Items.getAllViewGroups(root);

        assertEquals(1, result.size());
        assertSame(asViewGroup(nested), result.get(0));
    }

    /** Deeply nested containers are all collected. */
    @Test
    void deeplyNestedContainersAllCollected() {
        ItemGroup<Item> root = mockContainer();
        ItemGroup<Item> c1 = mockContainer();
        ItemGroup<Item> c2 = mockContainer();
        ItemGroup<Item> c3 = mockContainer();
        doReturn(Collections.singletonList(c1)).when(root).getItems();
        doReturn(Collections.singletonList(c2)).when(c1).getItems();
        doReturn(Collections.singletonList(c3)).when(c2).getItems();

        List<ViewGroup> result = Items.getAllViewGroups(root);

        assertEquals(4, result.size());
        assertSame(asViewGroup(root), result.get(0));
        assertSame(asViewGroup(c1), result.get(1));
        assertSame(asViewGroup(c2), result.get(2));
        assertSame(asViewGroup(c3), result.get(3));
    }

    /** An empty root yields just the root itself. */
    @Test
    void emptyRootYieldsOnlyItself() {
        ItemGroup<Item> root = mockContainer();
        doReturn(Collections.emptyList()).when(root).getItems();

        List<ViewGroup> result = Items.getAllViewGroups(root);

        assertEquals(1, result.size());
        assertSame(asViewGroup(root), result.get(0));
    }

    /**
     * A container whose children cannot be enumerated is skipped without
     * breaking the traversal of its siblings.
     */
    @Test
    void containerWithFailingGetItemsIsSkipped() {
        ItemGroup<Item> root = mockContainer();
        ItemGroup<Item> broken = mockGroup();
        ItemGroup<Item> healthy = mockContainer();
        doThrow(new RuntimeException("cannot enumerate")).when(broken).getItems();
        doReturn(Arrays.asList(broken, healthy)).when(root).getItems();

        List<ViewGroup> result = assertDoesNotThrow(() -> Items.getAllViewGroups(root));

        assertEquals(2, result.size());
        assertSame(asViewGroup(root), result.get(0));
        assertSame(asViewGroup(healthy), result.get(1));
    }

    /**
     * Performance characteristic of the fix: the traversal is linear in the
     * number of containers and independent of the number of leaf jobs.
     * A wide flat structure of containers plus many leaves must be traversed
     * quickly.
     */
    @Test
    void traversalIsLinearOverWideTree() {
        ItemGroup<Item> root = mockContainer();
        List<Object> children = new ArrayList<>();
        int containers = 500;
        int leaves = 5000;
        for (int i = 0; i < containers; i++) {
            ItemGroup<Item> c = mockContainer();
            doReturn(Collections.emptyList()).when(c).getItems();
            children.add(c);
        }
        for (int i = 0; i < leaves; i++) {
            children.add(mock(Item.class));
        }
        doReturn(children).when(root).getItems();

        long start = System.nanoTime();
        List<ViewGroup> result = Items.getAllViewGroups(root);
        long elapsedNanos = System.nanoTime() - start;

        assertEquals(containers + 1, result.size(), "all containers plus root");
        assertTrue(elapsedNanos < TimeUnit.SECONDS.toNanos(5),
                "traversal should complete in < 5s, took "
                + TimeUnit.NANOSECONDS.toMillis(elapsedNanos) + "ms");
    }

    /**
     * Concurrent callers of the traversal (as happens when many item deletion
     * notifications fire in parallel) must not interfere with each other.
     */
    @Test
    void concurrentTraversalSafety() throws Exception {
        ItemGroup<Item> root = mockContainer();
        List<Object> children = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            ItemGroup<Item> c = mockContainer();
            doReturn(Collections.emptyList()).when(c).getItems();
            children.add(c);
        }
        doReturn(children).when(root).getItems();

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger(0);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 50; i++) {
                        List<ViewGroup> result = Items.getAllViewGroups(root);
                        if (result.size() != 101) {
                            failures.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            }));
        }
        start.countDown();
        for (java.util.concurrent.Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(0, failures.get(), "no concurrent traversal failures expected");
    }
}
