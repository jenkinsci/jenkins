package ll;

import java.util.AbstractList;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

/**
 * @author Kohsuke Kawaguchi
 */
public class SortedStringList extends AbstractList<String> {
    private List<String> data;

    public SortedStringList(List<String> data) {
        this.data = data;
    }

    /**
     * Binary search to find the position of the given string.
     *
     * @return
     *      -(insertionPoint+1) if the exact string isn't found.
     *      That is, -1 means the probe would be inserted at the very beginning.
     */
    public int find(String probe) {
        return Collections.binarySearch(data, probe);
    }
    
    public String get(int idx) {
        return data.get(idx);
    }

    @Override
    public int size() {
        return data.size();
    }

    @Override
    public String remove(int index) {
        return data.remove(index);
    }

    @Override
    public boolean remove(Object o) {
        return data.remove(o);
    }

    /**
     * Finds the index of the entry lower than v.
     */
    public int lower(String v) {
        return Boundary.LOWER.apply(find(v));
    }

    /**
     * Finds the index of the entry greater than v.
     */
    public int higher(String v) {
        return Boundary.HIGHER.apply(find(v));
    }

    /**
     * Finds the index of the entry lower or equal to v.
     */
    public int floor(String v) {
        return Boundary.FLOOR.apply(find(v));
    }

    /**
     * Finds the index of the entry greater or equal to v.
     */
    public int ceil(String v) {
        return Boundary.CEIL.apply(find(v));
    }

    /**
     * Smarter {@link #subList(int, int)} that gracefully handles index that's out of range.
     */
    public SortedStringList safeSubList(int from, int to) {
        return new SortedStringList(subList(inside(from),inside(to)));
    }

    /**
     * Brings the index into the legal range.
     */
    private int inside(int idx) {
        idx = Math.max(0,idx);
        idx = Math.min(idx,size());
        return idx;
    }

    /**
     * Finds the middle value used for binary search pivot.
     */
    public Entry<Integer,String> middle() {
        assert !data.isEmpty();
        int idx = data.size()/2;
        return new SimpleEntry<Integer, String>(idx,data.get(idx));
    }

    /**
     * Overloaded version of {@link #remove(int)} that takes a pointer instead of the index.
     */
    public void remove(Entry<Integer,String> ptr) {
        remove(ptr.getKey().intValue());
    }

    /**
     * Returns the sub list that contains (pivot,end]
     */
    public SortedStringList upperHalf() {
        int sz = data.size();
        return safeSubList(sz/2+1,sz);
    }

    public SortedStringList lowerHalf() {
        int sz = data.size();
        return safeSubList(0,sz/2);
    }
}
