package hudson.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class QueryParameterMapTest {

    @Test
    void basic() {
        QueryParameterMap m = new QueryParameterMap("a=1&b=x%20y&a=2");
        assertEquals("1", m.get("a"));
        assertEquals(List.of("1", "2"), m.getAll("a"));
        assertEquals("x y", m.get("b"));
        assertNull(m.get("c"));
        assertEquals(List.of(), m.getAll("c"));
    }

    @Test
    void parameterWithoutValue() {
        QueryParameterMap m = new QueryParameterMap("flag&rev=&a=1");
        assertEquals("", m.get("flag"));
        assertEquals("", m.get("rev"));
        assertEquals("1", m.get("a"));
    }

    @Test
    void valueContainingEqualsSign() {
        QueryParameterMap m = new QueryParameterMap("token=abc==&expr=a=b");
        assertEquals("abc==", m.get("token"));
        assertEquals("a=b", m.get("expr"));
    }

    @Test
    void emptyParameters() {
        QueryParameterMap m = new QueryParameterMap("a=1&&b=2");
        assertEquals("1", m.get("a"));
        assertEquals("2", m.get("b"));
        assertEquals(List.of(), m.getAll(""));
    }
}
