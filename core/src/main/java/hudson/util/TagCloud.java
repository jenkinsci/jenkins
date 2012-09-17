/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson.util;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an order-preserving tag cloud (http://en.wikipedia.org/wiki/Tag_cloud)
 * where each keyword gets a weight and displayed according to their weight.
 *
 * TODO: define a view on its own.
 * 
 * @since 1.322
 */
public class TagCloud<T> extends AbstractList<TagCloud<T>.Entry> {
    public final class Entry {
        public final T item;
        public final float weight;

        public Entry(T item, float weight) {
            this.item = item;
            this.weight = weight;
        }

        public float scale() {
            // TODO: it's not obvious if linear scaling is the right approach or not.  
            return weight*9/max;
        }

        public String getClassName() {
            return "tag"+((int)scale());
        }
    }

    public interface WeightFunction<T> {
        float weight(T item);
    }

    private final List<Entry> entries = new ArrayList<Entry>();
    private float max = 1;

    /**
     * Creates a tag cloud.
     *
     * @param f
     *      Assigns weight to each item.
     */
    public TagCloud(Iterable<? extends T> inputs, WeightFunction<T> f) {
        for (T input : inputs) {
            float w = Math.abs(f.weight(input));
            max = Math.max(w,max);
            entries.add(new Entry(input, w));
        }
    }

    public Entry get(int index) {
        return entries.get(index);
    }

    public int size() {
        return entries.size();
    }
}
