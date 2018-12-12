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
package hudson;

import static org.junit.Assert.assertEquals;

import hudson.model.Saveable;
import org.junit.Test;

import java.io.IOException;

/**
 * Tests {@link BulkChange}.
 *
 * @author Kohsuke Kawaguchi
 */
public class BulkChangeTest {

    private class Point implements Saveable {
        /**
         * Don't actually do any save, but just remember how many the actual I/O would have happened.
         */
        int saveCount = 0;

        @SuppressWarnings("unused")
        int x,y;

        public void setX(int x) throws IOException {
            this.x = x;
            save();
        }

        public void setY(int y) throws IOException {
            this.y = y;
            save();
        }

        public void set(int x, int y) throws IOException {
            setX(x);
            setY(y);
        }

        public void save() throws IOException {
            if(BulkChange.contains(this))   return;
            saveCount++;
        }
    }

    /**
     * If there is no BulkChange, we should see two saves.
     */
    @Test
    public void noBulkChange() throws Exception {
        Point pt = new Point();
        pt.set(0,0);
        assertEquals(2,pt.saveCount);
    }

    /**
     * With a {@link BulkChange}, this will become just one save.
     */
    @Test
    public void bulkChange() throws Exception {
        Point pt = new Point();
        BulkChange bc = new BulkChange(pt);
        try {
            pt.set(0,0);
        } finally {
            bc.commit();
        }
        assertEquals(1,pt.saveCount);
    }

    /**
     * {@link BulkChange}s can be nested.
     */
    @Test
    public void nestedBulkChange() throws Exception {
        Point pt = new Point();
        Point pt2 = new Point();
        BulkChange bc1 = new BulkChange(pt);
        try {
            BulkChange bc2 = new BulkChange(pt2);
            try {
                BulkChange bc3 = new BulkChange(pt);
                try {
                    pt.set(0,0);
                } finally {
                    bc3.commit();
                }
            } finally {
                bc2.commit();
            }
            pt.set(0,0);
        } finally {
            bc1.commit();
        }
        assertEquals(1,pt.saveCount);
    }
}
