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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Before;
import org.junit.Test;

/**
 * @author Stephen Connolly
 */
public class ResourceListTest {

    private Resource a1, a2, a3, a4, a;
    private Resource b1, b2, b3, b4, b;
    private Resource c1, c2, c3, c4, c;
    private Resource d, e, f;
    private int fWriteCount;
    private Random entropy;
    private ResourceList x;
    private ResourceList y;
    private ResourceList z;

    @Before
    public void setUp() {
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
    public void emptyLists() {
        z.r(a);
        ResourceList w = new ResourceList();
        w.w(a);
        assertFalse("Empty vs Empty", x.isCollidingWith(y));
        assertFalse("Empty vs Empty", y.isCollidingWith(x));
        assertFalse("Empty vs Read", x.isCollidingWith(z));
        assertFalse("Read vs Empty", z.isCollidingWith(x));
        assertFalse("Empty vs Write", x.isCollidingWith(w));
        assertFalse("Write vs Empty", w.isCollidingWith(x));
    }

    @Test
    public void simpleR() {
        x.r(a);
        y.r(b);
        z.r(a);

        assertFalse("Read-Read", x.isCollidingWith(y));
        assertFalse("Read-Read", y.isCollidingWith(x));
        assertFalse("Read-Read", x.isCollidingWith(z));
        assertFalse("Read-Read", z.isCollidingWith(x));
        assertFalse("Read-Read", z.isCollidingWith(y));
        assertFalse("Read-Read", y.isCollidingWith(z));
    }

    @Test
    public void simpleRW() {
        x.r(a);
        y.r(b);
        z.w(a);

        assertFalse("Read-Read different resources", x.isCollidingWith(y));
        assertFalse("Read-Read different resources", y.isCollidingWith(x));
        assertTrue("Read-Write same resource", x.isCollidingWith(z));
        assertTrue("Read-Write same resource", z.isCollidingWith(x));
        assertFalse("Read-Write different resources", z.isCollidingWith(y));
        assertFalse("Read-Write different resources", y.isCollidingWith(z));
    }

    @Test
    public void simpleW() {
        x.w(a);
        y.w(b);
        z.w(a);

        assertFalse(x.isCollidingWith(y));
        assertFalse(y.isCollidingWith(x));
        assertTrue(x.isCollidingWith(z));
        assertTrue(z.isCollidingWith(x));
        assertFalse(z.isCollidingWith(y));
        assertFalse(y.isCollidingWith(z));

        ResourceList w = ResourceList.union(x,y);
        assertTrue(w.isCollidingWith(z));
        assertTrue(z.isCollidingWith(w));

        ResourceList v = new ResourceList();
        v.w(a1);
        assertTrue(w.isCollidingWith(v));
        assertTrue(z.isCollidingWith(w));
    }

    @Test
    public void parentChildR() {
        x.r(a1);
        x.r(a2);
        y.r(a3);
        y.r(a4);
        z.r(a);
        assertFalse("Reads should never conflict", x.isCollidingWith(y));
        assertFalse("Reads should never conflict", y.isCollidingWith(x));
        assertFalse("Reads should never conflict", x.isCollidingWith(z));
        assertFalse("Reads should never conflict", z.isCollidingWith(x));
        assertFalse("Reads should never conflict", z.isCollidingWith(y));
        assertFalse("Reads should never conflict", y.isCollidingWith(z));
    }

    @Test
    public void parentChildW() {
        x.w(a1);
        x.w(a2);
        y.w(a3);
        y.w(a4);
        z.w(a);
        assertFalse("Sibling resources should not conflict", x.isCollidingWith(y));
        assertFalse("Sibling resources should not conflict", y.isCollidingWith(x));
        assertTrue("Taking parent resource assumes all children are taken too", x.isCollidingWith(z));
        assertTrue("Taking parent resource assumes all children are taken too", z.isCollidingWith(x));
        assertTrue("Taking parent resource assumes all children are taken too", z.isCollidingWith(y));
        assertTrue("Taking parent resource assumes all children are taken too", y.isCollidingWith(z));
    }

    @Test
    public void parentChildR3() {
        x.r(c1);
        x.r(c2);
        y.r(c3);
        y.r(c4);
        z.r(c);
        assertFalse("Reads should never conflict", x.isCollidingWith(y));
        assertFalse("Reads should never conflict", y.isCollidingWith(x));
        assertFalse("Reads should never conflict", x.isCollidingWith(z));
        assertFalse("Reads should never conflict", z.isCollidingWith(x));
        assertFalse("Reads should never conflict", z.isCollidingWith(y));
        assertFalse("Reads should never conflict", y.isCollidingWith(z));
    }

    @Test
    public void parentChildW3() {
        x.w(c1);
        x.w(c2);
        y.w(c3);
        y.w(c4);
        z.w(c);
        assertFalse("Sibling resources should not conflict", x.isCollidingWith(y));
        assertFalse("Sibling resources should not conflict", y.isCollidingWith(x));
        assertFalse("Using less than the limit of child resources should not be a problem", x.isCollidingWith(z));
        assertFalse("Using less than the limit of child resources should not be a problem", z.isCollidingWith(x));
        assertFalse("Using less than the limit of child resources should not be a problem", z.isCollidingWith(y));
        assertFalse("Using less than the limit of child resources should not be a problem", y.isCollidingWith(z));

        ResourceList w = ResourceList.union(x,y);

        assertFalse("Using less than the limit of child resources should not be a problem", w.isCollidingWith(z));
        assertFalse("Using less than the limit of child resources should not be a problem", z.isCollidingWith(w));

        assertFalse("Total count = 2, limit is 3", w.isCollidingWith(x));
        assertFalse("Total count = 2, limit is 3", x.isCollidingWith(w));

        ResourceList v = ResourceList.union(x,x);  // write count is two
        assertFalse("Total count = 3, limit is 3", v.isCollidingWith(x));
        assertFalse("Total count = 3, limit is 3", x.isCollidingWith(v));

        v = ResourceList.union(v,x);  // write count is three
        assertTrue("Total count = 4, limit is 3", v.isCollidingWith(x));
        assertTrue("Total count = 4, limit is 3", x.isCollidingWith(v));
    }

    @Test
    public void multiWrite1() {
        y.w(e);
        assertFalse(x.isCollidingWith(y));
        assertFalse(y.isCollidingWith(x));

        for (int i = 0; i < fWriteCount; i++) {
            x.w(e);
            assertTrue("Total = W" + (i + 1) + ", Limit = W1", x.isCollidingWith(y));
            assertTrue("Total = W" + (i + 1) + ", Limit = W1", y.isCollidingWith(x));
        }
        int j = entropy.nextInt(50) + 3;
        for (int i = 1; i < j; i++) {
            assertTrue("Total = W" + (i + fWriteCount) + ", Limit = W1", x.isCollidingWith(y));
            assertTrue("Total = W" + (i + fWriteCount) + ", Limit = W1", y.isCollidingWith(x));
            x.w(e);
        }
    }

    @Test
    public void multiWriteN() {
        y.w(f);
        for (int i=0; i<f.numConcurrentWrite; i++) {
            assertFalse("Total = W" + i + ", Limit = W" + f.numConcurrentWrite, x.isCollidingWith(y));
            assertFalse("Total = W" + i + ", Limit = W" + f.numConcurrentWrite, y.isCollidingWith(x));
            x.w(f);
        }
        int j = entropy.nextInt(50) + 3;
        for (int i = 1; i < j; i++) {
            assertTrue("Total = W" + (fWriteCount + i) + ", Limit = W" + fWriteCount, x.isCollidingWith(y));
            assertTrue("Total = W" + (fWriteCount + i) + ", Limit = W" + fWriteCount, y.isCollidingWith(x));
            x.w(f);
        }
    }

    @Test
    public void multiRead1() {
        y.r(e);
        for (int i = 0; i < fWriteCount; i++) {
            assertFalse("Total = R" + (i + 1) + ", Limit = W1", x.isCollidingWith(y));
            assertFalse("Total = R" + (i + 1) + ", Limit = W1", y.isCollidingWith(x));
            x.r(e);
        }
        int j = entropy.nextInt(50) + 3;
        for (int i = 1; i < j; i++) {
            assertFalse("Total = R" + (i + fWriteCount) + ", Limit = W1", x.isCollidingWith(y));
            assertFalse("Total = R" + (i + fWriteCount) + ", Limit = W1", y.isCollidingWith(x));
            x.r(e);
        }
    }

    @Test
    public void multiReadN() {
        y.r(f);
        for (int i = 0; i < fWriteCount; i++) {
            assertFalse("Total = R" + (i + 1) + ", Limit = W" + fWriteCount, x.isCollidingWith(y));
            assertFalse("Total = R" + (i + 1) + ", Limit = W" + fWriteCount, y.isCollidingWith(x));
            x.r(f);
        }
        int j = entropy.nextInt(50) + 3;
        for (int i = 1; i < j; i++) {
            assertFalse("Total = R" + (fWriteCount + i) + ", Limit = W" + fWriteCount, x.isCollidingWith(y));
            assertFalse("Total = R" + (fWriteCount + i) + ", Limit = W" + fWriteCount, y.isCollidingWith(x));
            x.r(f);
        }
    }
}
