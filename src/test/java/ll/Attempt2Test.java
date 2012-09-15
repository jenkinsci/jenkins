package ll;

import ll.Attempt2.Direction;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * @author Kohsuke Kawaguchi
 */
public class Attempt2Test extends Assert {
    // A=1, B=3, C=5
    @Rule
    public FakeMapBuilder aBuilder = new FakeMapBuilder();
    private FakeMap a;

    // empty map
    @Rule
    public FakeMapBuilder bBuilder = new FakeMapBuilder();
    private FakeMap b;

    @Rule
    public FakeMapBuilder localBuilder = new FakeMapBuilder();

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
    public void lookupWithBogusKeyType() {
        assertNull(a.get(null));
        assertNull(a.get("foo"));
        assertNull(a.get(this));
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

    /**
     * If load fails, search needs to gracefully handle it
     */
    @Test
    public void unloadableData() throws IOException {
        FakeMap m = localBuilder.add(1, "A").addUnloadable("B").add(5, "C").make();

        assertNull(m.search(3,Direction.EXACT));
        m.search(3,Direction.DESC).asserts(1,"A");
        m.search(3,Direction.ASC ).asserts(5,"C");
    }

    @Test
    public void eagerLoading() throws IOException {
        Map.Entry[] b = a.entrySet().toArray(new Map.Entry[3]);
        ((Build)b[0].getValue()).asserts(1,"A");
        ((Build)b[1].getValue()).asserts(3,"B");
        ((Build)b[2].getValue()).asserts(5,"C");
    }
}
