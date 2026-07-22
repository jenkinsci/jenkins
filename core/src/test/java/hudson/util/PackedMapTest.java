package hudson.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * @author Kohsuke Kawaguchi
 */
class PackedMapTest {

    static class Holder {
        PackedMap pm;
    }

    private XStream2 xs = new XStream2();

    @Test
    void basic() {
        Map<String, String> o = new TreeMap<>();
        o.put("a", "b");
        o.put("c", "d");

        PackedMap<String, String> p = PackedMap.of(o);
        assertEquals("b", p.get("a"));
        assertEquals("d", p.get("c"));
        assertEquals(2, p.size());
        for (Map.Entry<String, String> e : p.entrySet()) {
            System.out.println(e.getKey() + '=' + e.getValue());
        }

        Holder h = new Holder();
        h.pm = p;
        String xml = xs.toXML(h);
        assertEquals(
                """
                        <hudson.util.PackedMapTest_-Holder>
                          <pm>
                            <entry>
                              <string>a</string>
                              <string>b</string>
                            </entry>
                            <entry>
                              <string>c</string>
                              <string>d</string>
                            </entry>
                          </pm>
                        </hudson.util.PackedMapTest_-Holder>""",
                xml);

        xs.fromXML(xml);
    }

    @Test
    void values() {
        Map<String, String> o = new TreeMap<>();
        o.put("a", "b");
        o.put("c", "d");

        PackedMap<String, String> p = PackedMap.of(o);
        assertEquals(List.of("b", "d"), new ArrayList<>(p.values()));
        assertEquals("b", p.values().stream().findFirst().orElseThrow());
        assertEquals(2, p.values().size());
    }
}
