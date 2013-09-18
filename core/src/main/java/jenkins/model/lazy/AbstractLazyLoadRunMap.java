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

import hudson.model.Job;
import hudson.model.Run;
import hudson.model.RunMap;
import org.apache.commons.collections.keyvalue.DefaultMapEntry;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.ref.Reference;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;

import static jenkins.model.lazy.AbstractLazyLoadRunMap.Direction.*;
import static jenkins.model.lazy.Boundary.*;

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
 * The lookup is primarily done by using the build number as the key (hence the key type is {@link Integer}),
 * but this class also provides look up based on {@linkplain #getIdOf(Object) the build ID}.
 *
 * <p>
 * This class makes the following assumption about the on-disk layout of the data:
 *
 * <ul>
 *     <li>Every build is stored in a directory, named after its ID.
 *     <li>ID and build number are in the consistent order. That is,
 *         if there are two builds #M and #N, {@code M>N <=> M.id > N.id}.
 * </ul>
 *
 * <p>
 * On certain platforms, there are symbolic links named after build numbers that link to the build ID.
 * If these are available, they are used as a hint to speed up the lookup. Otherwise
 * we rely on the assumption above and perform a binary search to locate the build.
 * (notice that we'll have to do linear search if we don't have the consistent ordering assumption,
 * which robs the whole point of doing lazy loading.)
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
 * updating {@link jenkins.model.lazy.AbstractLazyLoadRunMap.Index#byNumber} and {@link jenkins.model.lazy.AbstractLazyLoadRunMap.Index#byId}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.485
 */
public abstract class AbstractLazyLoadRunMap<R> extends AbstractMap<Integer,R> implements SortedMap<Integer,R> {
    /**
     * Used in {@link #all()} to quickly determine if we've already loaded everything.
     */
    private boolean fullyLoaded;

    /**
     * Currently visible index.
     * Updated atomically. Once set to this field, the index object may not be modified.
     */
    private volatile Index index = new Index();

    /**
     * Pair of two maps into a single class, so that the changes can be made visible atomically,
     * and updates can happen concurrently to read.
     *
     * The idiom is that you put yourself in a synchronized block, {@linkplain #copy() make a copy of this},
     * update the copy, then set it to {@link #index}.
     */
    private class Index {
        /**
         * Stores the mapping from build number to build, for builds that are already loaded.
         */
        private final TreeMap<Integer,BuildReference<R>> byNumber;

        /**
         * Stores the build ID to build number for builds that we already know.
         *
         * If we have known load failure of the given ID, we record that in the map
         * by using the null value (not to be confused with a non-null {@link BuildReference}
         * with null referent, which just means the record was GCed.)
         */
        private final TreeMap<String,BuildReference<R>> byId;

        private Index() {
            byId = new TreeMap<String,BuildReference<R>>();
            byNumber = new TreeMap<Integer,BuildReference<R>>(COMPARATOR);
        }

        private Index(Index rhs) {
            byId     = new TreeMap<String, BuildReference<R>>(rhs.byId);
            byNumber = new TreeMap<Integer,BuildReference<R>>(rhs.byNumber);
        }

        /**
         * Returns the build record #M (<=n)
         */
        private Map.Entry<Integer,BuildReference<R>> ceilingEntry(int n) {
// switch to this once we depend on JDK6
//            return byNumber.ceilingEntry(n);

            Set<Entry<Integer, BuildReference<R>>> s = byNumber.tailMap(n).entrySet();
            if (s.isEmpty())    return null;
            else                return s.iterator().next();
        }

        /**
         * Returns the build record #M (>=n)
         */
        // >= and not <= because byNumber is in the descending order
        private Map.Entry<Integer,BuildReference<R>> floorEntry(int n) {
// switch to this once we depend on JDK6
//            return byNumber.floorEntry(n);

            SortedMap<Integer, BuildReference<R>> sub = byNumber.headMap(n);
            if (sub.isEmpty())    return null;
            Integer k = sub.lastKey();
            return new DefaultMapEntry(k,sub.get(k));
        }
    }

    /**
     * Build IDs found as directories, in the ascending order.
     */
    // copy on write
    private volatile SortedList<String> idOnDisk = new SortedList<String>(Collections.<String>emptyList());

    /**
     * Build number shortcuts found on disk, in the ascending order.
     */
    // copy on write
    private volatile SortedIntList numberOnDisk = new SortedIntList(0);

    /**
     * Base directory for data.
     * In effect this is treated as a final field, but can't mark it final
     * because the compatibility requires that we make it settable
     * in the first call after the constructor.
     */
    private File dir;

    protected AbstractLazyLoadRunMap(File dir) {
        initBaseDir(dir);
    }

    @Restricted(NoExternalUse.class)
    protected void initBaseDir(File dir) {
        assert this.dir==null;
        this.dir = dir;
        if (dir!=null)
            loadIdOnDisk();
    }

    /**
     * @return true if {@link AbstractLazyLoadRunMap#AbstractLazyLoadRunMap} was called with a non-null param, or {@link RunMap#load(Job, RunMap.Constructor)} was called
     */
    @Restricted(NoExternalUse.class)
    public final boolean baseDirInitialized() {
        return dir != null;
    }

    /**
     * Let go of all the loaded references.
     *
     * This is a bit more sophisticated version of forcing GC.
     * Primarily for debugging and testing lazy loading behaviour.
     * @since 1.507
     */
    public void purgeCache() {
        index = new Index();
        loadIdOnDisk();
    }

    private void loadIdOnDisk() {
        String[] buildDirs = dir.list(createDirectoryFilter());
        if (buildDirs==null) {
            // the job may have just been created
            buildDirs=EMPTY_STRING_ARRAY;
        }
        // wrap into ArrayList to enable mutation
        Arrays.sort(buildDirs);
        idOnDisk = new SortedList<String>(new ArrayList<String>(Arrays.asList(buildDirs)));

        // TODO: should we check that shortcuts is a symlink?
        String[] shortcuts = dir.list();
        if (shortcuts==null)    shortcuts=EMPTY_STRING_ARRAY;
        SortedIntList list = new SortedIntList(shortcuts.length/2);
        for (String s : shortcuts) {
            try {
                list.add(Integer.parseInt(s));
            } catch (NumberFormatException e) {
                // this isn't a shortcut
            }
        }
        list.sort();
        numberOnDisk = list;
    }

    public Comparator<? super Integer> comparator() {
        return COMPARATOR;
    }

    /**
     * If we have non-zero R in memory, we can return false right away.
     * If we have zero R in memory, try loading one and see if we can find something.
     */
    @Override
    public boolean isEmpty() {
        return index.byId.isEmpty() && search(Integer.MAX_VALUE, DESC)==null;
    }

    @Override
    public Set<Entry<Integer, R>> entrySet() {
        assert baseDirInitialized();
        return Collections.unmodifiableSet(new BuildReferenceMapAdapter<R>(this,all()).entrySet());
    }

    /**
     * Returns a read-only view of records that has already been loaded.
     */
    public SortedMap<Integer,R> getLoadedBuilds() {
        return Collections.unmodifiableSortedMap(new BuildReferenceMapAdapter<R>(this, index.byNumber));
    }

    /**
     * @param fromKey
     *      Biggest build number to be in the returned set.
     * @param toKey
     *      Smallest build number-1 to be in the returned set (-1 because this is exclusive)
     */
    public SortedMap<Integer, R> subMap(Integer fromKey, Integer toKey) {
        // TODO: if this method can produce a lazy map, that'd be wonderful
        // because due to the lack of floor/ceil/higher/lower kind of methods
        // to look up keys in SortedMap, various places of Jenkins rely on
        // subMap+firstKey/lastKey combo.

        R start = search(fromKey, DESC);
        if (start==null)    return EMPTY_SORTED_MAP;

        R end = search(toKey, ASC);
        if (end==null)      return EMPTY_SORTED_MAP;

        for (R i=start; i!=end; ) {
            i = search(getNumberOf(i)-1,DESC);
            assert i!=null;
        }

        return Collections.unmodifiableSortedMap(new BuildReferenceMapAdapter<R>(this, index.byNumber.subMap(fromKey, toKey)));
    }

    public SortedMap<Integer, R> headMap(Integer toKey) {
        return subMap(Integer.MAX_VALUE, toKey);
    }

    public SortedMap<Integer, R> tailMap(Integer fromKey) {
        return subMap(fromKey, Integer.MIN_VALUE);
    }

    public Integer firstKey() {
        R r = newestBuild();
        if (r==null)    throw new NoSuchElementException();
        return getNumberOf(r);
    }

    public Integer lastKey() {
        R r = oldestBuild();
        if (r==null)    throw new NoSuchElementException();
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
        return search(n,Direction.EXACT);
    }

    /**
     * Finds the build #M where M is nearby the given 'n'.
     *
     * <p>
     *
     *
     * @param n
     *      the index to start the search from
     * @param d
     *      defines what we mean by "nearby" above.
     *      If EXACT, find #N or return null.
     *      If ASC, finds the closest #M that satisfies M>=N.
     *      If DESC, finds the closest #M that satisfies M&lt;=N.
     */
    public @CheckForNull R search(final int n, final Direction d) {
        Entry<Integer, BuildReference<R>> c = index.ceilingEntry(n);
        if (c!=null && c.getKey()== n) {
            R r = c.getValue().get();
            if (r!=null)
            return r;    // found the exact #n
        }

        // at this point we know that we don't have #n loaded yet

        {// check numberOnDisk as a cache to see if we can find it there
            int npos = numberOnDisk.find(n);
            if (npos>=0) {// found exact match
                R r = load(numberOnDisk.get(npos), null);
                if (r!=null)
                    return r;
            }

            switch (d) {
            case ASC:
            case DESC:
                // didn't find the exact match, but what's the nearest ascending value in the cache?
                int neighbor = (d==ASC?HIGHER:LOWER).apply(npos);
                if (numberOnDisk.isInRange(neighbor)) {
                    R r = getByNumber(numberOnDisk.get(neighbor));
                    if (r!=null) {
                        // make sure that the cache is accurate by looking at the previous ID
                        // and it actually satisfies the constraint
                        int prev = (d==ASC?LOWER:HIGHER).apply(idOnDisk.find(getIdOf(r)));
                        if (idOnDisk.isInRange(prev)) {
                            R pr = getById(idOnDisk.get(prev));
                            // sign*sign is making sure that #pr and #r sandwiches #n.
                            if (pr!=null && signOfCompare(getNumberOf(pr),n)*signOfCompare(n,getNumberOf(r))>0)
                                return r;
                            else {
                                // cache is lying. there's something fishy.
                                // ignore the cache and do the slow search
                            }
                        } else {
                            // r is the build with youngest ID
                            return r;
                        }
                    } else {
                        // cache says we should have a build but we didn't.
                        // ignore the cache and do the slow search
                    }
                }
                break;
            case EXACT:
                // fall through
            }

            // didn't find it in the cache, but don't give up yet
            // maybe the cache just doesn't exist.
            // so fall back to the slow search
        }

        // capture the snapshot and work off with it since it can be overwritten by other threads
        SortedList<String> idOnDisk = this.idOnDisk;
        boolean clonedIdOnDisk = false; // if we modify idOnDisk we need to write it back. this flag is set to true when we overwrit idOnDisk local var

        // slow path: we have to find the build from idOnDisk by guessing ID of the build.
        // first, narrow down the candidate IDs to try by using two known number-to-ID mapping
        if (idOnDisk.isEmpty())     return null;

        Entry<Integer, BuildReference<R>> f = index.floorEntry(n);

        // if bound is null, use a sentinel value
        String cid = c==null ? "\u0000"  : c.getValue().id;
        String fid = f==null ? "\uFFFF" : f.getValue().id;
        // at this point, #n must be in (cid,fid)

        // We know that the build we are looking for exists in [lo,hi)  --- it's "hi)" and not "hi]" because we do +1.
        // we will narrow this down via binary search
        final int initialSize = idOnDisk.size();
        int lo = idOnDisk.higher(cid);
        int hi = idOnDisk.lower(fid)+1;

        final int initialLo = lo, initialHi = hi;

        if (!(0<=lo && lo<=hi && hi<=idOnDisk.size())) {
            // assertion error, but we are so far unable to get to the bottom of this bug.
            // but don't let this kill the loading the hard way
            String msg = String.format(
                    "JENKINS-15652 Assertion error #1: failing to load %s #%d %s: lo=%d,hi=%d,size=%d,size2=%d",
                    dir, n, d, lo, hi, idOnDisk.size(), initialSize);
            LOGGER.log(Level.WARNING, msg);
            return null;
        }

        while (lo<hi) {
            final int pivot = (lo+hi)/2;
            if (!(0<=lo && lo<=pivot && pivot<hi && hi<=idOnDisk.size())) {
                // assertion error, but we are so far unable to get to the bottom of this bug.
                // but don't let this kill the loading the hard way
                String msg = String.format(
                        "JENKINS-15652 Assertion error #2: failing to load %s #%d %s: lo=%d,hi=%d,pivot=%d,size=%d (initial:lo=%d,hi=%d,size=%d)",
                        dir, n, d, lo, hi, pivot, idOnDisk.size(), initialLo, initialHi, initialSize);
                LOGGER.log(Level.WARNING, msg);
                return null;
            }
            R r = load(idOnDisk.get(pivot), null);
            if (r==null) {
                // this ID isn't valid. get rid of that and retry pivot
                hi--;
                if (!clonedIdOnDisk) {// if we are making an edit, we need to own a copy
                    idOnDisk = new SortedList<String>(idOnDisk);
                    clonedIdOnDisk = true;
                }
                idOnDisk.remove(pivot);
                continue;
            }

            int found = getNumberOf(r);
            if (found==n)
                return r;   // exact match

            if (found<n)    lo = pivot+1;   // the pivot was too small. look in the upper half
            else            hi = pivot;     // the pivot was too big. look in the lower half
        }

        if (clonedIdOnDisk)
            this.idOnDisk = idOnDisk;   // feedback the modified result atomically

        assert lo==hi;
        // didn't find the exact match
        // both lo and hi point to the insertion point on idOnDisk
        switch (d) {
        case ASC:
            if (hi==idOnDisk.size())    return null;
            return getById(idOnDisk.get(hi));
        case DESC:
            if (lo<=0)                 return null;
            if (lo-1>=idOnDisk.size()) {
                // assertion error, but we are so far unable to get to the bottom of this bug.
                // but don't let this kill the loading the hard way
                LOGGER.log(Level.WARNING, String.format(
                        "JENKINS-15652 Assertion error #3: failing to load %s #%d %s: lo=%d,hi=%d,size=%d (initial:lo=%d,hi=%d,size=%d)",
                        dir, n,d,lo,hi,idOnDisk.size(), initialLo,initialHi,initialSize));
                return null;
            }
            return getById(idOnDisk.get(lo-1));
        case EXACT:
            if (hi<=0)                 return null;
            R r = load(idOnDisk.get(hi-1), null);
            if (r==null)               return null;

            int found = getNumberOf(r);
            if (found==n)
                return r;   // exact match
            return null;
        default:
            throw new AssertionError();
        }
    }

    /**
     * sign of (a-b).
     */
    private static int signOfCompare(int a, int b) {
        if (a>b)    return 1;
        if (a<b)    return -1;
        return 0;
    }

    public R getById(String id) {
        Index snapshot = index;
        if (snapshot.byId.containsKey(id)) {
            BuildReference<R> ref = snapshot.byId.get(id);
            if (ref==null)      return null;    // known failure
            R v = unwrap(ref);
            if (v!=null)        return v;       // already in memory
            // otherwise fall through to load
        }
        return load(id,null);
    }

    public R getByNumber(int n) {
        return search(n,Direction.EXACT);
    }

    public R put(R value) {
        return _put(value);
    }

    protected R _put(R value) {
        return put(getNumberOf(value),value);
    }

    @Override
    public synchronized R put(Integer key, R r) {
        String id = getIdOf(r);
        int n = getNumberOf(r);

        Index copy = copy();
        BuildReference<R> ref = createReference(r);
        BuildReference<R> old = copy.byId.put(id,ref);
        copy.byNumber.put(n,ref);
        index = copy;

        /*
            search relies on the fact that every object added via
            put() method be available in the xyzOnDisk index, so I'm adding them here
            however, this is awfully inefficient. I wonder if there's any better way to do this?
         */
        if (!idOnDisk.contains(id)) {
            ArrayList<String> a = new ArrayList<String>(idOnDisk);
            a.add(id);
            Collections.sort(a);
            idOnDisk = new SortedList<String>(a);
        }

        if (!numberOnDisk.contains(n)) {
            SortedIntList a = new SortedIntList(numberOnDisk);
            a.add(n);
            a.sort();
            numberOnDisk = a;
        }

        return unwrap(old);
    }

    private R unwrap(Reference<R> ref) {
        return ref!=null ? ref.get() : null;
    }

    @Override
    public synchronized void putAll(Map<? extends Integer,? extends R> rhs) {
        Index copy = copy();
        for (R r : rhs.values()) {
            String id = getIdOf(r);
            BuildReference<R> ref = createReference(r);
            copy.byId.put(id,ref);
            copy.byNumber.put(getNumberOf(r),ref);
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
    private TreeMap<Integer,BuildReference<R>> all() {
        if (!fullyLoaded) {
            synchronized (this) {
                if (!fullyLoaded) {
                    Index copy = copy();
                    for (String id : idOnDisk) {
                        if (!copy.byId.containsKey(id))
                            load(id,copy);
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
     * Tries to load the record #N by using the shortcut.
     * 
     * @return null if the data failed to load.
     */
    protected R load(int n, Index editInPlace) {
        R r = null;
        File shortcut = new File(dir,String.valueOf(n));
        if (shortcut.isDirectory()) {
            synchronized (this) {
                r = load(shortcut,editInPlace);

                // make sure what we actually loaded is #n,
                // because the shortcuts can lie.
                if (r!=null && getNumberOf(r)!=n)
                    r = null;

                if (r==null) {
                    // if failed to locate, record that fact
                    SortedIntList update = new SortedIntList(numberOnDisk);
                    update.removeValue(n);
                    numberOnDisk = update;
                }
            }
        }
        return r;
    }


    protected R load(String id, Index editInPlace) {
        assert dir != null;
        R v = load(new File(dir, id), editInPlace);
        if (v==null && editInPlace!=null) {
            // remember the failure.
            // if editInPlace==null, we can create a new copy for this, but not sure if it's worth doing,
            // given that we also update idOnDisk anyway.
            editInPlace.byId.put(id,null);
        }
        return v;
    }

    /**
     * @param editInPlace
     *      If non-null, update this data structure.
     *      Otherwise do a copy-on-write of {@link #index}
     */
    protected synchronized R load(File dataDir, Index editInPlace) {
        try {
            R r = retrieve(dataDir);
            if (r==null)    return null;

            Index copy = editInPlace!=null ? editInPlace : new Index(index);

            String id = getIdOf(r);
            BuildReference<R> ref = createReference(r);
            copy.byId.put(id,ref);
            copy.byNumber.put(getNumberOf(r),ref);

            if (editInPlace==null)  index = copy;

            return r;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load "+dataDir,e);
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
    protected abstract String getIdOf(R r);

    /**
     * Allow subtype to capture a reference.
     */
    protected BuildReference<R> createReference(R r) {
        return new BuildReference<R>(getIdOf(r),r);
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
        copy.byNumber.remove(getNumberOf(run));
        BuildReference<R> old = copy.byId.remove(getIdOf(run));
        this.index = copy;

        return unwrap(old)!=null;
    }

    /**
     * Replaces all the current loaded Rs with the given ones.
     */
    public synchronized void reset(TreeMap<Integer,R> builds) {
        Index index = new Index();
        for (R r : builds.values()) {
            String id = getIdOf(r);
            BuildReference<R> ref = createReference(r);
            index.byId.put(id,ref);
            index.byNumber.put(getNumberOf(r),ref);
        }

        this.index = index;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object o) {
        return o==this;
    }

    /**
     * Lists the actual data directory
     */
    protected abstract FilenameFilter createDirectoryFilter();

    private static final Comparator<Comparable> COMPARATOR = new Comparator<Comparable>() {
        public int compare(Comparable o1, Comparable o2) {
            return -o1.compareTo(o2);
        }
    };
    
    public enum Direction {
        ASC, DESC, EXACT
    }

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private static final SortedMap EMPTY_SORTED_MAP = Collections.unmodifiableSortedMap(new TreeMap());

    static final Logger LOGGER = Logger.getLogger(AbstractLazyLoadRunMap.class.getName());
}
