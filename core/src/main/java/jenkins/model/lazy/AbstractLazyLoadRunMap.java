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
import static jenkins.model.lazy.AbstractLazyLoadRunMap.Direction.EXACT;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.RunMap;
import hudson.model.listeners.RunListener;
import hudson.util.CopyOnWriteMap;
import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.IntPredicate;
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
 * Object lock of {@code this} is used to make sure mutation occurs sequentially.
 * That is, ensure that only one thread is actually calling {@link #retrieve(File)} and
 * updating {@link jenkins.model.lazy.AbstractLazyLoadRunMap#core}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.485
 */
public abstract class AbstractLazyLoadRunMap<R> extends AbstractMap<Integer, R> implements SortedMap<Integer, R> {
    private final CopyOnWriteMap.Tree<Integer, BuildReference<R>> core = new CopyOnWriteMap.Tree<>(
            Collections.reverseOrder());
    private final BuildReferenceMapAdapter.Resolver<R> buildResolver = new BuildReferenceMapAdapterResolver();
    private final BuildReferenceMapAdapter<R> adapter = new BuildReferenceMapAdapter<>(core, buildResolver) {
        @Override
        protected boolean removeValue(R value) {
            return AbstractLazyLoadRunMap.this.removeValue(value);
        }
    };

    @Override
    public Set<Integer> keySet() {
        return adapter.keySet();
    }

    @Override
    public Collection<R> values() {
        return adapter.values();
    }

    @Override
    public Set<Map.Entry<Integer, R>> entrySet() {
        assert baseDirInitialized();
        return adapter.entrySet();
    }

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
        loadNumberOnDisk();
    }

    private static final Pattern BUILD_NUMBER = Pattern.compile("[0-9]+");

    private void loadNumberOnDisk() {
        String[] kids = dir.list();
        if (kids == null) {
            // the job may have just been created
            kids = MemoryReductionUtil.EMPTY_STRING_ARRAY;
        }
        TreeMap<Integer, BuildReference<R>> newBuildRefsMap = new TreeMap<>();
        var allower = createLoadAllower();
        for (String s : kids) {
            if (!BUILD_NUMBER.matcher(s).matches()) {
                // not a build directory
                continue;
            }
            try {
                int buildNumber = Integer.parseInt(s);
                if (allower.test(buildNumber)) {
                    newBuildRefsMap.put(buildNumber, new BuildReference<>(s));
                } else {
                    LOGGER.fine(() -> "declining to consider " + buildNumber + " in " + dir);
                }
            } catch (NumberFormatException e) {
                // matched BUILD_NUMBER but not an int?
            }
        }
        core.replaceBy(newBuildRefsMap);
    }

    @Restricted(NoExternalUse.class)
    protected boolean allowLoad(int buildNumber) {
        return true;
    }

    @Restricted(NoExternalUse.class)
    protected IntPredicate createLoadAllower() {
        return this::allowLoad;
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
                if (this.core.containsKey(buildNumber)) {
                    LOGGER.fine(() -> "already knew about " + buildNumber + " in " + dir);
                } else {
                    core.put(buildNumber, new BuildReference<>(String.valueOf(buildNumber)));
                    LOGGER.fine(() -> "recognizing " + buildNumber + " in " + dir);
                }
            }
        } else {
            LOGGER.fine(() -> "no such subdirectory " + buildNumber + " in " + dir);
        }
    }

    @Override
    public Comparator<? super Integer> comparator() {
        return core.comparator();
    }

    @Override
    public boolean isEmpty() {
        return adapter.isEmpty();
    }

    @Override
    public boolean containsKey(Object value) {
        return adapter.containsKey(value);
    }

    @Override
    public boolean containsValue(Object value) {
        return adapter.containsValue(value);
    }

    /**
     * Returns a read-only view of records that has already been loaded.
     */
    public SortedMap<Integer, R> getLoadedBuilds() {
        TreeMap<Integer, BuildReference<R>> res = new TreeMap<>(Comparator.reverseOrder());
        for (var entry : this.core.entrySet()) {
            BuildReference<R> buildRef = entry.getValue();
            if (buildRef.isSet() && !buildRef.isUnloadable()) {
                res.put(entry.getKey(), buildRef);
            }
        }
        return new BuildReferenceMapAdapter<>(res, buildResolver);
    }

    /**
     * @param fromKey
     *      Biggest build number to be in the returned set.
     * @param toKey
     *      Smallest build number-1 to be in the returned set (-1 because this is exclusive)
     */
    @Override
    public SortedMap<Integer, R> subMap(Integer fromKey, Integer toKey) {
        return adapter.subMap(fromKey, toKey);
    }

    @Override
    public SortedMap<Integer, R> headMap(Integer toKey) {
        return adapter.headMap(toKey);
    }

    @Override
    public SortedMap<Integer, R> tailMap(Integer fromKey) {
        return adapter.tailMap(fromKey);
    }

    @Override
    public Integer firstKey() {
        return adapter.firstKey();
    }

    @Override
    public Integer lastKey() {
        return adapter.lastKey();
    }

    public R newestBuild() {
        return search(Integer.MAX_VALUE, DESC);
    }

    public R oldestBuild() {
        return search(Integer.MIN_VALUE, ASC);
    }

    @Override
    public R get(Object key) {
        return adapter.get(key);
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
        return this.core.containsKey(number);
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
        if (d == EXACT) {
            return this.adapter.get(n);
        }
        // prepare sub map, where we need to find first resolvable entry
        NavigableMap<Integer, BuildReference<R>> subCore = (d == ASC)
                ? core.headMap(n, true).descendingMap()
                : core.tailMap(n, true);
        // wrap with BuildReferenceMapAdapter to skip unresolvable entries
        return new BuildReferenceMapAdapter<>(subCore, buildResolver).values().stream().findFirst().orElse(null);
    }

    public R getById(String id) {
        return getByNumber(Integer.parseInt(id));
    }

    /**
     * Ensure loading referent object if needed, cache it and return
     * Save that object as 'unloadable' in case of failure to avoid next load attempts
     *
     * @param ref reference object to be resolved
     * @return R referent build object, or null if it can't be resolved
     */
    private R resolveBuildRef(BuildReference<R> ref) {
        if (ref == null || ref.isUnloadable()) {
            return null;
        }
        R v;
        if ((v = ref.get()) != null) {
            return v; // already in memory
        }
        // otherwise fall through to load
        synchronized (this) {
            if ((v = ref.get()) != null) {
                return v; // already in memory
            }
            int n = ref.number;
            if (allowLoad(n)) {
                v = load(n);
                // save if build unloadable
                if (v == null) {
                    ref.setUnloadable();
                    return null;
                }
                ref.set(v);
                return v;
            } else {
                LOGGER.fine(() -> "declining to load " + n + " in " + dir);
                return null;
            }
        }
    }

    public R getByNumber(int n) {
        return adapter.get(n);
    }

    /**
     * @return the highest recorded build number, or 0 if there are none
     */
    @Restricted(NoExternalUse.class)
    public synchronized int maxNumberOnDisk() {
        try {
            return this.core.firstKey();
        } catch (NoSuchElementException ignored) {
            return 0;
        }
    }

    protected final synchronized void proposeNewNumber(int number) throws IllegalStateException {
        if (number <= maxNumberOnDisk()) {
            throw new IllegalStateException("JENKINS-27530: cannot create a build with number " + number + " since that (or higher) is already in use among " + keySet());
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
        BuildReference<R> old = core.put(n, createReference(r));
        return resolveBuildRef(old);
    }

    @Override
    public synchronized void putAll(Map<? extends Integer, ? extends R> newData) {
        TreeMap<Integer, BuildReference<R>> newWrapperData = new TreeMap<>();
        for (Map.Entry<? extends Integer, ? extends R> entry : newData.entrySet()) {
            newWrapperData.put(entry.getKey(), createReference(entry.getValue()));
        }
        core.putAll(newWrapperData);
    }

    @Override
    public R remove(Object key) {
        return adapter.remove(key);
    }

    /**
     * Tries to load the record #N.
     *
     * @return null if the data failed to load.
     */
    private R load(int n) {
        assert Thread.holdsLock(this);
        assert dir != null;
        return load(new File(dir, String.valueOf(n)));
    }

    private R load(File dataDir) {
        assert Thread.holdsLock(this);
        try {
            R r = retrieve(dataDir);
            if (r == null) {
                LOGGER.fine(() -> "nothing in " + dataDir);
                return null;
            }
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

    protected abstract Class<R> getBuildClass();

    public synchronized boolean removeValue(R run) {
        return core.remove(getNumberOf(run)) != null;
    }

    /**
     * Replaces all the current loaded Rs with the given ones.
     */
    public synchronized void reset(Map<Integer, R> builds) {
        TreeMap<Integer, BuildReference<R>> copy = new TreeMap<>();
        for (R r : builds.values()) {
            copy.put(getNumberOf(r), createReference(r));
        }

        this.core.replaceBy(copy);
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

    private class BuildReferenceMapAdapterResolver implements BuildReferenceMapAdapter.Resolver<R> {
        @Override
        public R resolveBuildRef(BuildReference<R> buildRef) {
            return AbstractLazyLoadRunMap.this.resolveBuildRef(buildRef);
        }

        @Override
        public Integer getNumberOf(R build) {
            return AbstractLazyLoadRunMap.this.getNumberOf(build);
        }

        @Override
        public Class<R> getBuildClass() {
            return AbstractLazyLoadRunMap.this.getBuildClass();
        }
    }

    static final Logger LOGGER = Logger.getLogger(AbstractLazyLoadRunMap.class.getName());
}
