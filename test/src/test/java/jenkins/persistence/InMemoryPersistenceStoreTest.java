/*
 * The MIT License
 *
 * Copyright (c) 2026, Jenkins project contributors
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

package jenkins.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * The key-value contract of {@link PersistenceStore}, as the in-memory store used by the tests implements it.
 */
class InMemoryPersistenceStoreTest {

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void writesAreConditionalOnTheVersion() throws Exception {
        InMemoryPersistenceStore store = new InMemoryPersistenceStore();
        long v1 = store.write("k", b("1"), PersistenceStore.ABSENT);
        assertThrows(ConflictException.class, () -> store.write("k", b("x"), PersistenceStore.ABSENT));
        long v2 = store.write("k", b("2"), v1);
        assertTrue(v2 > v1);
        ConflictException e = assertThrows(ConflictException.class, () -> store.write("k", b("3"), v1));
        assertEquals(v1, e.getExpectedVersion());
        assertEquals(v2, e.getActualVersion());
        assertEquals(v2, store.write("k", b("2"), v2), "writing the same content makes no new version");
        long v3 = store.write("k", b("3"), PersistenceStore.ANY);
        assertArrayEquals(b("3"), store.read("k").content());
        assertEquals(v3, store.read("k").version());
    }

    @Test
    void versionsIncreaseAcrossDeletionAndRecreation() throws Exception {
        InMemoryPersistenceStore store = new InMemoryPersistenceStore();
        long v1 = store.write("k", b("1"), PersistenceStore.ABSENT);
        assertThrows(ConflictException.class, () -> store.delete("k", v1 + 100));
        store.delete("k", v1);
        assertNull(store.read("k"));
        long v2 = store.write("k", b("1"), PersistenceStore.ABSENT);
        assertTrue(v2 > v1);
    }

    @Test
    void versionsOfDifferentKeysComeFromOneSequence() throws Exception {
        InMemoryPersistenceStore store = new InMemoryPersistenceStore();
        long a = store.write("jobs/a/config.xml", b("a"), PersistenceStore.ABSENT);
        long other = store.write("jobs/other/config.xml", b("o"), PersistenceStore.ABSENT);
        assertTrue(other > a, "a change made after another has a higher version, whatever its key");
        List<PersistenceStore.Moved> moved = store.move("jobs/a", "jobs/b", List.of());
        long made = store.write("jobs/a/config.xml", b("again"), PersistenceStore.ABSENT);
        assertTrue(made > moved.get(0).version(), "what is made at a key moved away from is newer than the move");
        assertTrue(moved.get(0).version() > other);
    }

    @Test
    void deletingAnAbsentKeySucceedsWhateverTheVersion() throws Exception {
        InMemoryPersistenceStore store = new InMemoryPersistenceStore();
        store.delete("never", 42);
        store.delete("never", PersistenceStore.ABSENT);
        store.delete("never", PersistenceStore.ANY);
    }

    @Test
    void childrenAreTheNamesDirectlyBelow() throws Exception {
        InMemoryPersistenceStore store = new InMemoryPersistenceStore();
        store.write("jobs/a/config.xml", b("a"), PersistenceStore.ANY);
        store.write("jobs/f/jobs/b/config.xml", b("b"), PersistenceStore.ANY);
        store.write("jobsx/c/config.xml", b("c"), PersistenceStore.ANY);
        assertEquals(Set.of("a", "f"), store.children("jobs"));
        assertEquals(Set.of("config.xml"), store.children("jobs/a"));
        assertEquals(Set.of(), store.children("jobs/a/config.xml"));
        assertEquals(3, store.deleteAll("jobs") + store.deleteAll("jobsx"));
        assertEquals(Set.of(), store.children("jobs"));
    }

