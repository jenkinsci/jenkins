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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.thoughtworks.xstream.XStreamException;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.mapper.CannotResolveClassException;
import hudson.Functions;
import hudson.model.Result;
import hudson.model.Run;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

/**
 * Tests for XML serialization of java objects.
 * @author Kohsuke Kawaguchi, Mike Dillon, Alan Harder, Richard Mortimer
 */
public class XStream2Test {

    public static final class Foo {
        Result r1, r2;
    }

    @Test
    public void marshalValue() {
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
    @Test
    public void xStream11Compatibility() {
        Bar b = (Bar) new XStream2().fromXML(
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
     */
    @Issue("JENKINS-5768")
    @Test
    public void xmlRoundTrip() {
        XStream2 xs = new XStream2();
        __Foo_Bar$Class b = new __Foo_Bar$Class();

        String xml = xs.toXML(b);
        __Foo_Bar$Class b2 = (__Foo_Bar$Class) xs.fromXML(xml);

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
     */
    @Issue("JENKINS-5769")
    @Test
    public void unmarshalThrowableMissingField() {
        Level oldLevel = disableLogging();

        Baz baz = new Baz();
        baz.myFailure = new Exception("foo");

        XStream2 xs = new XStream2();
        String xml = xs.toXML(baz);
        baz = (Baz) xs.fromXML(xml);
        assertEquals("foo", baz.myFailure.getMessage());

        baz = (Baz) xs.fromXML("<hudson.util.XStream2Test_-Baz><myFailure>"
                + "<missingField>true</missingField>"
                + "<detailMessage>hoho</detailMessage>"
                + "<stackTrace><trace>"
                + "hudson.util.XStream2Test.testUnmarshalThrowableMissingField(XStream2Test.java:97)"
                + "</trace></stackTrace>"
                + "</myFailure></hudson.util.XStream2Test_-Baz>");
        // Object should load, despite "missingField" in XML above
        assertEquals("hoho", baz.myFailure.getMessage());

        enableLogging(oldLevel);
    }

    private Level disableLogging() {
        Level oldLevel = Logger.getLogger(RobustReflectionConverter.class.getName()).getLevel();
        Logger.getLogger(RobustReflectionConverter.class.getName()).setLevel(Level.OFF);
        return oldLevel;
    }

    private void enableLogging(Level oldLevel) {
        Logger.getLogger(RobustReflectionConverter.class.getName()).setLevel(oldLevel);
    }

    private static class ImmutableMapHolder {
        ImmutableMap<?, ?> m;
    }

    private static class MapHolder {
        Map<?, ?> m;
    }

    @Test
    public void immutableMap() {
        XStream2 xs = new XStream2();

        roundtripImmutableMap(xs, ImmutableMap.of());
        roundtripImmutableMap(xs, ImmutableMap.of("abc", "xyz"));
        roundtripImmutableMap(xs, ImmutableMap.of("abc", "xyz", "def", "ghi"));

        roundtripImmutableMapAsPlainMap(xs, ImmutableMap.of());
        roundtripImmutableMapAsPlainMap(xs, ImmutableMap.of("abc", "xyz"));
        roundtripImmutableMapAsPlainMap(xs, ImmutableMap.of("abc", "xyz", "def", "ghi"));
    }

    /**
     * Since the field type is {@link ImmutableMap}, XML shouldn't contain a reference to the type name.
     */
    private void roundtripImmutableMap(XStream2 xs, ImmutableMap<?, ?> m) {
        ImmutableMapHolder a = new ImmutableMapHolder();
        a.m = m;
        String xml = xs.toXML(a);
        //System.out.println(xml);
        assertFalse("shouldn't contain the class name", xml.contains("google"));
        assertFalse("shouldn't contain the class name", xml.contains("class"));
        a = (ImmutableMapHolder) xs.fromXML(xml);

        assertSame(m.getClass(), a.m.getClass());    // should get back the exact same type, not just a random map
        assertEquals(m, a.m);
    }

    private void roundtripImmutableMapAsPlainMap(XStream2 xs, ImmutableMap<?, ?> m) {
        MapHolder a = new MapHolder();
        a.m = m;
        String xml = xs.toXML(a);
        //System.out.println(xml);
        assertTrue("XML should mention the class name", xml.contains('\"' + ImmutableMap.class.getName() + '\"'));
        a = (MapHolder) xs.fromXML(xml);

        assertSame(m.getClass(), a.m.getClass());    // should get back the exact same type, not just a random map
        assertEquals(m, a.m);
    }

    private static class ImmutableListHolder {
        ImmutableList<?> l;
    }

    private static class ListHolder {
        List<?> l;
    }

    @Test
    public void immutableList() {
        XStream2 xs = new XStream2();

        roundtripImmutableList(xs, ImmutableList.of());
        roundtripImmutableList(xs, ImmutableList.of("abc"));
        roundtripImmutableList(xs, ImmutableList.of("abc", "def"));

        roundtripImmutableListAsPlainList(xs, ImmutableList.of());
        roundtripImmutableListAsPlainList(xs, ImmutableList.of("abc"));
        roundtripImmutableListAsPlainList(xs, ImmutableList.of("abc", "def"));
    }

    /**
     * Since the field type is {@link ImmutableList}, XML shouldn't contain a reference to the type name.
     */
    private void roundtripImmutableList(XStream2 xs, ImmutableList<?> l) {
        ImmutableListHolder a = new ImmutableListHolder();
        a.l = l;
        String xml = xs.toXML(a);
        //System.out.println(xml);
        assertFalse("shouldn't contain the class name", xml.contains("google"));
        assertFalse("shouldn't contain the class name", xml.contains("class"));
        a = (ImmutableListHolder) xs.fromXML(xml);

        assertSame(l.getClass(), a.l.getClass());    // should get back the exact same type, not just a random list
        assertEquals(l, a.l);
    }

    private void roundtripImmutableListAsPlainList(XStream2 xs, ImmutableList<?> l) {
        ListHolder a = new ListHolder();
        a.l = l;
        String xml = xs.toXML(a);
        //System.out.println(xml);
        assertTrue("XML should mention the class name", xml.contains('\"' + ImmutableList.class.getName() + '\"'));
        a = (ListHolder) xs.fromXML(xml);

        assertSame(l.getClass(), a.l.getClass());    // should get back the exact same type, not just a random list
        assertEquals(l, a.l);
    }

    @Issue("JENKINS-8006") // Previously a null entry in an array caused NPE
    @Test
    public void emptyStack() {
        assertEquals("<object-array><null/><null/></object-array>",
                     Run.XSTREAM.toXML(new Object[2]).replaceAll("[ \n\r\t]+", ""));
    }

    @Issue("JENKINS-9843")
    @Test
    public void compatibilityAlias() {
        XStream2 xs = new XStream2();
        xs.addCompatibilityAlias("legacy.Point", Point.class);
        Point pt = (Point) xs.fromXML("<legacy.Point><x>1</x><y>2</y></legacy.Point>");
        assertEquals(1, pt.x);
        assertEquals(2, pt.y);
        String xml = xs.toXML(pt);
        //System.out.println(xml);
        assertFalse("Shouldn't use the alias when writing back", xml.contains("legacy"));
    }

    public static class Point {
        public int x, y;
    }

    public static class Foo2 {
        ConcurrentHashMap<String, String> m = new ConcurrentHashMap<>();
    }

    @Issue("SECURITY-105")
    @Test
    public void dynamicProxyBlocked() {
        assertThrows(
                XStreamException.class,
                () -> ((Runnable) new XStream2().fromXML(
                                "<dynamic-proxy><interface>java.lang.Runnable</interface><handler class='java.beans.EventHandler'><target class='"
                                        + Hacked.class.getName()
                                        + "'/><action>oops</action></handler></dynamic-proxy>"))
                        .run());
        assertFalse("should never have run that", Hacked.tripped);
    }

    public static final class Hacked {
        static boolean tripped;

        public void oops() {
            tripped = true;
        }
    }

    @Test
    public void trimVersion() {
        assertEquals("3.2", XStream2.trimVersion("3.2"));
        assertEquals("3.2.1", XStream2.trimVersion("3.2.1"));
        assertEquals("3.2-SNAPSHOT", XStream2.trimVersion("3.2-SNAPSHOT (private-09/23/2012 12:26-jhacker)"));
    }

    @Issue("JENKINS-21017")
    @Test
    public void unmarshalToDefault_populated() {
        String populatedXml = "<hudson.util.XStream2Test_-WithDefaults>\n"
                + "  <stringDefaultValue>my string</stringDefaultValue>\n"
                + "  <stringDefaultNull>not null</stringDefaultNull>\n"
                + "  <arrayDefaultValue>\n"
                + "    <string>1</string>\n"
                + "    <string>2</string>\n"
                + "    <string>3</string>\n"
                + "  </arrayDefaultValue>\n"
                + "  <arrayDefaultEmpty>\n"
                + "    <string>1</string>\n"
                + "    <string>2</string>\n"
                + "    <string>3</string>\n"
                + "  </arrayDefaultEmpty>\n"
                + "  <arrayDefaultNull>\n"
                + "    <string>1</string>\n"
                + "    <string>2</string>\n"
                + "    <string>3</string>\n"
                + "  </arrayDefaultNull>\n"
                + "  <listDefaultValue>\n"
                + "    <string>1</string>\n"
                + "    <string>2</string>\n"
                + "    <string>3</string>\n"
                + "  </listDefaultValue>\n"
                + "  <listDefaultEmpty>\n"
                + "    <string>1</string>\n"
                + "    <string>2</string>\n"
                + "    <string>3</string>\n"
                + "  </listDefaultEmpty>\n"
                + "  <listDefaultNull>\n"
                + "    <string>1</string>\n"
                + "    <string>2</string>\n"
                + "    <string>3</string>\n"
                + "  </listDefaultNull>\n"
                + "</hudson.util.XStream2Test_-WithDefaults>";

        WithDefaults existingInstance = new WithDefaults("foobar",
                "foobar",
                new String[]{"foobar", "barfoo", "fumanchu"},
                new String[]{"foobar", "barfoo", "fumanchu"},
                new String[]{"foobar", "barfoo", "fumanchu"},
                Arrays.asList("foobar", "barfoo", "fumanchu"),
                Arrays.asList("foobar", "barfoo", "fumanchu"),
                Arrays.asList("foobar", "barfoo", "fumanchu")
        );

        WithDefaults newInstance = new WithDefaults();

        String xmlA = Jenkins.XSTREAM2.toXML(fromXMLNullingOut(populatedXml, existingInstance));
        String xmlB = Jenkins.XSTREAM2.toXML(fromXMLNullingOut(populatedXml, newInstance));
        String xmlC = Jenkins.XSTREAM2.toXML(fromXMLNullingOut(populatedXml, null));

        assertThat("Deserializing over an existing instance is the same as with no root", xmlA, is(xmlC));
        assertThat("Deserializing over an new instance is the same as with no root", xmlB, is(xmlC));
    }


    @Issue("JENKINS-21017")
    @Test
    public void unmarshalToDefault_default() {
        String defaultXml = "<hudson.util.XStream2Test_-WithDefaults>\n"
                + "  <stringDefaultValue>defaultValue</stringDefaultValue>\n"
                + "  <arrayDefaultValue>\n"
                + "    <string>first</string>\n"
                + "    <string>second</string>\n"
                + "  </arrayDefaultValue>\n"
                + "  <arrayDefaultEmpty/>\n"
                + "  <listDefaultValue>\n"
                + "    <string>first</string>\n"
                + "    <string>second</string>\n"
                + "  </listDefaultValue>\n"
                + "  <listDefaultEmpty/>\n"
                + "</hudson.util.XStream2Test_-WithDefaults>";

        WithDefaults existingInstance = new WithDefaults("foobar",
                "foobar",
                new String[]{"foobar", "barfoo", "fumanchu"},
                new String[]{"foobar", "barfoo", "fumanchu"},
                new String[]{"foobar", "barfoo", "fumanchu"},
                Arrays.asList("foobar", "barfoo", "fumanchu"),
                Arrays.asList("foobar", "barfoo", "fumanchu"),
                Arrays.asList("foobar", "barfoo", "fumanchu")
        );

        WithDefaults newInstance = new WithDefaults();

        String xmlA = Jenkins.XSTREAM2.toXML(fromXMLNullingOut(defaultXml, existingInstance));
        String xmlB = Jenkins.XSTREAM2.toXML(fromXMLNullingOut(defaultXml, newInstance));
        String xmlC = Jenkins.XSTREAM2.toXML(fromXMLNullingOut(defaultXml, null));

        assertThat("Deserializing over an existing instance is the same as with no root", xmlA, is(xmlC));
        assertThat("Deserializing over an new instance is the same as with no root", xmlB, is(xmlC));
    }


    @Issue("JENKINS-21017")
    @Test
    public void unmarshalToDefault_empty() {
        String emptyXml = "<hudson.util.XStream2Test_-WithDefaults/>";

        WithDefaults existingInstance = new WithDefaults("foobar",
                "foobar",
                new String[]{"foobar", "barfoo", "fumanchu"},
                new String[]{"foobar", "barfoo", "fumanchu"},
                new String[]{"foobar", "barfoo", "fumanchu"},
                Arrays.asList("foobar", "barfoo", "fumanchu"),
                Arrays.asList("foobar", "barfoo", "fumanchu"),
                Arrays.asList("foobar", "barfoo", "fumanchu")
        );

        WithDefaults newInstance = new WithDefaults();

        Object reloaded = fromXMLNullingOut(emptyXml, existingInstance);
        assertSame(existingInstance, reloaded);
        String xmlA = Jenkins.XSTREAM2.toXML(reloaded);
        String xmlB = Jenkins.XSTREAM2.toXML(fromXMLNullingOut(emptyXml, newInstance));
        String xmlC = Jenkins.XSTREAM2.toXML(fromXMLNullingOut(emptyXml, null));

        assertThat("Deserializing over an existing instance is the same as with no root", xmlA, is(xmlC));
        assertThat("Deserializing over an new instance is the same as with no root", xmlB, is(xmlC));
    }

    private Object fromXMLNullingOut(String xml, Object root) {
        // Currently not offering a public convenience API for this:
        return Jenkins.XSTREAM2.unmarshal(XStream2.getDefaultDriver().createReader(new StringReader(xml)), root, null, true);
    }

    public static class WithDefaults {
        private String stringDefaultValue = "defaultValue";
        private String stringDefaultNull;
        private String[] arrayDefaultValue = { "first", "second" };
        private String[] arrayDefaultEmpty = new String[0];
        private String[] arrayDefaultNull;
        private List<String> listDefaultValue = new ArrayList<>(Arrays.asList("first", "second"));
        private List<String> listDefaultEmpty = new ArrayList<>();
        private List<String> listDefaultNull;

        public WithDefaults() {
        }

        public WithDefaults(String stringDefaultValue, String stringDefaultNull, String[] arrayDefaultValue,
                            String[] arrayDefaultEmpty, String[] arrayDefaultNull,
                            List<String> listDefaultValue, List<String> listDefaultEmpty,
                            List<String> listDefaultNull) {
            this.stringDefaultValue = stringDefaultValue;
            this.stringDefaultNull = stringDefaultNull;
            this.arrayDefaultValue = arrayDefaultValue == null ? null : arrayDefaultValue.clone();
            this.arrayDefaultEmpty = arrayDefaultEmpty == null ? null : arrayDefaultEmpty.clone();
            this.arrayDefaultNull = arrayDefaultNull == null ? null : arrayDefaultNull.clone();
            this.listDefaultValue = listDefaultValue == null ? null : new ArrayList<>(listDefaultValue);
            this.listDefaultEmpty = listDefaultEmpty == null ? null : new ArrayList<>(listDefaultEmpty);
            this.listDefaultNull = listDefaultNull == null ? null : new ArrayList<>(listDefaultNull);
        }

        public String getStringDefaultValue() {
            return stringDefaultValue;
        }

        public void setStringDefaultValue(String stringDefaultValue) {
            this.stringDefaultValue = stringDefaultValue;
        }

        public String getStringDefaultNull() {
            return stringDefaultNull;
        }

        public void setStringDefaultNull(String stringDefaultNull) {
            this.stringDefaultNull = stringDefaultNull;
        }

        public String[] getArrayDefaultValue() {
            return arrayDefaultValue;
        }

        public void setArrayDefaultValue(String[] arrayDefaultValue) {
            this.arrayDefaultValue = arrayDefaultValue;
        }

        public String[] getArrayDefaultEmpty() {
            return arrayDefaultEmpty;
        }

        public void setArrayDefaultEmpty(String[] arrayDefaultEmpty) {
            this.arrayDefaultEmpty = arrayDefaultEmpty;
        }

        public String[] getArrayDefaultNull() {
            return arrayDefaultNull;
        }

        public void setArrayDefaultNull(String[] arrayDefaultNull) {
            this.arrayDefaultNull = arrayDefaultNull;
        }

        public List<String> getListDefaultValue() {
            return listDefaultValue;
        }

        public void setListDefaultValue(List<String> listDefaultValue) {
            this.listDefaultValue = listDefaultValue;
        }

        public List<String> getListDefaultEmpty() {
            return listDefaultEmpty;
        }

        public void setListDefaultEmpty(List<String> listDefaultEmpty) {
            this.listDefaultEmpty = listDefaultEmpty;
        }

        public List<String> getListDefaultNull() {
            return listDefaultNull;
        }

        public void setListDefaultNull(List<String> listDefaultNull) {
            this.listDefaultNull = listDefaultNull;
        }
    }

    @Issue("SECURITY-503")
    @Test
    public void crashXstream() {
        assertThrows(XStreamException.class, () -> new XStream2().fromXML("<void/>"));
    }

    @Test
    public void annotations() {
        assertEquals("not registered, so sorry", "<hudson.util.XStream2Test_-C1/>", Jenkins.XSTREAM2.toXML(new C1()));
        assertEquals("manually registered", "<C-2/>", Jenkins.XSTREAM2.toXML(new C2()));
        assertEquals("manually processed", "<C-3/>", Jenkins.XSTREAM2.toXML(new C3()));
        assertThrows(CannotResolveClassException.class, () -> Jenkins.XSTREAM2.fromXML("<C-4/>"));

        Jenkins.XSTREAM2.processAnnotations(C5.class);
        assertThat("can deserialize from annotations so long as the processing happened at some point", Jenkins.XSTREAM2.fromXML("<C-5/>"), instanceOf(C5.class));
    }

    @XStreamAlias("C-1")
    public static final class C1 {}

    public static final class C2 {
        static {
            Jenkins.XSTREAM2.alias("C-2", C2.class);
        }
    }

    @XStreamAlias("C-3")
    public static final class C3 {
        static {
            Jenkins.XSTREAM2.processAnnotations(C3.class);
        }
    }

    @XStreamAlias("C-4")
    public static final class C4 {}

    @XStreamAlias("C-5")
    public static final class C5 {}

    @Issue("JENKINS-69129")
    @Test
    public void testEmoji() throws Exception {
        Bar bar;
        try (InputStream is = getClass().getResource("XStream2Emoji.xml").openStream()) {
            bar = (Bar) new XStream2().fromXML(is);
        }
        assertEquals("Fox 🦊", bar.s);
    }

    @Issue("JENKINS-69129")
    @Test
    public void testEmojiEscaped() throws Exception {
        Bar bar;
        try (InputStream is = getClass().getResource("XStream2EmojiEscaped.xml").openStream()) {
            bar = (Bar) new XStream2().fromXML(is);
        }
        assertEquals("Fox 🦊", bar.s);
    }

    @Issue("JENKINS-71182")
    @Test
    public void writeEmoji() throws Exception {
        Bar b = new Bar();
        String text = "Fox 🦊";
        b.s = text;
        StringWriter w = new StringWriter();
        XStream2 xs = new XStream2();
        xs.toXML(b, w);
        String xml = w.toString();
        assertThat(xml, is("<hudson.util.XStream2Test_-Bar>\n  <s>Fox 🦊</s>\n</hudson.util.XStream2Test_-Bar>"));
        b = (Bar) xs.fromXML(xml);
        assertEquals(text, b.s);
    }

    @Issue("JENKINS-71139")
    @Test
    public void nullsWithoutEncodingDeclaration() throws Exception {
        Bar b = new Bar();
        b.s = "x\u0000y";
        try {
            new XStream2().toXML(b, new StringWriter());
            fail("expected to fail fast; not supported to read either");
        } catch (RuntimeException x) {
            assertThat("cause is com.thoughtworks.xstream.io.StreamException: Invalid character 0x0 in XML stream", Functions.printThrowable(x), containsString("0x0"));
        }
    }

    @Issue("JENKINS-71139")
    @Test
    public void nullsWithEncodingDeclaration() throws Exception {
        Bar b = new Bar();
        b.s = "x\u0000y";
        try {
            new XStream2().toXMLUTF8(b, new ByteArrayOutputStream());
            fail("expected to fail fast; not supported to read either");
        } catch (RuntimeException x) {
            assertThat("cause is com.thoughtworks.xstream.io.StreamException: Invalid character 0x0 in XML stream", Functions.printThrowable(x), containsString("0x0"));
        }
    }

}
