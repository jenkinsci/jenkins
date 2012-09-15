package ll;

import ll.Attempt2.Direction;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.NoSuchElementException;

/**
 * @author Kohsuke Kawaguchi
 */
public class Attempt2Test extends Assert {
    @Rule
    public FakeMapBuilder aBuilder = new FakeMapBuilder();
    private FakeMap a;

    @Rule
    public FakeMapBuilder bBuilder = new FakeMapBuilder();
    private FakeMap b;


    @Before
    public void setUp() throws Exception {
        a = aBuilder.add(1, "A").add(3, "B").add(5, "C").make();

        b = bBuilder.make();
    }

    @Test
    public void lookup() {
        a.get(1).asserts(1,"A");
        assertNull(a.get(2));
        a.get(3).asserts(3,"B");
        assertNull(a.get(4));
        a.get(5).asserts(5,"C");

        assertNull(b.get(1));
        assertNull(b.get(3));
        assertNull(b.get(5));
    }

    @Test
    public void idempotentLookup() {
        for (int i=0; i<5; i++)
            a.get(1).asserts(1,"A");
    }

    @Test
    public void firstKey() {
        assertEquals(1, a.firstKey().intValue());

        try {
            b.firstKey();
            fail();
        } catch (NoSuchElementException e) {
            // as expected
        }
    }

    @Test
    public void lastKey() {
        assertEquals(5, a.lastKey().intValue());
        try {
            b.lastKey();
            fail();
        } catch (NoSuchElementException e) {
            // as expected
        }
    }

    @Test
    public void search() {
        // searching toward non-existent direction
        assertNull(a.search( 99, Direction.ASC));
        assertNull(a.search(-99, Direction.DESC));
    }
}
