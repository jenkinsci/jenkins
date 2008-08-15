package hudson;

import hudson.model.Saveable;
import junit.framework.TestCase;

import java.io.IOException;

/**
 * Tests {@link BulkChange}.
 *
 * @author Kohsuke Kawaguchi
 */
public class BulkChangeTest extends TestCase {

    private class Point implements Saveable {
        /**
         * Don't actually do any save, but just remember how many the actual I/O would have happened.
         */
        int saveCount = 0;

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
    public void testNoBulkChange() throws Exception {
        Point pt = new Point();
        pt.set(0,0);
        assertEquals(2,pt.saveCount);
    }

    /**
     * With a {@link BulkChange}, this will become just one save.
     */
    public void testBulkChange() throws Exception {
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
    public void testNestedBulkChange() throws Exception {
        Point pt = new Point();
        Point _ = new Point();
        BulkChange bc1 = new BulkChange(pt);
        try {
            BulkChange bc2 = new BulkChange(_);
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
