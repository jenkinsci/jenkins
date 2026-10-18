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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link PersistenceStore} held in memory, for tests and as a reference for implementations of the contract.
 * {@link #as} gives views with other origins, standing in for other controllers sharing the store.
 * Events are delivered synchronously, after the change, on the thread making it, in commit order.
 */
public class InMemoryPersistenceStore implements PersistenceStore {

    private static final Logger LOGGER = Logger.getLogger(InMemoryPersistenceStore.class.getName());

    private final InMemoryPersistenceStore root;
    private final String origin;
    private final java.util.NavigableMap<String, Entry> entries;
    private final List<Consumer<ChangeEvent>> listeners;
    /** Changes made but not yet delivered, in the order they were made: added to under the data lock. */
    private final Queue<ChangeEvent> outbox;
    /** Held while delivering, so that changes are delivered one at a time, in order, but not under the data lock. */
    private final Object delivering;
    /** The thread delivering changes, guarded by {@link #delivering}. */
    private Thread draining;
    /** Versions come from one counter, so a key that is deleted and recreated never repeats a version. */
    private long clock;
    /** Whether operations fail as with a store that cannot be reached. */
    private volatile boolean unreachable;
    private volatile boolean confidentialKeys;

    public InMemoryPersistenceStore() {
        this.root = this;
        this.origin = UUID.randomUUID().toString();
        this.entries = new TreeMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.outbox = new ArrayDeque<>();
        this.delivering = new Object();
    }

    private InMemoryPersistenceStore(InMemoryPersistenceStore root, String origin) {
        this.root = root;
        this.origin = origin;
        this.entries = root.entries;
        this.listeners = root.listeners;
        this.outbox = root.outbox;
        this.delivering = root.delivering;
    }

    /**
     * A view of the same entries whose changes carry a different origin, as if made by another controller.
     */
    public InMemoryPersistenceStore as(@NonNull String origin) {
        return new InMemoryPersistenceStore(root, origin);
    }

    /** Makes every operation of this store and its views fail, as when it cannot be reached; or work again. */
    public void setUnreachable(boolean unreachable) {
        root.unreachable = unreachable;
    }

    /** Makes the store say it {@linkplain #storesConfidentialKeys stores confidential keys}. */
    public InMemoryPersistenceStore withConfidentialKeys() {
        root.confidentialKeys = true;
        return this;
    }

    @Override
    public boolean storesConfidentialKeys() {
        return root.confidentialKeys;
    }

    private void check() throws IOException {
        if (root.unreachable) {
            throw new IOException("The store cannot be reached (test)");
        }
    }

    @Override
    @NonNull
    public String getOrigin() {
        return origin;
    }

    @Override
    @NonNull
    public AutoCloseable subscribe(@NonNull Consumer<ChangeEvent> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /**
     * Delivers the changes made so far in the order they were made: two threads committing one after the other must
     * not have their changes delivered the other way round.
     */
    private void deliver() {
        synchronized (delivering) {
            if (root.draining == Thread.currentThread()) {
                return; // a listener made a change: the loop below delivers it once every listener had the current one
            }
            root.draining = Thread.currentThread();
            try {
                while (true) {
                    ChangeEvent e;
                    synchronized (root) {
                        e = outbox.poll();
                    }
                    if (e == null) {
                        return;
                    }
                    for (Consumer<ChangeEvent> l : listeners) {
                        try {
                            l.accept(e);
                        } catch (RuntimeException | LinkageError x) {
                            // the change is made: the other listeners still get it, and the caller is not told it failed
                            LOGGER.log(Level.WARNING, "A listener failed to handle " + e, x);
                        }
                    }
                }
            } finally {
                root.draining = null;
            }
        }
    }

    @Override
    @CheckForNull
    public Entry read(@NonNull String key) throws IOException {
        check();
        synchronized (root) {
            Entry e = entries.get(key);
            return e == null ? null : new Entry(e.content().clone(), e.version());
        }
    }

    @Override
    public long write(@NonNull String key, @NonNull byte[] content, long expectedVersion) throws IOException {
        check();
        long version;
        synchronized (root) {
            Entry current = entries.get(key);
            long actual = current == null ? ABSENT : current.version();
            if (expectedVersion != ANY && expectedVersion != actual) {
                throw new ConflictException(key, expectedVersion, actual);
            }
            if (current != null && Arrays.equals(current.content(), content)) {
                return actual;
            }
            version = ++root.clock;
            entries.put(key, new Entry(content.clone(), version));
            outbox.add(new ChangeEvent(ChangeEvent.Kind.WRITTEN, key, null, version, origin));
        }
        deliver();
        return version;
    }

    @Override
    public void delete(@NonNull String key, long expectedVersion) throws IOException {
        check();
        synchronized (root) {
            Entry current = entries.get(key);
            if (current == null) {
                return;
            }
            if (expectedVersion != ANY && expectedVersion != current.version()) {
                throw new ConflictException(key, expectedVersion, current.version());
            }
            entries.remove(key);
            outbox.add(new ChangeEvent(ChangeEvent.Kind.DELETED, key, null, ++root.clock, origin));
        }
        deliver();
    }

    @Override
    @NonNull
    public Set<String> children(@NonNull String parentKey) throws IOException {
        check();
        String prefix = parentKey + "/";
        Set<String> names = new TreeSet<>();
        synchronized (root) {
            for (String key : entries.tailMap(prefix).keySet()) {
                if (!key.startsWith(prefix)) {
                    break;
                }
                String rest = key.substring(prefix.length());
                int slash = rest.indexOf('/');
                names.add(slash == -1 ? rest : rest.substring(0, slash));
            }
        }
        return names;
    }

    @Override
    @NonNull
    public List<Moved> move(@NonNull String fromPrefix, @NonNull String toPrefix, @NonNull List<Precondition> preconditions) throws IOException {
        check();
        List<Moved> moved = new ArrayList<>();
        synchronized (root) {
            for (Precondition p : preconditions) {
                Entry e = entries.get(p.key());
                long actual = e == null ? ABSENT : e.version();
                long expected = p.expectedVersion();
                boolean holds = expected == ANY || (expected == PRESENT ? actual != ABSENT : actual == expected);
                if (!holds) {
                    throw new ConflictException(p.key(), expected, actual);
                }
            }
            List<String> keys = new ArrayList<>();
            for (String key : entries.keySet()) {
                if (key.equals(toPrefix) || key.startsWith(toPrefix + "/")) {
                    throw new ConflictException(key, ABSENT, entries.get(key).version());
                }
                if (key.equals(fromPrefix) || key.startsWith(fromPrefix + "/")) {
                    keys.add(key);
                }
            }
            // shallower keys first, so that an item's configuration moves before what is below it
            keys.sort((a, b) -> {
                int da = a.split("/").length;
                int db = b.split("/").length;
                return da != db ? Integer.compare(da, db) : a.compareTo(b);
            });
            for (String key : keys) {
                String newKey = toPrefix + key.substring(fromPrefix.length());
                long version = ++root.clock;
                Entry old = entries.remove(key);
                entries.put(newKey, new Entry(old.content(), version));
                moved.add(new Moved(key, old.version(), newKey, version));
                outbox.add(new ChangeEvent(ChangeEvent.Kind.MOVED, newKey, key, version, origin));
            }
        }
        deliver();
        return moved;
    }

    /**
     * The stored keys with their versions, for assertions.
     */
    public Map<String, Long> versions() {
        Map<String, Long> r = new TreeMap<>();
        synchronized (root) {
            entries.forEach((k, v) -> r.put(k, v.version()));
        }
        return r;
    }

    /** The stored content of a key as text, or null, for assertions. */
    @CheckForNull
    public String text(@NonNull String key) {
        synchronized (root) {
            Entry e = entries.get(key);
            return e == null ? null : new String(e.content(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
