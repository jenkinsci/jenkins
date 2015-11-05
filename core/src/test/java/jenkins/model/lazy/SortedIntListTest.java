package jenkins.model.lazy;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author Kohsuke Kawaguchi
 */
public class SortedIntListTest {
    @Test
    public void testLower() {
        SortedIntList l = new SortedIntList(5);
        l.add(0);
        l.add(5);
        l.add(10);
        assertEquals(2, l.lower(Integer.MAX_VALUE));
    }

    @Test public void ceil() {
        SortedIntList l = new SortedIntList(5);
        l.add(1);
        l.add(3);
        l.add(5);
        assertEquals(0, l.ceil(0));
        assertEquals(0, l.ceil(1));
        assertEquals(1, l.ceil(2));
        assertEquals(1, l.ceil(3));
        assertEquals(2, l.ceil(4));
        assertEquals(2, l.ceil(5));
        assertEquals(3, l.ceil(6));
        assertTrue(l.isInRange(0));
        assertTrue(l.isInRange(1));
        assertTrue(l.isInRange(2));
        assertFalse(l.isInRange(3));
    }

}
