package jenkins.model.lazy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * @author Kohsuke Kawaguchi
 */
class SortedIntListTest {

    @Test
    void testLower() {
        SortedIntList l = new SortedIntList(5);
        l.add(0);
        l.add(5);
        l.add(10);
        assertEquals(2, l.lower(Integer.MAX_VALUE));
    }

    @Test
    void ceil() {
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

    @Test
    void max() {
        SortedIntList l = new SortedIntList(5);
        assertEquals(0, l.max());
        l.add(1);
        assertEquals(1, l.max());
        l.add(5);
        assertEquals(5, l.max());
        l.add(10);
        assertEquals(10, l.max());
    }

}
