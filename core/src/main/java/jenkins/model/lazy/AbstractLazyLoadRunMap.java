/*
 * The MIT License
 *
 * Copyright (c) 2012, CloudBees, Inc.
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

package jenkins.model.lazy;

import static jenkins.model.lazy.AbstractLazyLoadRunMap.Direction.ASC;
import static jenkins.model.lazy.AbstractLazyLoadRunMap.Direction.DESC;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.RunMap;
import hudson.model.listeners.RunListener;
import java.io.File;
import java.io.IOException;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeMap;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import jenkins.util.MemoryReductionUtil;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * {@link SortedMap} that keeps build records by their build numbers, in the descending order
 * (newer ones first.)
 *
 * <p>
 * The main thing about this class is that it encapsulates the lazy loading logic.
 * That is, while this class looks and feels like a normal {@link SortedMap} from outside,
 * it actually doesn't have every item in the map instantiated yet. As items in the map get
 * requested, this class {@link #retrieve(File) retrieves them} on demand, one by one.
 *
 * <p>
 * The lookup is done by using the build number as the key (hence the key type is {@link Integer}).
 *
 * <p>
 * This class makes the following assumption about the on-disk layout of the data:
 *
 * <ul>
 *     <li>Every build is stored in a directory, named after its number.
 * </ul>
 *
 * <p>
 * Some of the {@link SortedMap} operations are weakly implemented. For example,
 * {@link #size()} may be inaccurate because we only count the number of directories that look like
 * build records, without checking if they are loadable. But these weaknesses aren't distinguishable
 * from concurrent modifications, where another thread deletes a build while one thread iterates them.
 *
 * <p>
 * Some of the {@link SortedMap} operations are inefficiently implemented, by
 * {@linkplain #all() loading all the build records eagerly}. We hope to replace
 * these implementations by more efficient lazy-loading ones as we go.
 *
 * <p>
 * Object lock of {@code this} is used to make sure mutation occurs sequentially.
 * That is, ensure that only one thread is actually calling {@link #retrieve(File)} and
 * updating {@link jenkins.model.lazy.AbstractLazyLoadRunMap.Index#byNumber}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.485
 */
public abstract class AbstractLazyLoadRunMap<R> extends AbstractMap<Integer, R> implements SortedMap<Integer, R> {
    /**
     * Used in {@link #all()} to quickly determine if we've already loaded everything.
     */
    private volatile boolean fullyLoaded;

    /**
     * Currently visible index.
     * Updated atomically. Once set to this field, the index object may not be modified.
     */
    private volatile Index index = new Index();
    private LazyLoadRunMapEntrySet<R> entrySet = new LazyLoadRunMapEntrySet<>(this);

    private transient volatile Set<Integer> keySet;
    private transient volatile Collection<R> values;

    @Override
    public Set<Integer> keySet() {
        Set<Integer> ks = keySet;
        if (ks == null) {
            ks = new AbstractSet<>() {
                @Override
                public Iterator<Integer> iterator() {
                    return new Iterator() {
                        private final Iterator<Entry<Integer, R>> it = entrySet().iterator();

                        @Override
                        public boolean hasNext() {
                            return it.hasNext();
                        }

                        @Override
                        public Integer next() {
                            return it.next().getKey();
                        }

                        @Override
                        public void remove() {
                            it.remove();
                        }
                    };
                }

                @Override
                public Spliterator<Integer> spliterator() {
                    return new Spliterators.AbstractIntSpliterator(
                            Long.MAX_VALUE,
                            Spliterator.DISTINCT | Spliterator.ORDERED | Spliterator.SORTED) {
                        private final Iterator<Integer> it = iterator();

                        @Override
                        public boolean tryAdvance(IntConsumer action) {
                            if (action == null) {
                                throw new NullPointerException();
                            }
                            if (it.hasNext()) {
                                action.accept(it.next());
                                return true;
                            }
                            return false;
                        }

                        @Override
                        public Comparator<Integer> getComparator() {
                            return Collections.reverseOrder();
                        }
                    };
                }

                @Override
                public int size() {
                    return AbstractLazyLoadRunMap.this.size();
                }

                @Override
                public boolean isEmpty() {
                    return AbstractLazyLoadRunMap.this.isEmpty();
                }

                @Override
                public void clear() {
                    AbstractLazyLoadRunMap.this.clear();
                }

                @Override
                public boolean contains(Object k) {
                    return AbstractLazyLoadRunMap.this.containsKey(k);
                }
            };
            keySet = ks;
        }
        return ks;
    }

