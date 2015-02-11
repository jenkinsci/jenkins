/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import static java.util.Arrays.asList;
import static org.junit.Assert.*;

import hudson.util.Iterators.CountingPredicate;

import java.util.Iterator;
import java.util.List;

import org.junit.Test;

/**
 * @author Kohsuke Kawaguchi
 */
public class IteratorsTest {

    @Test
    public void reverseSequence() {
        List<Integer> lst = Iterators.reverseSequence(1, 4);
        assertEquals(3,(int)lst.get(0));
        assertEquals(2,(int)lst.get(1));
        assertEquals(1,(int)lst.get(2));
        assertEquals(3,lst.size());
    }

    @Test
    public void sequence() {
        List<Integer> lst = Iterators.sequence(1,4);
        assertEquals(1,(int)lst.get(0));
        assertEquals(2,(int)lst.get(1));
        assertEquals(3,(int)lst.get(2));
        assertEquals(3, lst.size());
    }

    @Test
    public void wrap() {
        List<Integer> lst = Iterators.sequence(1,4);
        Iterable<Integer> wrapped = Iterators.wrap(lst);
        assertFalse(wrapped instanceof List);
        Iterator<Integer> iter = wrapped.iterator();
        assertTrue(iter.hasNext());
        assertEquals(1,(int)iter.next());
        assertTrue(iter.hasNext());
        assertEquals(2,(int)iter.next());
        assertTrue(iter.hasNext());
        assertEquals(3,(int)iter.next());
        assertFalse(iter.hasNext());
    }

    @Test
    public void limit() {
        assertEquals("[0]",com.google.common.collect.Iterators.toString(Iterators.limit(asList(0,1,2,3,4).iterator(), EVEN)));
        assertEquals("[]", com.google.common.collect.Iterators.toString(Iterators.limit(asList(1,2,4,6).iterator(), EVEN)));
    }

    public static final CountingPredicate<Integer> EVEN = new CountingPredicate<Integer>() {
        public boolean apply(int index, Integer input) {
            return input % 2 == 0;
        }
    };
}
