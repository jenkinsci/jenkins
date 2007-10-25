package hudson.util;

import junit.framework.TestCase;

import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class IteratorsTest extends TestCase {
    public void testReverseSequence() {
        List<Integer> lst = Iterators.reverseSequence(1,4);
        assertEquals(3,(int)lst.get(0));
        assertEquals(2,(int)lst.get(1));
        assertEquals(1,(int)lst.get(2));
        assertEquals(3,lst.size());
    }

    public void testSequence() {
        List<Integer> lst = Iterators.sequence(1,4);
        assertEquals(1,(int)lst.get(0));
        assertEquals(2,(int)lst.get(1));
        assertEquals(3,(int)lst.get(2));
        assertEquals(3,lst.size());
    }
}
