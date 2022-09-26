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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author Kohsuke Kawaguchi
 */
public class ConsistentHashTest {
    /**
     * Just some random tests to ensure that we have no silly NPE or that kind of error.
     */
    @Test
    public void basic() {
        ConsistentHash<String> hash = new ConsistentHash<>();
        hash.add("data1");
        hash.add("data2");
        hash.add("data3");

        // In a pure random scenario, there's one in 2^32 chance that this test fails
        // but as we control the input and the hash function is deterministic, there is no risk of failure
        String x = hash.lookup(Integer.MIN_VALUE);
        String y = hash.lookup(Integer.MAX_VALUE);
        // both values are data3
        assertEquals(x, y);

        // list them up
        Iterator<String> itr = hash.list(Integer.MIN_VALUE).iterator();
        Set<String> all = new HashSet<>();
        String z = itr.next();
        all.add(z);
        assertEquals(z, x);
        all.add(itr.next());
        all.add(itr.next());
        assertFalse(itr.hasNext());
        assertEquals(3, all.size());
    }

    /**
     * Uneven distribution should result in uneven mapping.
     */
    @Test
    public void unevenDistribution() {
        ConsistentHash<String> hash = new ConsistentHash<>();
        hash.add("Even", 10);
        hash.add("Odd", 100);

        Random r = new Random(0);
        int even = 0;
        int odd = 0;
        for (int i = 0; i < 1000; i++) {
            String v = hash.lookup(r.nextInt());
            if (v.equals("Even")) {
                even++;
            } else {
                odd++;
            }
        }

        System.out.printf("%d/%d%n", even, odd);
        assertTrue(even * 8 < odd);
    }

    /**
     * Removal shouldn't affect existing nodes
     */
    @Test
    public void removal() {
        ConsistentHash<Integer> hash = new ConsistentHash<>();
        for (int i = 0; i < 10; i++) {
            hash.add(i);
        }

        // what was the mapping before the mutation?
        Map<Integer, Integer> before = new HashMap<>();
        Random r = new Random(0);
        for (int i = 0; i < 1000; i++) {
            int q = r.nextInt();
            before.put(q, hash.lookup(q));
        }

        // remove a node
        hash.remove(0);

        // verify that the mapping remains consistent
        for (Map.Entry<Integer, Integer> e : before.entrySet()) {
            int m = hash.lookup(e.getKey());
            assertTrue(e.getValue() == 0 || e.getValue() == m);
        }
    }

    @Test
    public void emptyBehavior() {
        ConsistentHash<String> hash = new ConsistentHash<>();
        assertEquals(0, hash.countAllPoints());
        assertFalse(hash.list(0).iterator().hasNext());
        assertNull(hash.lookup(0));
        assertNull(hash.lookup(999));
    }

    @Test
    public void countAllPoints() {
        ConsistentHash<String> hash = new ConsistentHash<>();
        assertEquals(0, hash.countAllPoints());
        hash.add("foo", 10);
        assertEquals(10, hash.countAllPoints());
        hash.add("bar", 5);
        assertEquals(15, hash.countAllPoints());
        hash.remove("foo");
        assertEquals(5, hash.countAllPoints());
    }

    @Test
    public void defaultReplicationIsOneHundred() {
        ConsistentHash<String> hash = new ConsistentHash<>();
        assertEquals(0, hash.countAllPoints());
        hash.add("foo");
        assertEquals(100, hash.countAllPoints());
    }

    @Test
    public void setCustomDefaultReplication() {
        ConsistentHash<String> hash = new ConsistentHash<>((ConsistentHash.Hash<String>) ConsistentHash.DEFAULT_HASH, 7);
        assertEquals(0, hash.countAllPoints());
        hash.add("foo");
        assertEquals(7, hash.countAllPoints());
    }

    @Test
    public void usesCustomHash() {
        final RuntimeException exception = new RuntimeException();
        ConsistentHash.Hash<String> hashFunction = str -> {
            throw exception;
        };

        ConsistentHash<String> hash = new ConsistentHash<>(hashFunction);
        final RuntimeException e = assertThrows(RuntimeException.class, () -> hash.add("foo"));
        assertSame(exception, e);
    }

    /**
     * This test doesn't fail but it's written to measure the performance of the consistent hash function with large data set.
     */
    @Test
    @Ignore("Helper test for performance, no assertion")
    public void speed() {
        Map<String, Integer> data = new CopyOnWriteMap.Hash<>();
        for (int i = 0; i < 1000; i++) {
            data.put("node" + i, 100);
        }
        data.put("tail", 100);

        long start = System.currentTimeMillis();
        for (int j = 0; j < 10; j++) {
            ConsistentHash<String> b = new ConsistentHash<>();
            b.addAll(data);
        }

        System.out.println(System.currentTimeMillis() - start);
    }
}
