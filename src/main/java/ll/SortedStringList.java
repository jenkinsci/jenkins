package ll;

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;

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

    public boolean isInRange(int idx) {
        return 0<=idx && idx<data.size();
    }
}
