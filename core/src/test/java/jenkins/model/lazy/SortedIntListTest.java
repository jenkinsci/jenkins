package jenkins.model.lazy;

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
        System.out.println(l.lower(Integer.MAX_VALUE));
    }
}