    @Override
    public Collection<R> values() {
        Collection<R> vals = values;
        if (vals == null) {
            vals = new AbstractCollection<>() {
                @Override
                public Iterator<R> iterator() {
                    return new Iterator<>() {
                        private final Iterator<Entry<Integer, R>> it = entrySet().iterator();

                        @Override
                        public boolean hasNext() {
                            return it.hasNext();
                        }

                        @Override
                        public R next() {
                            return it.next().getValue();
                        }

                        @Override
                        public void remove() {
                            it.remove();
                        }
                    };
                }

                @Override
                public Spliterator<R> spliterator() {
                    return Spliterators.spliteratorUnknownSize(
                            iterator(), Spliterator.DISTINCT | Spliterator.ORDERED);
                }

                @Override
                public int size() {
                    return AbstractLazyLoadRunMap.this.size();
                }

                @Override
                public boolean isEmpty() {
                    return AbstractLazyLoadRunMap.this.isEmpty();
                }

                @Override
                public void clear() {
                    AbstractLazyLoadRunMap.this.clear();
                }

                @Override
                public boolean contains(Object v) {
                    return AbstractLazyLoadRunMap.this.containsValue(v);
                }
            };
            values = vals;
        }
        return vals;
    }

    /**
     * Historical holder for map.
     *
     * TODO all this mess including {@link #numberOnDisk} could probably be simplified to a single {@code TreeMap<Integer,BuildReference<R>>}
     * where a null value means not yet loaded and a broken entry just uses {@code NoHolder}.
     *
     * The idiom is that you put yourself in a synchronized block, {@linkplain #copy() make a copy of this},
     * update the copy, then set it to {@link #index}.
     */
    private class Index {
        /**
         * Stores the mapping from build number to build, for builds that are already loaded.
         *
         * If we have known load failure of the given ID, we record that in the map
         * by using the null value (not to be confused with a non-null {@link BuildReference}
         * with null referent, which just means the record was GCed.)
         */
        private final TreeMap<Integer, BuildReference<R>> byNumber;

        private Index() {
            byNumber = new TreeMap<>(Collections.reverseOrder());
        }

        private Index(Index rhs) {
            byNumber = new TreeMap<>(rhs.byNumber);
        }
    }

    /**
     * Build numbers found on disk, in the ascending order.
     */
    // copy on write
    private volatile SortedIntList numberOnDisk = new SortedIntList(0);

    /**
     * Base directory for data.
     * In effect this is treated as a final field, but can't mark it final
     * because the compatibility requires that we make it settable
     * in the first call after the constructor.
     */
    protected File dir;

    @Restricted(NoExternalUse.class) // subclassing other than by RunMap does not guarantee compatibility
    protected AbstractLazyLoadRunMap() {
    }

    @Restricted(NoExternalUse.class)
    protected void initBaseDir(File dir) {
        assert this.dir == null;
        this.dir = dir;
        if (dir != null)
            loadNumberOnDisk();
    }

    /**
     * @return true if {@link AbstractLazyLoadRunMap#AbstractLazyLoadRunMap} was called with a non-null param, or {@link RunMap#load(Job, RunMap.Constructor)} was called
     */
    @Restricted(NoExternalUse.class)
    public final boolean baseDirInitialized() {
        return dir != null;
    }

    /**
     * Updates base directory location after directory changes.
     * This method should be used on jobs renaming, etc.
     * @param dir Directory location
     * @since 1.546
     */
    public final void updateBaseDir(File dir) {
        this.dir = dir;
    }

    /**
     * Let go of all the loaded references.
     *
     * This is a bit more sophisticated version of forcing GC.
     * Primarily for debugging and testing lazy loading behaviour.
     * @since 1.507
     */
    public synchronized void purgeCache() {
        index = new Index();
        fullyLoaded = false;
        loadNumberOnDisk();
    }

    private static final Pattern BUILD_NUMBER = Pattern.compile("[0-9]+");

