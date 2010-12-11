/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
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

import com.google.common.collect.ImmutableMap;
import junit.framework.TestCase;
import hudson.model.Result;
import hudson.model.Run;

import java.util.Map;

/**
 * Tests for XML serialization of java objects.
 * @author Kohsuke Kawaguchi, Mike Dillon, Alan Harder
 */
public class XStream2Test extends TestCase {

    public static final class Foo {
        Result r1,r2;
    }

    public void testMarshalValue() {
        Foo f = new Foo();
        f.r1 = f.r2 = Result.FAILURE;
        String xml = Run.XSTREAM.toXML(f);
        // we should find two "FAILURE"s as they should be written out twice
        assertEquals(xml, 3, xml.split("FAILURE").length);
    }

    private static class Bar {
        String s;
    }

    /**
     * Test ability to read old XML from Hudson 1.105 or older.
     */
    public void testXStream11Compatibility() {
        Bar b = (Bar)new XStream2().fromXML(
                "<hudson.util.XStream2Test-Bar><s>foo</s></hudson.util.XStream2Test-Bar>");
        assertEquals("foo", b.s);
    }

    public static final class __Foo_Bar$Class {
        String under_1 = "1", under__2 = "2",
               _leadUnder1 = "L1", __leadUnder2 = "L2",
               $dollar = "D1", dollar$2 = "D2";
    }

    /**
     * Test marshal/unmarshal round trip for class/field names with _ and $ characters.
     * (HUDSON-5768)
     */
    public void testXmlRoundTrip() {
        XStream2 xs = new XStream2();
        __Foo_Bar$Class b = new __Foo_Bar$Class();

        String xml = xs.toXML(b);
        __Foo_Bar$Class b2 = (__Foo_Bar$Class)xs.fromXML(xml);

        assertEquals(xml, b.under_1, b2.under_1);
        assertEquals(xml, b.under__2, b2.under__2);
        assertEquals(xml, b._leadUnder1, b2._leadUnder1);
        assertEquals(xml, b.__leadUnder2, b2.__leadUnder2);
        assertEquals(xml, b.$dollar, b2.$dollar);
        assertEquals(xml, b.dollar$2, b2.dollar$2);
    }

    private static class Baz {
        private Exception myFailure;
    }

    /**
     * Verify RobustReflectionConverter can handle missing fields in a class extending
     * Throwable/Exception (default ThrowableConverter registered by XStream calls
     * ReflectionConverter directly, rather than our RobustReflectionConverter replacement).
     * (HUDSON-5769)
     */
    public void testUnmarshalThrowableMissingField() {
        Baz baz = new Baz();
        baz.myFailure = new Exception("foo");

        XStream2 xs = new XStream2();
        String xml = xs.toXML(baz);
        baz = (Baz)xs.fromXML(xml);
        assertEquals("foo", baz.myFailure.getMessage());

        baz = (Baz)xs.fromXML("<hudson.util.XStream2Test_-Baz><myFailure>"
                + "<missingField>true</missingField>"
                + "<detailMessage>hoho</detailMessage>"
                + "<stackTrace><trace>"
                + "hudson.util.XStream2Test.testUnmarshalThrowableMissingField(XStream2Test.java:97)"
                + "</trace></stackTrace>"
                + "</myFailure></hudson.util.XStream2Test_-Baz>");
        // Object should load, despite "missingField" in XML above
        assertEquals("hoho", baz.myFailure.getMessage());
    }

    private static class ImmutableMapHolder {
        ImmutableMap m;
    }

    private static class MapHolder {
        Map m;
    }


    public void testImmutableMap() {
        XStream2 xs = new XStream2();

        roundtripImmutableMap(xs, ImmutableMap.of());
        roundtripImmutableMap(xs, ImmutableMap.of("abc", "xyz"));
        roundtripImmutableMap(xs, ImmutableMap.of("abc", "xyz", "def","ghi"));

        roundtripImmutableMapAsPlainMap(xs, ImmutableMap.of());
        roundtripImmutableMapAsPlainMap(xs, ImmutableMap.of("abc", "xyz"));
        roundtripImmutableMapAsPlainMap(xs, ImmutableMap.of("abc", "xyz", "def","ghi"));
    }

    /**
     * Since the field type is {@link ImmutableMap}, XML shouldn't contain a reference to the type name.
     */
    private void roundtripImmutableMap(XStream2 xs, ImmutableMap<?,?> m) {
        ImmutableMapHolder a = new ImmutableMapHolder();
        a.m = m;
        String xml = xs.toXML(a);
        System.out.println(xml);
        assertFalse("shouldn't contain the class name",xml.contains("google"));
        assertFalse("shouldn't contain the class name",xml.contains("class"));
        a = (ImmutableMapHolder)xs.fromXML(xml);

        assertSame(m.getClass(),a.m.getClass());    // should get back the exact same type, not just a random map
        assertEquals(m,a.m);
    }

    private void roundtripImmutableMapAsPlainMap(XStream2 xs, ImmutableMap<?,?> m) {
        MapHolder a = new MapHolder();
        a.m = m;
        String xml = xs.toXML(a);
        System.out.println(xml);
        assertTrue("XML should mention the class name",xml.contains('\"'+ImmutableMap.class.getName()+'\"'));
        a = (MapHolder)xs.fromXML(xml);

        assertSame(m.getClass(),a.m.getClass());    // should get back the exact same type, not just a random map
        assertEquals(m,a.m);
    }

    // @Bug(8006) -- Previously a null entry in an array caused NPE
    public void testEmptyStack() {
        assertEquals("<object-array><null/><null/></object-array>",
                     Run.XSTREAM.toXML(new Object[2]).replaceAll("[ \n\r\t]+", ""));
    }
}
