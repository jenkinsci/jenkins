/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly
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

package hudson.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Stephen Connolly
 */
class ResourceListTest {

    private Resource a1, a2, a3, a4, a;
    private Resource b1, b2, b3, b4, b;
    private Resource c1, c2, c3, c4, c;
    private Resource d, e, f;
    private int fWriteCount;
    private Random entropy;
    private ResourceList x;
    private ResourceList y;
    private ResourceList z;

    @BeforeEach
    void setUp() {
        entropy = new Random(0);
        a = new Resource("A" + entropy.nextLong());
        a1 = new Resource(a, "A" + entropy.nextLong());
        a2 = new Resource(a, "A" + entropy.nextLong());
        a3 = new Resource(a, "A" + entropy.nextLong());
        a4 = new Resource(a, "A" + entropy.nextLong());
        b = new Resource("B" + entropy.nextLong());
        b1 = new Resource(b, "B" + entropy.nextLong());
        b2 = new Resource(b, "B" + entropy.nextLong());
        b3 = new Resource(b, "B" + entropy.nextLong());
        b4 = new Resource(b, "B" + entropy.nextLong());
        c = new Resource(null, "C" + entropy.nextLong(), 3);
        c1 = new Resource(c, "C" + entropy.nextLong(), 3);
        c2 = new Resource(c, "C" + entropy.nextLong(), 3);
        c3 = new Resource(c, "C" + entropy.nextLong(), 3);
        c4 = new Resource(c, "C" + entropy.nextLong(), 3);
        d = new Resource("D" + entropy.nextLong());
        e = new Resource(null, "E" + entropy.nextLong());
        fWriteCount = 5 + entropy.nextInt(100);
        f = new Resource(null, "F" + entropy.nextLong(), 5);
        x = new ResourceList();
        y = new ResourceList();
        z = new ResourceList();
    }

    @Test
    void emptyLists() {
        z.r(a);
        ResourceList w = new ResourceList();
        w.w(a);
        assertFalse(x.isCollidingWith(y), "Empty vs Empty");
        assertFalse(y.isCollidingWith(x), "Empty vs Empty");
        assertFalse(x.isCollidingWith(z), "Empty vs Read");
        assertFalse(z.isCollidingWith(x), "Read vs Empty");
        assertFalse(x.isCollidingWith(w), "Empty vs Write");
        assertFalse(w.isCollidingWith(x), "Write vs Empty");
    }

    @Test
    void simpleR() {
        x.r(a);
        y.r(b);
        z.r(a);

        assertFalse(x.isCollidingWith(y), "Read-Read");
        assertFalse(y.isCollidingWith(x), "Read-Read");
        assertFalse(x.isCollidingWith(z), "Read-Read");
        assertFalse(z.isCollidingWith(x), "Read-Read");
        assertFalse(z.isCollidingWith(y), "Read-Read");
        assertFalse(y.isCollidingWith(z), "Read-Read");
    }

    @Test
    void simpleRW() {
        x.r(a);
        y.r(b);
        z.w(a);

        assertFalse(x.isCollidingWith(y), "Read-Read different resources");
        assertFalse(y.isCollidingWith(x), "Read-Read different resources");
        assertTrue(x.isCollidingWith(z), "Read-Write same resource");
        assertTrue(z.isCollidingWith(x), "Read-Write same resource");
        assertFalse(z.isCollidingWith(y), "Read-Write different resources");
        assertFalse(y.isCollidingWith(z), "Read-Write different resources");
    }

    @Test
    void simpleW() {
        x.w(a);
        y.w(b);
        z.w(a);

        assertFalse(x.isCollidingWith(y));
        assertFalse(y.isCollidingWith(x));
        assertTrue(x.isCollidingWith(z));
        assertTrue(z.isCollidingWith(x));
        assertFalse(z.isCollidingWith(y));
        assertFalse(y.isCollidingWith(z));

        ResourceList w = ResourceList.union(x, y);
        assertTrue(w.isCollidingWith(z));
        assertTrue(z.isCollidingWith(w));

        ResourceList v = new ResourceList();
        v.w(a1);
        assertTrue(w.isCollidingWith(v));
        assertTrue(z.isCollidingWith(w));
    }

    @Test
    void parentChildR() {
        x.r(a1);
        x.r(a2);
        y.r(a3);
        y.r(a4);
        z.r(a);
        assertFalse(x.isCollidingWith(y), "Reads should never conflict");
        assertFalse(y.isCollidingWith(x), "Reads should never conflict");
        assertFalse(x.isCollidingWith(z), "Reads should never conflict");
        assertFalse(z.isCollidingWith(x), "Reads should never conflict");
        assertFalse(z.isCollidingWith(y), "Reads should never conflict");
        assertFalse(y.isCollidingWith(z), "Reads should never conflict");
    }

    @Test
    void parentChildW() {
        x.w(a1);
        x.w(a2);
        y.w(a3);
        y.w(a4);
        z.w(a);
        assertFalse(x.isCollidingWith(y), "Sibling resources should not conflict");
        assertFalse(y.isCollidingWith(x), "Sibling resources should not conflict");
        assertTrue(x.isCollidingWith(z), "Taking parent resource assumes all children are taken too");
        assertTrue(z.isCollidingWith(x), "Taking parent resource assumes all children are taken too");
        assertTrue(z.isCollidingWith(y), "Taking parent resource assumes all children are taken too");
        assertTrue(y.isCollidingWith(z), "Taking parent resource assumes all children are taken too");
    }