    private void loadNumberOnDisk() {
        String[] kids = dir.list();
        if (kids == null) {
            // the job may have just been created
            kids = MemoryReductionUtil.EMPTY_STRING_ARRAY;
        }
        SortedIntList list = new SortedIntList(kids.length / 2);
        for (String s : kids) {
            if (!BUILD_NUMBER.matcher(s).matches()) {
                // not a build directory
                continue;
            }
            try {
                int buildNumber = Integer.parseInt(s);
                if (allowLoad(buildNumber)) {
                    list.add(buildNumber);
                } else {
                    LOGGER.fine(() -> "declining to consider " + buildNumber + " in " + dir);
                }
            } catch (NumberFormatException e) {
                // matched BUILD_NUMBER but not an int?
            }
        }
        list.sort();
        numberOnDisk = list;
    }

    @Restricted(NoExternalUse.class)
    protected boolean allowLoad(int buildNumber) {
        return true;
    }

    /**
     * Permits a previous blocked build number to be eligible for loading.
     * @param buildNumber a build number
     * @see RunListener#allowLoad
     */
    @Restricted(Beta.class)
    public final void recognizeNumber(int buildNumber) {
        if (new File(dir, Integer.toString(buildNumber)).isDirectory()) {
            synchronized (this) {
                SortedIntList list = new SortedIntList(numberOnDisk);
                if (list.contains(buildNumber)) {
                    LOGGER.fine(() -> "already knew about " + buildNumber + " in " + dir);
                } else {
                    list.add(buildNumber);
                    list.sort();
                    numberOnDisk = list;
                    LOGGER.fine(() -> "recognizing " + buildNumber + " in " + dir);
                }
            }
        } else {
            LOGGER.fine(() -> "no such subdirectory " + buildNumber + " in " + dir);
        }
    }

    @Override
    public Comparator<? super Integer> comparator() {
        return Collections.reverseOrder();
    }

    @Override
    public boolean isEmpty() {
        return search(Integer.MAX_VALUE, DESC) == null;
    }

    @Override
    public Set<Entry<Integer, R>> entrySet() {
        assert baseDirInitialized();
        return entrySet;
    }

    /**
     * Returns a read-only view of records that has already been loaded.
     */
    public SortedMap<Integer, R> getLoadedBuilds() {
        return Collections.unmodifiableSortedMap(new BuildReferenceMapAdapter<>(this, index.byNumber));
    }

    /**
     * @param fromKey
     *      Biggest build number to be in the returned set.
     * @param toKey
     *      Smallest build number-1 to be in the returned set (-1 because this is exclusive)
     */
    @Override
    public SortedMap<Integer, R> subMap(Integer fromKey, Integer toKey) {
        // TODO: if this method can produce a lazy map, that'd be wonderful
        // because due to the lack of floor/ceil/higher/lower kind of methods
        // to look up keys in SortedMap, various places of Jenkins rely on
        // subMap+firstKey/lastKey combo.

        R start = search(fromKey, DESC);
        if (start == null)    return EMPTY_SORTED_MAP;

        R end = search(toKey, ASC);
        if (end == null)      return EMPTY_SORTED_MAP;

        for (R i = start; i != end; ) {
            i = search(getNumberOf(i) - 1, DESC);
            assert i != null;
        }

        return Collections.unmodifiableSortedMap(new BuildReferenceMapAdapter<>(this, index.byNumber.subMap(fromKey, toKey)));
    }

    @Override
    public SortedMap<Integer, R> headMap(Integer toKey) {
        return subMap(Integer.MAX_VALUE, toKey);
    }

    @Override
    public SortedMap<Integer, R> tailMap(Integer fromKey) {
        return subMap(fromKey, Integer.MIN_VALUE);
    }

    @Override
    public Integer firstKey() {
        R r = newestBuild();
        if (r == null)    throw new NoSuchElementException();
        return getNumberOf(r);
    }

    @Override
    public Integer lastKey() {
        R r = oldestBuild();
        if (r == null)    throw new NoSuchElementException();
        return getNumberOf(r);
    }

    public R newestBuild() {
        return search(Integer.MAX_VALUE, DESC);
    }

