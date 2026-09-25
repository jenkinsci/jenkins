/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.CollectionConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.security.InputManipulationException;
import hudson.model.Saveable;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.xstream.CriticalXStreamException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.jvnet.hudson.test.Issue;

/**
 * @author Kohsuke Kawaguchi
 */
class RobustReflectionConverterTest {
    private final boolean originalRecordFailures = RobustReflectionConverter.RECORD_FAILURES_FOR_ALL_AUTHENTICATIONS;

    static {
        Logger.getLogger(RobustReflectionConverter.class.getName()).setLevel(Level.OFF);
    }

    @BeforeEach
    void before() {
        RobustReflectionConverter.RECORD_FAILURES_FOR_ALL_AUTHENTICATIONS = true;
    }

    @AfterEach
    void after() {
        RobustReflectionConverter.RECORD_FAILURES_FOR_ALL_AUTHENTICATIONS = originalRecordFailures;
    }

    @Test
    void robustUnmarshalling() {
        Point p = read(new XStream2());
        assertEquals(1, p.x);
        assertEquals(2, p.y);
    }

    private Point read(XStream xs) {
        String clsName = Point.class.getName();
        return (Point) xs.fromXML("<" + clsName + "><x>1</x><y>2</y><z>3</z></" + clsName + '>');
    }

    @Test
    void ifWorkaroundNeeded() {
        XStream xs = new XStream(XStream2.getDefaultDriver());
        xs.allowTypes(new Class[] {Point.class});
        final ConversionException e = assertThrows(ConversionException.class, () -> read(xs));
        assertThat(e.getMessage(), containsString("No such field hudson.util.Point.z"));
    }

    @Test
    void classOwnership() {
        Enchufla s1 = new Enchufla();
        s1.number = 1;
        s1.direction = "North";
        Moonwalk s2 = new Moonwalk();
        s2.number = 2;
        s2.boot = new Boot();
        s2.lover = new Billy();
        Moonwalk s3 = new Moonwalk();
        s3.number = 3;
        s3.boot = new Boot();
        s3.jacket = new Jacket();
        s3.lover = new Jean();
        Bild b = new Bild();
        b.steppes = new Steppe[] {s1, s2, s3};
        Projekt p = new Projekt();
        p.bildz = new Bild[] {b};
        XStream xs = new XStream2(clazz -> {
            Owner o = clazz.getAnnotation(Owner.class);
            return o != null ? o.value() : null;
        });
        String prefix1 = RobustReflectionConverterTest.class.getName() + "_-";
        String prefix2 = RobustReflectionConverterTest.class.getName() + "$";
        assertEquals("<Projekt><bildz><Bild><steppes>"
                + "<Enchufla plugin='p1'><number>1</number><direction>North</direction></Enchufla>"
                // note no plugin='p2' on <boot/> since that would be redundant; <jacket/> is quiet even though unowned
                + "<Moonwalk plugin='p2'><number>2</number><boot/><lover class='Billy' plugin='p3'/></Moonwalk>"
                + "<Moonwalk plugin='p2'><number>3</number><boot/><jacket/><lover class='Jean' plugin='p4'/></Moonwalk>"
                + "</steppes></Bild></bildz></Projekt>",
                xs.toXML(p).replace(prefix1, "").replace(prefix2, "").replaceAll("\r?\n *", "").replace('"', '\''));
        Moonwalk s = (Moonwalk) xs.fromXML("<" + prefix1 + "Moonwalk plugin='p2'><lover class='" + prefix2 + "Billy' plugin='p3'/></" + prefix1 + "Moonwalk>");
        assertEquals(Billy.class, s.lover.getClass());
    }

    @Test
    void implicitCollection() {
        XStream2 xs = new XStream2();
        xs.alias("hold", Hold.class);
        xs.addImplicitCollection(Hold.class, "items", "item", String.class);
        Hold h = (Hold) xs.fromXML("<hold><item>a</item><item>b</item></hold>");
        assertThat(h.items, Matchers.containsInAnyOrder("a", "b"));
        assertEquals("""
                <hold>
                  <item>a</item>
                  <item>b</item>
                </hold>""", xs.toXML(h));
    }

    @Disabled("Throws an NPE in writeValueToImplicitCollection. Issue has existed since RobustReflectionConverter was created.")
    @Test
    void implicitCollectionsAllowNullElements() {
        XStream2 xs = new XStream2();
        xs.alias("hold", Hold.class);
        xs.addImplicitCollection(Hold.class, "items", "item", String.class);
        Hold h = (Hold) xs.fromXML("<hold><null/><item>b</item></hold>");
        assertThat(h.items, Matchers.containsInAnyOrder(null, "b"));
        assertEquals("""
                <hold>
                  <null/>
                  <item>b</item>
                </hold>""", xs.toXML(h));
    }

