package hudson.util;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class CopyOnWriteListTest extends TestCase {
    public static final class TestData {
        CopyOnWriteList list1 = new CopyOnWriteList();
        List list2 = new ArrayList();
    }

    /**
     * Verify that the serialization form of List and CopyOnWriteList are the same.
     */
    public void testSerialization() throws Exception {

        XStream2 xs = new XStream2();
        TestData td = new TestData();
        td.list1.add("foobar1");
        td.list2.add("foobar2");

        xs.toXML(td,System.out);
    }
}