    public R oldestBuild() {
        return search(Integer.MIN_VALUE, ASC);
    }

    @Override
    public R get(Object key) {
        if (key instanceof Integer) {
            int n = (Integer) key;
            return get(n);
        }
        return super.get(key);
    }

    public R get(int n) {
        return getByNumber(n);
    }

    /**
     * Checks if the specified build exists.
     *
     * @param number the build number to probe.
     * @return {@code true} if there is an run for the corresponding number, note that this does not mean that
     * the corresponding record will load.
     * @since 2.14
     */
    public boolean runExists(int number) {
        return numberOnDisk.contains(number);
    }

    /**
     * Finds the build #M where M is nearby the given 'n'.
     *
     * @param n
     *      the index to start the search from
     * @param d
     *      defines what we mean by "nearby" above.
     *      If EXACT, find #N or return null.
     *      If ASC, finds the closest #M that satisfies M ≥ N.
     *      If DESC, finds the closest #M that satisfies M ≤ N.
     */
    public @CheckForNull R search(final int n, final Direction d) {
        switch (d) {
        case EXACT:
            return getByNumber(n);
        case ASC:
            for (int m : numberOnDisk) {
                if (m < n) {
                    // TODO could be made more efficient with numberOnDisk.find
                    continue;
                }
                R r = getByNumber(m);
                if (r != null) {
                    return r;
                }
            }
            return null;
        case DESC:
            // TODO again could be made more efficient
            ListIterator<Integer> iterator = numberOnDisk.listIterator(numberOnDisk.size());
            while (iterator.hasPrevious()) {
                int m = iterator.previous();
                if (m > n) {
                    continue;
                }
                R r = getByNumber(m);
                if (r != null) {
                    return r;
                }
            }
            return null;
        default:
            throw new AssertionError();
        }
    }

    public R getById(String id) {
        return getByNumber(Integer.parseInt(id));
    }

    public R getByNumber(int n) {
        Index snapshot = index;
        if (snapshot.byNumber.containsKey(n)) {
            BuildReference<R> ref = snapshot.byNumber.get(n);
            if (ref == null) {
                LOGGER.fine(() -> "known failure of #" + n + " in " + dir);
                return null;
            }
            R v = unwrap(ref);
            if (v != null) {
                return v; // already in memory
            }
            // otherwise fall through to load
        }
        synchronized (this) {
            if (index.byNumber.containsKey(n)) { // JENKINS-22767: recheck inside lock
                BuildReference<R> ref = index.byNumber.get(n);
                if (ref == null) {
                    LOGGER.fine(() -> "known failure of #" + n + " in " + dir);
                    return null;
                }
                R v = unwrap(ref);
                if (v != null) {
                    return v;
                }
            }
            if (allowLoad(n)) {
                return load(n, null);
            } else {
                LOGGER.fine(() -> "declining to load " + n + " in " + dir);
                return null;
            }
        }
    }

    /**
     * @return the highest recorded build number, or 0 if there are none
     */
    @Restricted(NoExternalUse.class)
    public synchronized int maxNumberOnDisk() {
        return numberOnDisk.max();
    }

    protected final synchronized void proposeNewNumber(int number) throws IllegalStateException {
        if (number <= maxNumberOnDisk()) {
            throw new IllegalStateException("JENKINS-27530: cannot create a build with number " + number + " since that (or higher) is already in use among " + numberOnDisk);
        }
    }

    public R put(R value) {
        return _put(value);
    }

    protected R _put(R value) {
        return put(getNumberOf(value), value);
    }

    @Override
    public synchronized R put(Integer key, R r) {
        int n = getNumberOf(r);

        Index copy = copy();
        BuildReference<R> ref = createReference(r);
        BuildReference<R> old = copy.byNumber.put(n, ref);
        index = copy;

        if (!numberOnDisk.contains(n)) {
            SortedIntList a = new SortedIntList(numberOnDisk);
            a.add(n);
            a.sort();
            numberOnDisk = a;
        }

        entrySet.clearCache();

        return unwrap(old);
    }

    private R unwrap(BuildReference<R> ref) {
        return ref != null ? ref.get() : null;
    }

