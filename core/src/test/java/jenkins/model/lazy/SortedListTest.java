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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * @author Kohsuke Kawaguchi
 */
class SortedListTest {
    private SortedList<String> l = new SortedList<>(new ArrayList<>(Arrays.asList("B", "D", "F")));

    @Test
    void testCeil() {
        assertEquals(0, l.ceil("A"));
        assertEquals(0, l.ceil("B"));
        assertEquals(1, l.ceil("C"));
        assertEquals(1, l.ceil("D"));
        assertEquals(2, l.ceil("E"));
        assertEquals(2, l.ceil("F"));
        assertEquals(3, l.ceil("G"));
    }

    @Test
    void testFloor() {
        assertEquals(-1, l.floor("A"));
        assertEquals(0, l.floor("B"));
        assertEquals(0, l.floor("C"));
        assertEquals(1, l.floor("D"));
        assertEquals(1, l.floor("E"));
        assertEquals(2, l.floor("F"));
        assertEquals(2, l.floor("G"));
    }

    @Test
    void testLower() {
        assertEquals(-1, l.lower("A"));
        assertEquals(-1, l.lower("B"));
        assertEquals(0, l.lower("C"));
        assertEquals(0, l.lower("D"));
        assertEquals(1, l.lower("E"));
        assertEquals(1, l.lower("F"));
        assertEquals(2, l.lower("G"));
    }

    @Test
    void testHigher() {
        assertEquals(0, l.higher("A"));
        assertEquals(1, l.higher("B"));
        assertEquals(1, l.higher("C"));
        assertEquals(2, l.higher("D"));
        assertEquals(2, l.higher("E"));
        assertEquals(3, l.higher("F"));
        assertEquals(3, l.higher("G"));
    }

    @Test
    void testRange() {
        assertTrue(l.isInRange(0));
        assertTrue(l.isInRange(1));
        assertTrue(l.isInRange(2));

        assertFalse(l.isInRange(-1));
        assertFalse(l.isInRange(3));
    }

    @Test
    void remove() {
        l.remove("nosuchthing");
        assertEquals(3, l.size());

        l.remove("B");
        assertEquals(2, l.size());
        assertEquals("D", l.get(0));
        assertEquals("F", l.get(1));
    }

    @Test
    void testClone() {
        final int originalSize = l.size();
        SortedList<String> l2 = new SortedList<>(l);
        assertEquals(originalSize, l2.size());
        assertEquals(originalSize, l.size());
        for (int i = 0; i < originalSize; i++) {
            assertEquals(l.get(i), l2.get(i));
        }
        l.remove(0);
        assertEquals(originalSize - 1, l.size());
        assertEquals(originalSize, l2.size());
        l2.remove(1);
        l2.remove(1);
        assertEquals(originalSize - 1, l.size());
        assertEquals(originalSize - 2, l2.size());
    }
}
