/*
 * The MIT License
 *
 * Copyright (c) 2012, CloudBees, Inc.
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

package jenkins.util.xstream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.xmlunit.matchers.CompareMatcher.isSimilarTo;

import hudson.util.XStream2;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.ElementSelectors;

/**
 * @author Kohsuke Kawaguchi
 */
class XStreamDOMTest {

    private XStream2 xs;

    public static class Foo {
        XStreamDOM bar;
        XStreamDOM zot;
    }

    @BeforeEach
    void setUp() {
        xs = new XStream2();
        xs.alias("foo", Foo.class);
    }

    @Test
    void testMarshal() throws IOException {
        Foo foo = createSomeFoo();
        String xml = xs.toXML(foo);
        System.out.println(xml);
        assertThat(getTestData1().trim(), isSimilarTo(xml.trim()).ignoreWhitespace()
                .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byNameAndText)));
    }

    private String getTestData1() throws IOException {
        return getTestData("XStreamDOMTest.data1.xml");
    }

    private String getTestData(String resourceName) throws IOException {
        return IOUtils.toString(getClass().getResourceAsStream(resourceName)).replaceAll("\r\n", "\n");
    }


    private Foo createSomeFoo() {
        Foo foo = new Foo();
        foo.bar = new XStreamDOM("test1", Map.of("key", "value"), "text!");
        foo.zot = new XStreamDOM("test2", Map.of("key", "value"), List.of(foo.bar));
        return foo;
    }

    @Test
    void testUnmarshal() throws Exception {
        Foo foo;
        try (InputStream is = XStreamDOMTest.class.getResourceAsStream("XStreamDOMTest.data1.xml")) {
            foo = (Foo) xs.fromXML(is);
        }
        assertEquals("test1", foo.bar.getTagName());
        assertEquals("value", foo.bar.getAttribute("key"));
        assertEquals("text!", foo.bar.getValue());
    }

    @Test
    void testWriteToDOM() throws Exception {
        // roundtrip via DOM
        XStreamDOM dom = XStreamDOM.from(xs, createSomeFoo());
        Foo foo = dom.unmarshal(xs);

        String xml = xs.toXML(foo);
        System.out.println(xml);
        assertThat(getTestData1().trim(), isSimilarTo(xml.trim()).ignoreWhitespace()
                .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byNameAndText)));
    }

    @Test
    void testNoChild() {
        String[] in = new String[0];
        XStreamDOM dom = XStreamDOM.from(xs, in);
        System.out.println(xs.toXML(dom));
        String[] out = dom.unmarshal(xs);
        assertEquals(in.length, out.length);
    }

    @Test
    void testNameEscape() {
        Object o = new Name_That_Gets_Escaped();
        XStreamDOM dom = XStreamDOM.from(xs, o);
        System.out.println(xs.toXML(dom));
        Object out = dom.unmarshal(xs);
        assertEquals(o.getClass(), out.getClass());
    }

    public static class Name_That_Gets_Escaped {}

    public static class DomInMap {
        Map<String, XStreamDOM> values = new HashMap<>();
    }

    @Test
    void testDomInMap() {
        DomInMap v = new DomInMap();
        v.values.put("foo", createSomeFoo().bar);
        String xml = xs.toXML(v);
        Object v2 = xs.fromXML(xml);
        assertThat(v2, instanceOf(DomInMap.class));
        assertXStreamDOMEquals(v.values.get("foo"), ((DomInMap) v2).values.get("foo"));
    }

    private void assertXStreamDOMEquals(XStreamDOM expected, XStreamDOM actual) {
        assertEquals(expected.getTagName(), actual.getTagName());
        assertEquals(expected.getValue(), actual.getValue());

        assertEquals(expected.getAttributeCount(), actual.getAttributeCount());
        for (int i = 0; i < expected.getAttributeCount(); i++) {
            assertEquals(expected.getAttributeName(i), actual.getAttributeName(i));
            assertEquals(expected.getAttribute(i), actual.getAttribute(i));
        }

        if (expected.getChildren() == null) {
            assertNull(actual.getChildren());
        } else {
            assertEquals(expected.getChildren().size(), actual.getChildren().size());
            int childrenCount = expected.getChildren().size();
            for (int i = 0; i < childrenCount; i++) {
                assertXStreamDOMEquals(expected.getChildren().get(i), actual.getChildren().get(i));
            }
        }
    }

    @Test
    void readFromInputStream() throws Exception {
        for (String name : new String[]{"XStreamDOMTest.data1.xml", "XStreamDOMTest.data2.xml"}) {
            String input = getTestData(name);
            XStreamDOM dom = XStreamDOM.from(new StringReader(input));
            StringWriter sw = new StringWriter();
            dom.writeTo(sw);
            assertEquals(input.trim(), sw.toString().trim());
        }
    }

    /**
     * Regardless of how we read XML into XStreamDOM, XStreamDOM should retain the raw XML infoset,
     * which means escaped names.
     */
    @Test
    void escapeHandling() throws Exception {
        String input = getTestData("XStreamDOMTest.data3.xml");

        XStreamDOM dom = XStreamDOM.from(new StringReader(input));
        List<XStreamDOM> children = dom.getChildren().get(0).getChildren().get(0).getChildren();
        assertNamesAreEscaped(children);

        Foo foo = (Foo) xs.fromXML(new StringReader(input));
        assertNamesAreEscaped(foo.bar.getChildren());

        StringWriter sw = new StringWriter();
        dom.writeTo(sw);
        assertTrue(sw.toString().contains("bar_-bar"));
        assertTrue(sw.toString().contains("zot__bar"));

        String s = xs.toXML(foo);
        assertTrue(s.contains("bar_-bar"));
        assertTrue(s.contains("zot__bar"));
    }

    private void assertNamesAreEscaped(List<XStreamDOM> children) {
        assertEquals("bar_-bar", children.get(0).getTagName());
        assertEquals("zot__bar", children.get(1).getTagName());
    }
}