    @Issue("JENKINS-63343")
    @Test
    void robustAgainstImplicitCollectionElementsWithBadTypes() {
        XStream2 xs = new XStream2();
        xs.alias("hold", Hold.class);
        // Note that the fix only matters for `addImplicitCollection` overloads like the following where the element type is not provided.
        xs.addImplicitCollection(Hold.class, "items");
        Hold h = (Hold) xs.fromXML(
                """
                <hold>
                  <int>123</int>
                  <string>abc</string>
                  <int>456</int>
                  <string>def</string>
                </hold>
                """);
        assertThat(h.items, equalTo(List.of("abc", "def")));
    }

    public static class Hold implements Saveable {
        List<String> items;

        @Override
        public void save() {
            // We only implement Saveable so that RobustReflectionConverter logs deserialization problems.
        }
    }

    @Test
    void implicitCollectionRawtypes() {
        XStream2 xs = new XStream2();
        xs.alias("hold", HoldRaw.class);
        xs.addImplicitCollection(HoldRaw.class, "items");
        var h = (HoldRaw) xs.fromXML(
                """
                <hold>
                  <int>123</int>
                  <string>abc</string>
                  <int>456</int>
                  <string>def</string>
                </hold>
                """);
        assertThat(h.items, equalTo(List.of(123, "abc", 456, "def")));
    }

    public static class HoldRaw implements Saveable {
        List items;

        @Override
        public void save() throws IOException {
            // We only implement Saveable so that RobustReflectionConverter logs deserialization problems.
        }
    }

    @Retention(RetentionPolicy.RUNTIME) @interface Owner {
        String value();
    }

    public static class Projekt {
        Bild[] bildz;
    }

    public static class Bild {
        Steppe[] steppes;
    }

    public abstract static class Steppe {
        int number;
    }

    @Owner("p1")
    public static class Enchufla extends Steppe {
        String direction;
    }

    @Owner("p2")
    public static class Moonwalk extends Steppe {
        Boot boot;
        Jacket jacket;
        Lover lover;
    }

    @Owner("p2")
    public static class Boot {}

    public static class Jacket {}

    @Owner("p2")
    public abstract static class Lover {}

    @Owner("p3")
    public static class Billy extends Lover {}

    @Owner("p4")
    public static class Jean extends Lover {}

    @Test
    @Timeout(value = 30 * 1000, unit = TimeUnit.MILLISECONDS)
    @Issue("SECURITY-2602")
    void robustDoesNotSwallowDosException() {
        XStream2 xstream2 = new XStream2();

        Set<Object> set = preparePayload();
        ParentItem parentItem = new ParentItem();
        parentItem.childList = new ArrayList<>();
        parentItem.childList.add(new ChildItem());
        ChildItem secondChild = new ChildItem();
        secondChild.mySet = set;
        parentItem.childList.add(secondChild);
        parentItem.childList.add(new ChildItem());

        final String xml = xstream2.toXML(parentItem);

        CriticalXStreamException e = assertThrows(CriticalXStreamException.class, () -> xstream2.fromXML(xml));
        Throwable cause = e.getCause();
        assertNotNull(cause);
        assertThat(cause, instanceOf(InputManipulationException.class));
        InputManipulationException ime = (InputManipulationException) cause;
        assertTrue(ime.getMessage().contains("exceeds 5 seconds"), "Limit expected in message");
    }

    @Test
    @Timeout(value = 30 * 1000, unit = TimeUnit.MILLISECONDS)
    void customConverter_useDefaultXStreamException() {
        XStream2 xstream2 = new XStream2();
        xstream2.registerConverter(new CustomSet.ConverterImpl(xstream2.getMapper()));

        CustomSet customSet = preparePayloadUsingCustomSet();

        // Will use the ConverterImpl without going with RobustReflectionConverter
        final String xml = xstream2.toXML(customSet);

        InputManipulationException e = assertThrows(InputManipulationException.class, () -> xstream2.fromXML(xml));
        assertTrue(e.getMessage().contains("exceeds 5 seconds"), "Limit expected in message");
    }

