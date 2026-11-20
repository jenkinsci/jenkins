package hudson.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

class KeyedDataStorageTest {

    @Issue("27695")
    @Test
    void interruptedWaiterDoesNotStartConcurrentLoad() throws Exception {
        CountDownLatch threadAInLoad = new CountDownLatch(1);
        CountDownLatch threadAHold = new CountDownLatch(1);
        AtomicInteger activeLoads = new AtomicInteger(0);
        AtomicInteger maxConcurrentLoads = new AtomicInteger(0);

        KeyedDataStorage<String, String> storage = new KeyedDataStorage<>() {
            @Override
            protected String load(String key) throws IOException {
                int cur = activeLoads.incrementAndGet();
                maxConcurrentLoads.updateAndGet(m -> Math.max(m, cur));
                if ("Thread-A".equals(Thread.currentThread().getName())) {
                    threadAInLoad.countDown();
                    try {
                        threadAHold.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                activeLoads.decrementAndGet();
                return "val-" + key;
            }

            @Override
            protected String create(String key, String p) {
                return "created";
            }
        };

        Thread ta = new Thread(() -> {
            try {
                storage.getOrCreate("k", "p1");
            } catch (IOException ignored) {
            }
        }, "Thread-A");
        ta.start();

        assertTrue(threadAInLoad.await(3, TimeUnit.SECONDS), "Thread A should be inside load()");

        AtomicBoolean threadBCaughtInterrupt = new AtomicBoolean(false);
        Thread tb = new Thread(() -> {
            try {
                storage.getOrCreate("k", "p2");
            } catch (InterruptedIOException e) {
                threadBCaughtInterrupt.set(true);
            } catch (IOException ignored) {
            }
        }, "Thread-B");
        tb.start();

        while (tb.getState() != Thread.State.WAITING) {
            Thread.onSpinWait();
        }
        tb.interrupt();

        tb.join(2000);
        threadAHold.countDown();
        ta.join(2000);

        assertTrue(threadBCaughtInterrupt.get(), "Thread B must receive InterruptedIOException");
        assertEquals(1, maxConcurrentLoads.get(), "Concurrent loads for the same key must never exceed 1");
    }

    @Issue("27695")
    @Test
    void failedLoadDoesNotPoisonCacheForSubsequentReads() throws Exception {
        AtomicBoolean fail = new AtomicBoolean(true);
        AtomicInteger callCount = new AtomicInteger(0);

        KeyedDataStorage<String, String> storage = new KeyedDataStorage<>() {
            @Override
            protected String load(String key) throws IOException {
                int c = callCount.incrementAndGet();
                if (fail.get()) {
                    throw new IOException("Simulated storage failure");
                }
                return "good-value";
            }

            @Override
            protected String create(String key, String p) {
                return "created";
            }
        };

        assertThrows(IOException.class, () -> storage.get("keyX"));

        // Storage recovers
        fail.set(false);

        String result = storage.get("keyX");
        assertEquals("good-value", result, "Subsequent get() must retry loading after storage recovery");
        assertEquals(2, callCount.get(), "load() should have been invoked exactly twice");
    }

    @Issue("27695")
    @Test
    void runtimeExceptionDuringLoadDoesNotPoisonCache() throws Exception {
        AtomicBoolean fail = new AtomicBoolean(true);
        AtomicInteger callCount = new AtomicInteger(0);

        KeyedDataStorage<String, String> storage = new KeyedDataStorage<>() {
            @Override
            protected String load(String key) {
                callCount.incrementAndGet();
                if (fail.get()) {
                    throw new IllegalStateException("Simulated unchecked runtime error");
                }
                return "good-value";
            }

            @Override
            protected String create(String key, String p) {
                return "created";
            }
        };

        assertThrows(IllegalStateException.class, () -> storage.get("keyY"));

        fail.set(false);

        String result = storage.get("keyY");
        assertEquals("good-value", result, "Subsequent get() must retry loading after runtime exception clears");
        assertEquals(2, callCount.get(), "load() should have been invoked twice");
    }

    @Test
    void concurrentGetOrCreateReturnsIdenticalInstance() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        int threads = 8;
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        ConcurrentLinkedQueue<Object> results = new ConcurrentLinkedQueue<>();

        KeyedDataStorage<Object, String> storage = new KeyedDataStorage<>() {
            @Override
            protected Object load(String key) {
                return null;
            }

            @Override
            protected Object create(String key, String p) {
                return new Object();
            }
        };

        try {
            for (int i = 0; i < threads; i++) {
                exec.submit(() -> {
                    try {
                        startLatch.await();
                        results.add(storage.getOrCreate("common-key", "p"));
                    } catch (Exception ignored) {
                    }
                });
            }

            startLatch.countDown();
            exec.shutdown();
            assertTrue(exec.awaitTermination(5, TimeUnit.SECONDS));

            assertEquals(threads, results.size());
            Object first = results.peek();
            for (Object obj : results) {
                assertSame(first, obj, "All concurrent getOrCreate callers must receive the exact same singleton instance");
            }
        } finally {
            exec.shutdownNow();
        }
    }
}
