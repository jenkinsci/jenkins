package hudson.model;

import junit.framework.TestCase;
import hudson.model.Fingerprint.RangeSet;

/**
 * @author Kohsuke Kawaguchi
 */
public class FingerprintTest extends TestCase {
    public void test() {
        RangeSet rs = new RangeSet();
        assertFalse(rs.includes(0));
        assertFalse(rs.includes(3));
        assertFalse(rs.includes(5));

        rs.add(3);
        assertFalse(rs.includes(2));
        assertTrue(rs.includes(3));
        assertFalse(rs.includes(4));
        assertEquals("[3,4)",rs.toString());

        rs.add(4);
        assertFalse(rs.includes(2));
        assertTrue(rs.includes(3));
        assertTrue(rs.includes(4));
        assertFalse(rs.includes(5));
        assertEquals("[3,5)",rs.toString());

        rs.add(10);
        assertEquals("[3,5),[10,11)",rs.toString());

        rs.add(9);
        assertEquals("[3,5),[9,11)",rs.toString());

        rs.add(6);
        assertEquals("[3,5),[6,7),[9,11)",rs.toString());

        rs.add(5);
        assertEquals("[3,7),[9,11)",rs.toString());
    }

    public void testMerge() {
        RangeSet x = new RangeSet();
        x.add(1);
        x.add(2);
        x.add(3);
        x.add(5);
        x.add(6);
        assertEquals("[1,4),[5,7)",x.toString());

        RangeSet y = new RangeSet();
        y.add(3);
        y.add(4);
        y.add(5);
        assertEquals("[3,6)",y.toString());

        x.add(y);
        assertEquals("[1,7)",x.toString());
    }

    public void testMerge2() {
        RangeSet x = new RangeSet();
        x.add(1);
        x.add(2);
        x.add(5);
        x.add(6);
        assertEquals("[1,3),[5,7)",x.toString());

        RangeSet y = new RangeSet();
        y.add(3);
        y.add(4);
        assertEquals("[3,5)",y.toString());

        x.add(y);
        assertEquals("[1,7)",x.toString());
    }

    public void testMerge3() {
        RangeSet x = new RangeSet();
        x.add(1);
        x.add(5);
        assertEquals("[1,2),[5,6)",x.toString());

        RangeSet y = new RangeSet();
        y.add(3);
        y.add(5);
        y.add(7);
        assertEquals("[3,4),[5,6),[7,8)",y.toString());

        x.add(y);
        assertEquals("[1,2),[3,4),[5,6),[7,8)",x.toString());
    }
}
