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

import hudson.util.XStream2;
import junit.framework.TestCase;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Kohsuke Kawaguchi
 */
public class XStreamDOMTest extends TestCase {

    private XStream2 xs;

    public static class Foo {
        XStreamDOM bar;
        XStreamDOM zot;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        xs = new XStream2();
        xs.alias("foo", Foo.class);
    }

    public void testMarshal() throws IOException {
        Foo foo = createSomeFoo();
        String xml = xs.toXML(foo);
        System.out.println(xml);
        assertEquals(IOUtils.toString(getClass().getResourceAsStream("XStreamDOMTest.data1.xml")).trim(),xml.trim());
    }

    private Foo createSomeFoo() {
        Foo foo = new Foo();
        foo.bar = new XStreamDOM("test1", Collections.singletonMap("key", "value"),"text!");
        foo.zot = new XStreamDOM("test2", Collections.singletonMap("key","value"),Collections.singletonList(foo.bar));
        return foo;
    }

    public void testUnmarshal() throws Exception {
        Foo foo = (Foo) xs.fromXML(getClass().getResourceAsStream("XStreamDOMTest.data1.xml"));
        assertEquals("test1",foo.bar.getTagName());
        assertEquals("value",foo.bar.getAttribute("key"));
        assertEquals("text!",foo.bar.getValue());
    }

    public void testWriteToDOM() throws Exception {
        // roundtrip via DOM
        XStreamDOM dom = XStreamDOM.from(xs, createSomeFoo());
        Foo foo = dom.unmarshal(xs);

        String xml = xs.toXML(foo);
        System.out.println(xml);
        assertEquals(IOUtils.toString(getClass().getResourceAsStream("XStreamDOMTest.data1.xml")).trim(),xml.trim());
    }

    public void testNoChild() {
        String[] in = new String[0];
        XStreamDOM dom = XStreamDOM.from(xs, in);
        System.out.println(xs.toXML(dom));
        String[] out = dom.unmarshal(xs);
        assertEquals(in.length, out.length);
    }

    public void testNameEscape() {
        Object o = new Name_That_Gets_Escaped();
        XStreamDOM dom = XStreamDOM.from(xs, o);
        System.out.println(xs.toXML(dom));
        Object out = dom.unmarshal(xs);
        assertEquals(o.getClass(),out.getClass());
    }

    public static class Name_That_Gets_Escaped {}

    public static class DomInMap {
        Map<String,XStreamDOM> values = new HashMap<String, XStreamDOM>();
    }

    public void testDomInMap() {
        DomInMap v = new DomInMap();
        v.values.put("foo",createSomeFoo().bar);
        String xml = xs.toXML(v);
        System.out.println(xml);
        Object v2 = xs.fromXML(xml);
        System.out.println(v2);
    }
}
