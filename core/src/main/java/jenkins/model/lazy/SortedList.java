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

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link List} decorator that provides a number of binary-search related methods
 * by assuming that the array is sorted in the ascending order.
 *
 * @author Kohsuke Kawaguchi
 */
class SortedList<T extends Comparable<T>> extends AbstractList<T> {
    private List<T> data;

    public SortedList(List<T> data) {
        this.data = new ArrayList<T>(data);
        assert isSorted();
    }

    /**
     * Binary search to find the position of the given string.
     *
     * @return
     *      -(insertionPoint+1) if the exact string isn't found.
     *      That is, -1 means the probe would be inserted at the very beginning.
     */
    public int find(T probe) {
        return Collections.binarySearch(data, probe);
    }

    @Override
    public boolean contains(Object o) {
        return find((T)o)>=0;
    }

    public T get(int idx) {
        return data.get(idx);
    }

    @Override
    public int size() {
        return data.size();
    }

    @Override
    public T remove(int index) {
        return data.remove(index);
    }

    @Override
    public boolean remove(Object o) {
        return data.remove(o);
    }

    /**
     * Finds the index of the entry lower than v.
     *
     * @return
     *      return value will be in the [-1,size) range
     */
    public int lower(T v) {
        return Boundary.LOWER.apply(find(v));
    }

    /**
     * Finds the index of the entry greater than v.
     *
     * @return
     *      return value will be in the [0,size] range
     */
    public int higher(T v) {
        return Boundary.HIGHER.apply(find(v));
    }

    /**
     * Finds the index of the entry lower or equal to v.
     *
     * @return
     *      return value will be in the [-1,size) range
     */
    public int floor(T v) {
        return Boundary.FLOOR.apply(find(v));
    }

    /**
     * Finds the index of the entry greater or equal to v.
     *
     * @return
     *      return value will be in the [0,size] range
     */
    public int ceil(T v) {
        return Boundary.CEIL.apply(find(v));
    }

    public boolean isInRange(int idx) {
        return 0<=idx && idx<data.size();
    }

    private boolean isSorted() {
        for (int i = 1; i < data.size(); i++) {
            if (data.get(i).compareTo(data.get(i - 1)) < 0) {
                return false;
            }
        }
        return true;
    }

}