    @Test
    void parentChildR3() {
        x.r(c1);
        x.r(c2);
        y.r(c3);
        y.r(c4);
        z.r(c);
        assertFalse(x.isCollidingWith(y), "Reads should never conflict");
        assertFalse(y.isCollidingWith(x), "Reads should never conflict");
        assertFalse(x.isCollidingWith(z), "Reads should never conflict");
        assertFalse(z.isCollidingWith(x), "Reads should never conflict");
        assertFalse(z.isCollidingWith(y), "Reads should never conflict");
        assertFalse(y.isCollidingWith(z), "Reads should never conflict");
    }

    @Test
    void parentChildW3() {
        x.w(c1);
        x.w(c2);
        y.w(c3);
        y.w(c4);
        z.w(c);
        assertFalse(x.isCollidingWith(y), "Sibling resources should not conflict");
        assertFalse(y.isCollidingWith(x), "Sibling resources should not conflict");
        assertFalse(x.isCollidingWith(z), "Using less than the limit of child resources should not be a problem");
        assertFalse(z.isCollidingWith(x), "Using less than the limit of child resources should not be a problem");
        assertFalse(z.isCollidingWith(y), "Using less than the limit of child resources should not be a problem");
        assertFalse(y.isCollidingWith(z), "Using less than the limit of child resources should not be a problem");

        ResourceList w = ResourceList.union(x, y);

        assertFalse(w.isCollidingWith(z), "Using less than the limit of child resources should not be a problem");
        assertFalse(z.isCollidingWith(w), "Using less than the limit of child resources should not be a problem");

        assertFalse(w.isCollidingWith(x), "Total count = 2, limit is 3");
        assertFalse(x.isCollidingWith(w), "Total count = 2, limit is 3");

        ResourceList v = ResourceList.union(x, x);  // write count is two
        assertFalse(v.isCollidingWith(x), "Total count = 3, limit is 3");
        assertFalse(x.isCollidingWith(v), "Total count = 3, limit is 3");

        v = ResourceList.union(v, x);  // write count is three
        assertTrue(v.isCollidingWith(x), "Total count = 4, limit is 3");
        assertTrue(x.isCollidingWith(v), "Total count = 4, limit is 3");
    }

    @Test
    void multiWrite1() {
        y.w(e);
        assertFalse(x.isCollidingWith(y));
        assertFalse(y.isCollidingWith(x));

        for (int i = 0; i < fWriteCount; i++) {
            x.w(e);
            assertTrue(x.isCollidingWith(y), "Total = W" + (i + 1) + ", Limit = W1");
            assertTrue(y.isCollidingWith(x), "Total = W" + (i + 1) + ", Limit = W1");
        }
        int j = entropy.nextInt(50) + 3;
        for (int i = 1; i < j; i++) {
            assertTrue(x.isCollidingWith(y), "Total = W" + (i + fWriteCount) + ", Limit = W1");
            assertTrue(y.isCollidingWith(x), "Total = W" + (i + fWriteCount) + ", Limit = W1");
            x.w(e);
        }
    }

    @Test
    void multiWriteN() {
        y.w(f);
        for (int i = 0; i < f.numConcurrentWrite; i++) {
            assertFalse(x.isCollidingWith(y), "Total = W" + i + ", Limit = W" + f.numConcurrentWrite);
            assertFalse(y.isCollidingWith(x), "Total = W" + i + ", Limit = W" + f.numConcurrentWrite);
            x.w(f);
        }
        int j = entropy.nextInt(50) + 3;
        for (int i = 1; i < j; i++) {
            assertTrue(x.isCollidingWith(y), "Total = W" + (fWriteCount + i) + ", Limit = W" + fWriteCount);
            assertTrue(y.isCollidingWith(x), "Total = W" + (fWriteCount + i) + ", Limit = W" + fWriteCount);
            x.w(f);
        }
    }

    @Test
    void multiRead1() {
        y.r(e);
        for (int i = 0; i < fWriteCount; i++) {
            assertFalse(x.isCollidingWith(y), "Total = R" + (i + 1) + ", Limit = W1");
            assertFalse(y.isCollidingWith(x), "Total = R" + (i + 1) + ", Limit = W1");
            x.r(e);
        }
        int j = entropy.nextInt(50) + 3;
        for (int i = 1; i < j; i++) {
            assertFalse(x.isCollidingWith(y), "Total = R" + (i + fWriteCount) + ", Limit = W1");
            assertFalse(y.isCollidingWith(x), "Total = R" + (i + fWriteCount) + ", Limit = W1");
            x.r(e);
        }
    }

    @Test
    void multiReadN() {
        y.r(f);
        for (int i = 0; i < fWriteCount; i++) {
            assertFalse(x.isCollidingWith(y), "Total = R" + (i + 1) + ", Limit = W" + fWriteCount);
            assertFalse(y.isCollidingWith(x), "Total = R" + (i + 1) + ", Limit = W" + fWriteCount);
            x.r(f);
        }
        int j = entropy.nextInt(50) + 3;
        for (int i = 1; i < j; i++) {
            assertFalse(x.isCollidingWith(y), "Total = R" + (fWriteCount + i) + ", Limit = W" + fWriteCount);
            assertFalse(y.isCollidingWith(x), "Total = R" + (fWriteCount + i) + ", Limit = W" + fWriteCount);
            x.r(f);
        }
    }
}
