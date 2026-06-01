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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.util.Iterators.CountingPredicate;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

/**
 * @author Kohsuke Kawaguchi
 */
class IteratorsTest {

    @Test
    void reverseSequence() {
        List<Integer> lst = Iterators.reverseSequence(1, 4);
        assertEquals(3, (int) lst.get(0));
        assertEquals(2, (int) lst.get(1));
        assertEquals(1, (int) lst.get(2));
        assertEquals(3, lst.size());
    }

    @Test
    void sequence() {
        List<Integer> lst = Iterators.sequence(1, 4);
        assertEquals(1, (int) lst.get(0));
        assertEquals(2, (int) lst.get(1));
        assertEquals(3, (int) lst.get(2));
        assertEquals(3, lst.size());
    }

    @Test
    void wrap() {
        List<Integer> lst = Iterators.sequence(1, 4);
        Iterable<Integer> wrapped = Iterators.wrap(lst);
        assertThat(wrapped, not(instanceOf(List.class)));
        Iterator<Integer> iter = wrapped.iterator();
        assertTrue(iter.hasNext());
        assertEquals(1, (int) iter.next());
        assertTrue(iter.hasNext());
        assertEquals(2, (int) iter.next());
        assertTrue(iter.hasNext());
        assertEquals(3, (int) iter.next());
        assertFalse(iter.hasNext());
    }

    @Test
    void limit() {
        assertEquals("[0]", com.google.common.collect.Iterators.toString(Iterators.limit(asList(0, 1, 2, 3, 4).iterator(), EVEN)));
        assertEquals("[]", com.google.common.collect.Iterators.toString(Iterators.limit(asList(1, 2, 4, 6).iterator(), EVEN)));
    }

    public static final CountingPredicate<Integer> EVEN = (index, input) -> input % 2 == 0;

    @Issue("JENKINS-51779")
    @Test
    void skip() {
        List<Integer> lst = Iterators.sequence(1, 4);
        Iterator<Integer> it = lst.iterator();
        Iterators.skip(it, 0);
        assertEquals("[1, 2, 3]", com.google.common.collect.Iterators.toString(it));
        it = lst.iterator();
        Iterators.skip(it, 1);
        assertEquals("[2, 3]", com.google.common.collect.Iterators.toString(it));
        it = lst.iterator();
        Iterators.skip(it, 2);
        assertEquals("[3]", com.google.common.collect.Iterators.toString(it));
        it = lst.iterator();
        Iterators.skip(it, 3);
        assertEquals("[]", com.google.common.collect.Iterators.toString(it));
        it = lst.iterator();
        Iterators.skip(it, 4);
        assertEquals("[]", com.google.common.collect.Iterators.toString(it));
    }

}
