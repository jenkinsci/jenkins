/*
 * The MIT License
 * 
 * Copyright (c) 2010, Yahoo!, Inc., Alan Harder
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.util;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author Mike Dillon, Alan Harder
 */
public class CopyOnWriteMapTest {
    public static final class HashData {
        CopyOnWriteMap.Hash<String,String> map1 = new CopyOnWriteMap.Hash<String,String>();
        HashMap<String,String> map2 = new HashMap<String,String>();
    }

    /**
     * Verify that serialization form of CopyOnWriteMap.Hash and HashMap are the same.
     *
     * @throws Exception test failure
     */
    @Test public void hashSerialization() throws Exception {
        HashData td = new HashData();
        XStream2 xs = new XStream2();

        String out = xs.toXML(td);
        assertEquals("empty maps", "<hudson.util.CopyOnWriteMapTest_-HashData>"
                + "<map1/><map2/></hudson.util.CopyOnWriteMapTest_-HashData>",
                out.replaceAll("\\s+", ""));
        HashData td2 = (HashData)xs.fromXML(out);
        assertTrue(td2.map1.isEmpty());
        assertTrue(td2.map2.isEmpty());

        td.map1.put("foo1", "bar1");
        td.map2.put("foo2", "bar2");
        out = xs.toXML(td);
        assertEquals("maps", "<hudson.util.CopyOnWriteMapTest_-HashData><map1>"
                + "<entry><string>foo1</string><string>bar1</string></entry></map1>"
                + "<map2><entry><string>foo2</string><string>bar2</string></entry>"
                + "</map2></hudson.util.CopyOnWriteMapTest_-HashData>",
                out.replaceAll("\\s+", ""));
        td2 = (HashData)xs.fromXML(out);
        assertEquals("bar1", td2.map1.get("foo1"));
        assertEquals("bar2", td2.map2.get("foo2"));
    }

    public static final class TreeData {
        CopyOnWriteMap.Tree<String,String> map1;
        TreeMap<String,String> map2;
        TreeData() {
            map1 = new CopyOnWriteMap.Tree<String,String>();
            map2 = new TreeMap<String,String>();
        }
        TreeData(Comparator<String> comparator) {
            map1 = new CopyOnWriteMap.Tree<String,String>(comparator);
            map2 = new TreeMap<String,String>(comparator);
        }
    }

    /**
     * Verify that an empty CopyOnWriteMap.Tree can be serialized,
     * and that serialization form is the same as a standard TreeMap.
     *
     * @throws Exception test failure
     */
    @Test public void treeSerialization() throws Exception {
        TreeData td = new TreeData();
        XStream2 xs = new XStream2();

        String out = xs.toXML(td);
        assertEquals("empty maps", "<hudson.util.CopyOnWriteMapTest_-TreeData>"
                + "<map1/><map2/>"
                + "</hudson.util.CopyOnWriteMapTest_-TreeData>",
                out.replaceAll("\\s+", ""));
        TreeData td2 = (TreeData)xs.fromXML(out);
        assertTrue(td2.map1.isEmpty());
        assertTrue(td2.map2.isEmpty());

        td = new TreeData(String.CASE_INSENSITIVE_ORDER);
        td.map1.put("foo1", "bar1");
        td.map2.put("foo2", "bar2");
        out = xs.toXML(td);
        assertEquals("maps", "<hudson.util.CopyOnWriteMapTest_-TreeData><map1>"
                + "<comparator class=\"java.lang.String$CaseInsensitiveComparator\"/>"
                + "<entry><string>foo1</string><string>bar1</string></entry></map1>"
                + "<map2><comparator class=\"java.lang.String$CaseInsensitiveComparator\""
                + " reference=\"../../map1/comparator\"/>"
                + "<entry><string>foo2</string><string>bar2</string></entry></map2>"
                + "</hudson.util.CopyOnWriteMapTest_-TreeData>",
                out.replaceAll(">\\s+<", "><"));
        td2 = (TreeData)xs.fromXML(out);
        assertEquals("bar1", td2.map1.get("foo1"));
        assertEquals("bar2", td2.map2.get("foo2"));
    }

    @Test public void equalsHashCodeToString() throws Exception {
        Map<String,Integer> m1 = new TreeMap<String,Integer>();
        Map<String,Integer> m2 = new CopyOnWriteMap.Tree<String,Integer>();
        m1.put("foo", 5);
        m1.put("bar", 7);
        m2.put("foo", 5);
        m2.put("bar", 7);
        assertEquals(m1.hashCode(), m2.hashCode());
        assertTrue(m2.equals(m1));
        assertTrue(m1.equals(m2));
        assertEquals(m1.toString(), m2.toString());
    }

}
