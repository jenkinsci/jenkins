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

import static org.junit.Assert.*;

import hudson.util.CopyOnWriteMap.Hash;
import org.junit.Test;

import java.util.Random;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;
import java.util.Map.Entry;

/**
 * @author Kohsuke Kawaguchi
 */
public class ConsistentHashTest {
    /**
     * Just some random tests to ensure that we have no silly NPE or that kind of error.
     */
    @Test
    public void basic() {
        ConsistentHash<String> hash = new ConsistentHash<String>();
        hash.add("data1");
        hash.add("data2");
        hash.add("data3");

        System.out.println(hash.lookup(0));

        // there's one in 2^32 chance that this test fails, but these two query points are
        // only off by one.
        String x = hash.lookup(Integer.MIN_VALUE);
        String y = hash.lookup(Integer.MAX_VALUE);
        assertEquals(x,y);

        // list them up
        Iterator<String> itr = hash.list(Integer.MIN_VALUE).iterator();
        Set<String> all = new HashSet<String>();
        String z = itr.next();
        all.add(z);
        assertEquals(z,x);
        all.add(itr.next());
        all.add(itr.next());
        assertTrue(!itr.hasNext());
        assertEquals(3,all.size());
    }

    /**
     * Uneven distribution should result in uneven mapping.
     */
    @Test
    public void unevenDistribution() {
        ConsistentHash<String> hash = new ConsistentHash<String>();
        hash.add("even",10);
        hash.add("odd",100);

        Random r = new Random(0);
        int even=0,odd=0;
        for(int i=0; i<1000; i++) {
            String v = hash.lookup(r.nextInt());
            if(v.equals("even"))    even++;
            else                    odd++;
        }

        // again, there's a small chance tha this test fails.
        System.out.printf("%d/%d\n",even,odd);
        assertTrue(even*8<odd);
    }

    /**
     * Removal shouldn't affect existing nodes
     */
    @Test
    public void removal() {
        ConsistentHash<Integer> hash = new ConsistentHash<Integer>();
        for( int i=0; i<10; i++ )
            hash.add(i);

        // what was the mapping before the mutation?
        Map<Integer,Integer> before = new HashMap<Integer, Integer>();
        Random r = new Random(0);
        for(int i=0; i<1000; i++) {
            int q = r.nextInt();
            before.put(q,hash.lookup(q));
        }

        // remove a node
        hash.remove(0);

        // verify that the mapping remains consistent
        for (Entry<Integer,Integer> e : before.entrySet()) {
            int m = hash.lookup(e.getKey());
            assertTrue(e.getValue()==0 || e.getValue()==m);
        }
    }

    @Test
    public void emptyBehavior() {
        ConsistentHash<String> hash = new ConsistentHash<String>();
        assertFalse(hash.list(0).iterator().hasNext());
        assertNull(hash.lookup(0));
        assertNull(hash.lookup(999));
    }

    /**
     * This test doesn't fail but it's written to measure the performance of the consistent hash function with large data set.
     */
    @Test
    public void speed() {
        Map<String,Integer> data = new Hash<String, Integer>();
        for (int i = 0; i < 1000; i++)
            data.put("node" + i,100);
        data.put("tail",100);

        long start = System.currentTimeMillis();
        for (int j=0; j<10; j++) {
            ConsistentHash<String> b = new ConsistentHash<String>();
            b.addAll(data);
        }

        System.out.println(System.currentTimeMillis()-start);
    }
}
