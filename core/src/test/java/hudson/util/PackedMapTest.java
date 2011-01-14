package hudson.util;

import junit.framework.TestCase;

import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * @author Kohsuke Kawaguchi
 */
public class PackedMapTest extends TestCase {
    static class Holder {
        PackedMap pm;
    }

    private XStream2 xs = new XStream2();

    public void testBasic() throws Exception {
        Map<String,String> o = new TreeMap<String, String>();
        o.put("a","b");
        o.put("c","d");

        PackedMap<String,String> p = PackedMap.of(o);
        assertEquals("b",p.get("a"));
        assertEquals("d", p.get("c"));
        assertEquals(p.size(),2);
        for (Entry<String,String> e : p.entrySet()) {
            System.out.println(e.getKey()+'='+e.getValue());
        }

        Holder h = new Holder();
        h.pm = p;
        String xml = xs.toXML(h);
        assertEquals(
                "<hudson.util.PackedMapTest_-Holder>\n" +
                "  <pm>\n" +
                "    <entry>\n" +
                "      <string>a</string>\n" +
                "      <string>b</string>\n" +
                "    </entry>\n" +
                "    <entry>\n" +
                "      <string>c</string>\n" +
                "      <string>d</string>\n" +
                "    </entry>\n" +
                "  </pm>\n" +
                "</hudson.util.PackedMapTest_-Holder>",
                xml);

        xs.fromXML(xml);
    }
}
