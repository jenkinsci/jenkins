package hudson.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Before;
import org.junit.Test;


public class ResourceTest {

    private Resource a, b, c;
    private Resource a1;

    private Random random;
    private ResourceList list1;
    private ResourceList list2;
    private ResourceList list3;

    @Before
    public void setUp() {
        random = new Random(0);
        a = new Resource("A" + random.nextLong());
        b = new Resource("B" + random.nextLong());
        c = new Resource(null, "C" + random.nextLong(), 3);
        a1 = new Resource(a, "A" + random.nextLong());

        list1 = new ResourceList();
        list2 = new ResourceList();
        list3 = new ResourceList();
    }

    @Test
    public void emptyLists() {
        list3.r(a);
        ResourceList list = new ResourceList();
        list.w(a);
        assertFalse("Empty vs Empty", list1.isCollidingWith(list2));
        assertFalse("Empty vs Empty", list2.isCollidingWith(list1));
        assertFalse("Empty vs Read", list1.isCollidingWith(list3));
        assertFalse("Read vs Empty", list3.isCollidingWith(list1));
        assertFalse("Empty vs Write", list1.isCollidingWith(list));
        assertFalse("Write vs Empty", list.isCollidingWith(list1));
    }

    @Test
    public void Read() {
        list1.r(a);
        list2.r(b);
        list3.r(a);

        assertFalse("Read-Read", list1.isCollidingWith(list2));
        assertFalse("Read-Read", list2.isCollidingWith(list1));
        assertFalse("Read-Read", list1.isCollidingWith(list3));
        assertFalse("Read-Read", list3.isCollidingWith(list1));
        assertFalse("Read-Read", list3.isCollidingWith(list2));
        assertFalse("Read-Read", list2.isCollidingWith(list3));
    }

    @Test
    public void ReadWrite() {
        list1.r(a);
        list2.r(b);
        list3.w(a);

        assertFalse("Read-Read different resources", list1.isCollidingWith(list2));
        assertFalse("Read-Read different resources", list2.isCollidingWith(list1));
        assertTrue("Read-Write same resource", list1.isCollidingWith(list3));
        assertTrue("Read-Write same resource", list3.isCollidingWith(list1));
        assertFalse("Read-Write different resources", list3.isCollidingWith(list2));
        assertFalse("Read-Write different resources", list2.isCollidingWith(list3));
    }

    @Test
    public void Write() {
        list1.w(a);
        list2.w(b);
        list3.w(a);

        assertFalse(list1.isCollidingWith(list2));
        assertFalse(list2.isCollidingWith(list1));
        assertTrue(list1.isCollidingWith(list3));
        assertTrue(list3.isCollidingWith(list1));
        assertFalse(list3.isCollidingWith(list2));
        assertFalse(list2.isCollidingWith(list3));

        ResourceList w = ResourceList.union(list1, list2);
        assertTrue(w.isCollidingWith(list3));
        assertTrue(list3.isCollidingWith(w));

        ResourceList list = new ResourceList();
        list.w(a1);
        assertTrue(w.isCollidingWith(list));
        assertTrue(list3.isCollidingWith(w));
    }

}