    @Override
    public synchronized void putAll(Map<? extends Integer, ? extends R> rhs) {
        Index copy = copy();
        for (R r : rhs.values()) {
            BuildReference<R> ref = createReference(r);
            copy.byNumber.put(getNumberOf(r), ref);
        }
        index = copy;
    }

    /**
     * Loads all the build records to fully populate the map.
     * Calling this method results in eager loading everything,
     * so the whole point of this class is to avoid this call as much as possible
     * for typical code path.
     *
     * @return
     *      fully populated map.
     */
    /*package*/ TreeMap<Integer, BuildReference<R>> all() {
        if (!fullyLoaded) {
            synchronized (this) {
                if (!fullyLoaded) {
                    Index copy = copy();
                    for (Integer number : numberOnDisk) {
                        if (!copy.byNumber.containsKey(number))
                            load(number, copy);
                    }
                    index = copy;
                    fullyLoaded = true;
                }
            }
        }
        return index.byNumber;
    }

    /**
     * Creates a duplicate for the COW data structure in preparation for mutation.
     */
    private Index copy() {
        return new Index(index);
   }

    /**
     * Tries to load the record #N.
     *
     * @return null if the data failed to load.
     */
    private R load(int n, Index editInPlace) {
        assert Thread.holdsLock(this);
        assert dir != null;
        R v = load(new File(dir, String.valueOf(n)), editInPlace);
        if (v == null && editInPlace != null) {
            // remember the failure.
            // if editInPlace==null, we can create a new copy for this, but not sure if it's worth doing,
            // TODO should we also update numberOnDisk?
            editInPlace.byNumber.put(n, null);
        }
        return v;
    }

    /**
     * @param editInPlace
     *      If non-null, update this data structure.
     *      Otherwise do a copy-on-write of {@link #index}
     */
    private R load(File dataDir, Index editInPlace) {
        assert Thread.holdsLock(this);
        try {
            R r = retrieve(dataDir);
            if (r == null) {
                LOGGER.fine(() -> "nothing in " + dataDir);
                return null;
            }

            Index copy = editInPlace != null ? editInPlace : new Index(index);

            BuildReference<R> ref = createReference(r);
            BuildReference<R> old = copy.byNumber.put(getNumberOf(r), ref);
            assert old == null || old.get() == null : "tried to overwrite " + old + " with " + ref;

            if (editInPlace == null)  index = copy;

            return r;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load " + dataDir, e);
        }
        return null;
    }

    /**
     * Subtype to provide {@link Run#getNumber()} so that this class doesn't have to depend on it.
     */
    protected abstract int getNumberOf(R r);

    /**
     * Subtype to provide {@link Run#getId()} so that this class doesn't have to depend on it.
     */
    protected String getIdOf(R r) {
        return String.valueOf(getNumberOf(r));
    }

    /**
     * Allow subtype to capture a reference.
     */
    protected BuildReference<R> createReference(R r) {
        return new BuildReference<>(getIdOf(r), r);
    }


    /**
     * Parses {@code R} instance from data in the specified directory.
     *
     * @return
     *      null if the parsing failed.
     * @throws IOException
     *      if the parsing failed. This is just like returning null
     *      except the caller will catch the exception and report it.
     */
    protected abstract R retrieve(File dir) throws IOException;

    public synchronized boolean removeValue(R run) {
        Index copy = copy();
        int n = getNumberOf(run);
        BuildReference<R> old = copy.byNumber.remove(n);
        SortedIntList a = new SortedIntList(numberOnDisk);
        a.removeValue(n);
        numberOnDisk = a;
        this.index = copy;

        entrySet.clearCache();

        return old != null;
    }

    /**
     * Replaces all the current loaded Rs with the given ones.
     */
    public synchronized void reset(TreeMap<Integer, R> builds) {
        Index index = new Index();
        for (R r : builds.values()) {
            BuildReference<R> ref = createReference(r);
            index.byNumber.put(getNumberOf(r), ref);
        }

        this.index = index;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object o) {
        return o == this;
    }

    public enum Direction {
        ASC, DESC, EXACT
    }

    private static final SortedMap EMPTY_SORTED_MAP = Collections.unmodifiableSortedMap(new TreeMap());

    static final Logger LOGGER = Logger.getLogger(AbstractLazyLoadRunMap.class.getName());
}
