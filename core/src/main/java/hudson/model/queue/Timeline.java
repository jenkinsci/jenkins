/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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
package hudson.model.queue;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static java.lang.Math.*;

/**
* Represents a mutable q(t), a discrete value that changes over the time.
*
* <p>
* Internally represented by a set of ranges and the value of q(t) in that range,
* as a map from "starting time of a range" to "value of q(t)".
*/
final class Timeline {
    private final TreeMap<Long, int[]> data = new TreeMap<Long, int[]>();

    /**
     * Obtains q(t) for the given t.
     */
    private int at(long t) {
        SortedMap<Long, int[]> head = data.headMap(t);
        if (head.isEmpty()) return 0;
        return data.get(head.lastKey())[0];
    }

    /**
     * Splits the range set at the given timestamp (if it hasn't been split yet)
     */
    private void splitAt(long t) {
        if (data.containsKey(t)) return; // already split at this timestamp

        SortedMap<Long, int[]> head = data.headMap(t);

        int v = head.isEmpty() ? 0 : data.get(head.lastKey())[0];
        data.put(t, new int[]{v});
    }

    /**
     * increases q(t) by n for t in [start,end).
     *
     * @return peak value of q(t) in this range as a result of addition.
     */
    int insert(long start, long end, int n) {
        splitAt(start);
        splitAt(end);

        int peak = 0;
        for (Map.Entry<Long, int[]> e : data.tailMap(start).headMap(end).entrySet()) {
            peak = max(peak, e.getValue()[0] += n);
        }
        return peak;
    }
}