    @Test
    void moveMovesAPrefixAtOnceShallowerKeysFirst() throws Exception {
        InMemoryPersistenceStore store = new InMemoryPersistenceStore();
        long a = store.write("jobs/a/config.xml", b("a"), PersistenceStore.ANY);
        store.write("jobs/a/jobs/x/config.xml", b("x"), PersistenceStore.ANY);
        store.write("jobs/ab/config.xml", b("ab"), PersistenceStore.ANY);
        store.write("jobs/f/config.xml", b("f"), PersistenceStore.ANY);
        List<PersistenceStore.ChangeEvent> events = new ArrayList<>();
        store.subscribe(events::add);
        List<PersistenceStore.Moved> moved = store.move("jobs/a", "jobs/f/jobs/a", List.of(
                new PersistenceStore.Precondition("jobs/a/config.xml", a),
                new PersistenceStore.Precondition("jobs/f/config.xml", PersistenceStore.PRESENT),
                new PersistenceStore.Precondition("jobs/f/jobs/a/config.xml", PersistenceStore.ABSENT)));
        assertEquals(List.of("jobs/a/config.xml", "jobs/a/jobs/x/config.xml"), moved.stream().map(PersistenceStore.Moved::from).toList());
        assertEquals(a, moved.get(0).fromVersion());
        assertEquals(List.of("jobs/f/jobs/a/config.xml", "jobs/f/jobs/a/jobs/x/config.xml"), events.stream().map(PersistenceStore.ChangeEvent::key).toList());
        assertEquals(PersistenceStore.ChangeEvent.Kind.MOVED, events.get(0).kind());
        assertEquals("jobs/a/config.xml", events.get(0).oldKey());
        assertNull(store.read("jobs/a/config.xml"));
        assertArrayEquals(b("ab"), store.read("jobs/ab/config.xml").content(), "a sibling sharing the prefix's text is not moved");
        assertEquals(moved.get(0).version(), store.read("jobs/f/jobs/a/config.xml").version());
    }

    @Test
    void moveHappensOnlyIfEveryPreconditionHolds() throws Exception {
        InMemoryPersistenceStore store = new InMemoryPersistenceStore();
        long a = store.write("jobs/a/config.xml", b("a"), PersistenceStore.ANY);
        store.write("jobs/b/config.xml", b("b"), PersistenceStore.ANY);
        // changed since it was read
        assertThrows(ConflictException.class, () -> store.move("jobs/a", "jobs/c",
                List.of(new PersistenceStore.Precondition("jobs/a/config.xml", a + 100))));
        // the destination's parent is gone (moved or deleted by another move)
        assertThrows(ConflictException.class, () -> store.move("jobs/a", "jobs/g/jobs/a",
                List.of(new PersistenceStore.Precondition("jobs/g/config.xml", PersistenceStore.PRESENT))));
        // something is at the destination already
        assertThrows(ConflictException.class, () -> store.move("jobs/a", "jobs/b", List.of()));
        assertArrayEquals(b("a"), store.read("jobs/a/config.xml").content());
        assertEquals(a, store.read("jobs/a/config.xml").version(), "nothing moved");
    }

    @Test
    void changesAreDeliveredInTheOrderMadeWithTheirOrigin() throws Exception {
        InMemoryPersistenceStore store = new InMemoryPersistenceStore();
        PersistenceStore other = store.as("other");
        assertNotEquals(store.getOrigin(), other.getOrigin());
        List<PersistenceStore.ChangeEvent> delivered = new ArrayList<>();
        store.subscribe(e -> {
            synchronized (delivered) {
                delivered.add(e);
            }
        });
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable writer = () -> {
            try {
                PersistenceStore s = Thread.currentThread().getName().equals("a") ? store : other;
                for (int i = 0; i < 500; i++) {
                    s.write("k", b(Thread.currentThread().getName() + i), PersistenceStore.ANY);
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        };
        Thread ta = new Thread(writer, "a");
        Thread tb = new Thread(writer, "b");
        ta.start();
        tb.start();
        ta.join();
        tb.join();
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        assertEquals(1000, delivered.size());
        for (int i = 1; i < delivered.size(); i++) {
            assertTrue(delivered.get(i).version() > delivered.get(i - 1).version(), "delivered out of order at " + i);
        }
        assertEquals(500, delivered.stream().filter(e -> e.origin().equals("other")).count());
    }

    @Test
    void aChangeMadeByAListenerIsDeliveredAfterTheOneItIsHandling() throws Exception {
        InMemoryPersistenceStore store = new InMemoryPersistenceStore();
        store.subscribe(e -> {
            if (e.key().equals("k1")) {
                try {
                    store.write("k2", new byte[] {2}, PersistenceStore.ANY);
                } catch (Exception x) {
                    throw new AssertionError(x);
                }
            }
        });
        List<String> delivered = new ArrayList<>();
        store.subscribe(e -> delivered.add(e.key()));
        store.write("k1", new byte[] {1}, PersistenceStore.ANY);
        assertEquals(List.of("k1", "k2"), delivered);
    }

    @Test
    void aListenerFailingDoesNotStopTheOthersNorTheChange() throws Exception {
        InMemoryPersistenceStore store = new InMemoryPersistenceStore();
        store.subscribe(e -> {
            throw new IllegalStateException("broken listener");
        });
        List<String> delivered = new ArrayList<>();
        store.subscribe(e -> delivered.add(e.key()));
        store.write("k1", new byte[] {1}, PersistenceStore.ANY);
        store.write("k2", new byte[] {2}, PersistenceStore.ANY);
        assertEquals(List.of("k1", "k2"), delivered);
    }
}