    @Test
    @Timeout(value = 30 * 1000, unit = TimeUnit.MILLISECONDS)
    @Issue("SECURITY-2602")
    void customConverter_wrapped_useCriticalXStreamException() {
        XStream2 xstream2 = new XStream2();
        xstream2.registerConverter(new CustomSet.ConverterImpl(xstream2.getMapper())); // TODO Fix test so it does not pass without this

        CustomSet customSet = preparePayloadUsingCustomSet();
        // enforce the use of RobustReflectionConverter
        ParentObject parentObject = new ParentObject();
        parentObject.customSet1 = customSet;
        parentObject.customSet2 = customSet;
        parentObject.customSet3 = customSet;

        final String xml = xstream2.toXML(parentObject);

        // Without the InputManipulationException catch in RobustReflectionConverter,
        // the parsing is continued despite the DoS prevention being triggered due to the robustness
        CriticalXStreamException e = assertThrows(CriticalXStreamException.class, () -> xstream2.fromXML(xml));
        Throwable cause = e.getCause();
        assertNotNull(cause);
        assertThat(cause, instanceOf(InputManipulationException.class));
        InputManipulationException ime = (InputManipulationException) cause;
        assertTrue(ime.getMessage().contains("exceeds 5 seconds"), "Limit expected in message");
    }

    private Set<Object> preparePayload() {
        /*
            On a i7-1185G7@3.00GHz (2021)
            Full test time:
            max=27 => ~11s
            max=28 => ~22s
            max=29 => ~47s
            max=30 => >1m30
            max=32 => est. 6m

            With the protection in place, each test is taking ~15 seconds before the protection triggers
        */

        final Set<Object> set = new HashSet<>();
        Set<Object> s1 = set;
        Set<Object> s2 = new HashSet<>();
        for (int i = 0; i < 32; i++) {
            final Set<Object> t1 = new HashSet<>();
            final Set<Object> t2 = new HashSet<>();
            t1.add("a");
            t2.add("b");
            s1.add(t1);
            s1.add(t2);
            s2.add(t2);
            s2.add(t1);
            s1 = t1;
            s2 = t2;
        }
        return set;
    }

    private CustomSet preparePayloadUsingCustomSet() {
        /*
            On a i7-1185G7@3.00GHz (2021)
            Full test time:
            max=24 => ~3s
            max=25 => ~4s
            max=26 => ~7s
            max=27 => ~13s // trigger DoS prevention
            max=28 => ~25s
            max=29 => ~50s
            max=29 => ~1m40s
            max=30 => >3m0

            With the protection in place, each test is taking ~15 seconds before the protection triggers
        */
        final CustomSet customSet = new CustomSet();
        CustomSet s1 = customSet;
        CustomSet s2 = new CustomSet();
        for (int i = 0; i < 30; i++) {
            final CustomSet t1 = new CustomSet();
            final CustomSet t2 = new CustomSet();
            t1.internalSet.add("a");
            t2.internalSet.add("b");
            s1.internalSet.add(t1);
            s1.internalSet.add(t2);
            s2.internalSet.add(t2);
            s2.internalSet.add(t1);
            s1 = t1;
            s2 = t2;
        }
        return customSet;
    }

    static class ParentItem {
        List<ChildItem> childList;
    }

    static class ChildItem {
        Set<Object> mySet;
    }

    static class ParentObject {
        // cannot use List/Set to avoid RobustCollectionConverter
        CustomSet customSet1;
        CustomSet customSet2;
        CustomSet customSet3;
    }

    static class CustomSet {
        Set<Object> internalSet;

        CustomSet() {
            this.internalSet = new HashSet<>();
        }

        CustomSet(Set<Object> internalSet) {
            this.internalSet = internalSet;
        }

        @Override
        public int hashCode() {
            return internalSet == null ? 1 : internalSet.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CustomSet customSet = (CustomSet) o;
            return Objects.equals(internalSet, customSet.internalSet);
        }

        static class ConverterImpl extends CollectionConverter {
            ConverterImpl(Mapper mapper) {
                super(mapper);
            }

            @Override
            public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
                CustomSet customSet = (CustomSet) source;
                for (Object item : customSet.internalSet) {
                    writeCompleteItem(item, context, writer);
                }
            }

            @Override
            @SuppressWarnings("unchecked")
            public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
                return new CustomSet((Set<Object>) super.unmarshal(reader, context));
            }

            @Override
            public boolean canConvert(Class type) {
                return type == CustomSet.class;
            }

            @Override
            protected Object createCollection(Class type) {
                return new HashSet<>();
            }
        }
    }
}
