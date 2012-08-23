import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import static Attempt2.Direction.*;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class Attempt2<R> extends AbstractMap<Integer,R> implements SortedMap<Integer,R> {

    private boolean fullyLoaded;
    // copy on write map that stores what's already byNumber
    private volatile TreeMap<Integer,R> byNumber = new TreeMap<Integer,R>();

    /**
     * Stores the build ID to build number for builds that we already know
     */
    private final TreeMap<String,R> byId = new TreeMap<String,R>();

    /**
     * IDs found as directories, in the ascending order.
     */
    private List<Integer> idOnDisk;

    /**
     * Number shortcuts found on disk, in the ascending order.
     */
    private List<String> numberOnDisk;

    private final File dir;
    
    private final Object loadLock = this;

    public Attempt2(File dir) {
        this.dir = dir;
    }

    public Comparator<? super Integer> comparator() {
        return COMPARATOR;
    }

    @Override
    public Set<Entry<Integer, R>> entrySet() {
        return all().entrySet();
    }

    public Integer firstKey() {
        R r = search(Integer.MIN_VALUE, Direction.ASC);
        if (r==null)    throw new NoSuchElementException();
        return getNumberOf(r);
    }

    @Override
    public R get(Object key) {
        if (key instanceof Integer) {
            int n = (Integer) key;
            if (byNumber.containsKey(n))
                return byNumber.get(n);

            return search(n, EXACT);
        }
        return super.get(key);
    }

    protected R search(int n, Direction d) {
        while (true) {
            Entry<Integer, R> f = byNumber.floorEntry(n);
            if (f!=null && f.getKey()== n)  return f.getValue();    // found the exact #n

            // at this point we know that we don't have #n
            // but maybe we have disk cache?
            if (!idOnDisk.isEmpty()) {
                int pos = Collections.binarySearch(idOnDisk, n);
                if (pos>=0) {
                    R r = load(n, true);
                    if (d==EXACT || r!=null)    return r;

                } else {
                    // non-exact match
                    n = d.candidate(pos);
                }
            }

            Entry<Integer, R> c = byNumber.ceilingEntry(n);

            // bisect the expected record 'start' by two bounds
        }
    }

    protected int toIdIndex(Entry<Integer, R> f, int fallback) {
        if (f==null)    return fallback;
        return getIdOf(f.getValue());
    }


    /**
     * Loads all the build records to fully populate the map. Last resort.
     */
    private TreeMap<Integer,R> all() {
        if (!fullyLoaded) {
            synchronized (this) {
                if (!fullyLoaded) {
                    String[] buildDirs = dir.list(createDirectoryFilter());
                    if (buildDirs!=null) {
                        copy();
                        for (String id : buildDirs) {
                            if (!byId.containsKey(id))
                                load(id);
                        }
                    }
                }
            }
        }
        return byNumber;
    }

    /**
     * Creates a duplicate for the COW data structure in preparation for mutation.
     */
    private void copy() {
        byNumber = new TreeMap<Integer,R>(byNumber);
    }

    /**
     * Tries to load the record #N by using the shortcut.
     * 
     * @return null if the data failed to load.
     */
    protected R load(int n, boolean copy) {
        R r = null;
        File shortcut = new File(dir,String.valueOf(n));
        if (shortcut.isDirectory()) {
            r = load(shortcut,copy);
            if (r==null) {
                // if failed to locate, record that fact
                if (copy)   copy();
                byNumber.put(n,null);
            }
        }
        return r;
    }
    
    protected R load(File dataDir, boolean copy) {
        try {
            R r = retrieve(dataDir);
            if (copy)   copy();
            byId.put(getIdOf(r),r);
            byNumber.put(getNumberOf(r),r);
            return r;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    protected abstract int getNumberOf(R r);
    protected abstract String getIdOf(R r);

    protected abstract R retrieve(File dir) throws IOException;

    /**
     * Lists the actual data directory
     */
    protected abstract FilenameFilter createDirectoryFilter();

    public static final Comparator<Comparable> COMPARATOR = new Comparator<Comparable>() {
        public int compare(Comparable o1, Comparable o2) {
            return -o1.compareTo(o2);
        }
    };
    
    enum Direction {
        UP(0), DOWN(-1), EXACT(0/*invalid*/);
        
        private final int binarySearchTrimOffset;

        Direction(int binarySearchTrimOffset) {
            this.binarySearchTrimOffset = binarySearchTrimOffset;
        }

        /**
         * Given the negative result from {@link Collections#binarySearch(List, Object)}
         * that indicates no-exact match, determine the position of the element we'll load
         * instead that matches with the search order.
         */
        private int candidate(int n) {
            return -n-1+binarySearchTrimOffset;
        }
    };
}
