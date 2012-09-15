package ll;

import java.util.AbstractList;
import java.util.Arrays;

/**
 * @author Kohsuke Kawaguchi
 */
public class SortedIntList extends AbstractList<Integer> {
    private int[] data;
    private int size;

    public SortedIntList(int capacity) {
        this.data = new int[capacity];
        this.size = 0;
    }

    /**
     * Internal copy constructor.
     */
    public SortedIntList(SortedIntList rhs) {
        this.data = Arrays.copyOf(rhs.data,rhs.data.length);
        this.size = rhs.size;
    }

    /**
     * Binary search to find the position of the given string.
     *
     * @return
     *      -(insertionPoint+1) if the exact string isn't found.
     *      That is, -1 means the probe would be inserted at the very beginning.
     */
    public int find(int probe) {
        return Arrays.binarySearch(data, 0, size, probe);
    }

    @Override
    public Integer get(int index) {
        if (size<=index)    throw new IndexOutOfBoundsException();
        return data[index];
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean add(Integer i) {
        return add(i.intValue());
    }

    public boolean add(int i) {
        ensureCapacity(size+1);
        data[size++] = i;
        return true;
    }

    private void ensureCapacity(int i) {
        if (data.length<i) {
            int[] r = new int[Math.max(data.length*2,i)];
            System.arraycopy(data,0,r,0,size);
            data = r;
        }
    }

    /**
     * Finds the index of the entry lower than v.
     */
    public int lower(int v) {
        return Boundary.LOWER.apply(find(v));
    }

    /**
     * Finds the index of the entry greater than v.
     */
    public int higher(int v) {
        return Boundary.HIGHER.apply(find(v));
    }

    /**
     * Finds the index of the entry lower or equal to v.
     */
    public int floor(int v) {
        return Boundary.FLOOR.apply(find(v));
    }

    /**
     * Finds the index of the entry greater or equal to v.
     */
    public int ceil(int v) {
        return Boundary.CEIL.apply(find(v));
    }

    public boolean isInRange(int idx) {
        return 0<=idx && idx<size;
    }

    public void sort() {
        Arrays.sort(data,0,size);
    }

    public void removeValue(int n) {
        int idx = find(n);
        if (idx<0)  return;
        System.arraycopy(data,idx+1,data,idx,size-(idx+1));
        size--;
    }
}
