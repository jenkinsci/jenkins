package ll;

import ll.SortedStringList;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 * @author Kohsuke Kawaguchi
 */
public class SortedStringListTest extends Assert {
    SortedStringList l = new SortedStringList(Arrays.asList("B","D","F"));

    @Test
    public void testCeil() {
        assertEquals(0,l.ceil("A"));
        assertEquals(0,l.ceil("B"));
        assertEquals(1,l.ceil("C"));
        assertEquals(1,l.ceil("D"));
        assertEquals(2,l.ceil("E"));
        assertEquals(2,l.ceil("F"));
        assertEquals(3,l.ceil("G"));
    }
    
    @Test
    public void testFloor() {
        assertEquals(-1,l.floor("A"));
        assertEquals(0,l.floor("B"));
        assertEquals(0,l.floor("C"));
        assertEquals(1,l.floor("D"));
        assertEquals(1,l.floor("E"));
        assertEquals(2,l.floor("F"));
        assertEquals(2,l.floor("G"));
    }

    @Test
    public void testLower() {
        assertEquals(-1,l.lower("A"));
        assertEquals(-1,l.lower("B"));
        assertEquals(0,l.lower("C"));
        assertEquals(0,l.lower("D"));
        assertEquals(1,l.lower("E"));
        assertEquals(1,l.lower("F"));
        assertEquals(2,l.lower("G"));
    }

    @Test
    public void testHigher() {
        assertEquals(0,l.higher("A"));
        assertEquals(1,l.higher("B"));
        assertEquals(1,l.higher("C"));
        assertEquals(2,l.higher("D"));
        assertEquals(2,l.higher("E"));
        assertEquals(3,l.higher("F"));
        assertEquals(3,l.higher("G"));
    }

    @Test
    public void testRange() {
        assertTrue(l.isInRange(0));
        assertTrue(l.isInRange(1));
        assertTrue(l.isInRange(2));

        assertFalse(l.isInRange(-1));
        assertFalse(l.isInRange(3));
    }
}
