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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.xstream.CriticalXStreamException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.jvnet.hudson.test.Issue;
import org.jvnet.tiger_types.Types;

/**
 * @author Kohsuke Kawaguchi
 */
class RobustReflectionConverterTest {
    private final boolean originalRecordFailures = RobustReflectionConverter.RECORD_FAILURES_FOR_ALL_AUTHENTICATIONS;
    private final boolean originalSkipFieldCheck = RobustReflectionConverter.SKIP_FIELD_VALUE_ELEMENT_TYPE_CHECK;
    private final boolean originalAcceptNonIterable = RobustReflectionConverter.ACCEPT_NON_ITERABLE_COLLECTIONS;

    static {
        Logger.getLogger(RobustReflectionConverter.class.getName()).setLevel(Level.OFF);
    }

    @BeforeEach
    void before() {
        RobustReflectionConverter.RECORD_FAILURES_FOR_ALL_AUTHENTICATIONS = true;
        RobustReflectionConverter.SKIP_FIELD_VALUE_ELEMENT_TYPE_CHECK = false;
        RobustReflectionConverter.ACCEPT_NON_ITERABLE_COLLECTIONS = false;
        ConstructionDetector.wasConstructed.set(false);
    }

    @AfterEach
    void after() {
        RobustReflectionConverter.RECORD_FAILURES_FOR_ALL_AUTHENTICATIONS = originalRecordFailures;
        RobustReflectionConverter.SKIP_FIELD_VALUE_ELEMENT_TYPE_CHECK = originalSkipFieldCheck;
        RobustReflectionConverter.ACCEPT_NON_ITERABLE_COLLECTIONS = originalAcceptNonIterable;
        ConstructionDetector.wasConstructed.set(false);
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
        assertThat(h.items, containsInAnyOrder("a", "b"));
        assertEquals("""
                <hold>
                  <item>a</item>
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

    public static class ConstructionDetector implements Serializable {
        static final AtomicBoolean wasConstructed = new AtomicBoolean(false);
        String value;

        @SuppressWarnings("unused")
        private Object readResolve() {
            wasConstructed.set(true);
            return this;
        }
    }

    public static class HoldTyped implements Saveable {
        List<String> items;
        Set<String> set;
        Map<String, String> map;
        CopyOnWriteList<String> cowl;

        @Override
        public void save() {}
    }

    private static XStream2 newXStream() {
        XStream2 xs = new XStream2();
        xs.alias("hold", HoldTyped.class);
        xs.alias("detector", ConstructionDetector.class);
        return xs;
    }

    @Test
    @Issue("SECURITY-4225")
    void catchAllRejectsWrongTypedElementInArrayList() {
        var h = (HoldTyped) newXStream().fromXML(
                "<hold><items class='java.util.ArrayList'><detector><value>bad</value></detector></items></hold>");
        assertNotNull(h.items, "RobustCollectionConverter already dropped the bad element; catch-all sees empty list");
        assertTrue(h.items.isEmpty());
    }

    @Test
    @Issue("SECURITY-4225")
    void catchAllRejectsWrongTypedElementInImmutableList() {
        var h = (HoldTyped) newXStream().fromXML(
                "<hold><items class='com.google.common.collect.ImmutableList'><detector><value>bad</value></detector></items></hold>");
        assertThat(h.items, nullValue());
        assertTrue(ConstructionDetector.wasConstructed.get());
    }

    @Test
    @Issue("SECURITY-4225")
    void catchAllRejectsWrongTypedElementInImmutableSet() {
        var h = (HoldTyped) newXStream().fromXML(
                "<hold><set class='com.google.common.collect.ImmutableSet'><detector><value>bad</value></detector></set></hold>");
        assertThat(h.set, nullValue());
    }

    @Test
    @Issue("SECURITY-4225")
    void catchAllRejectsWrongTypedElementInImmutableSortedSet() {
        var h = (HoldTyped) newXStream().fromXML(
                "<hold><set class='com.google.common.collect.ImmutableSortedSet'><detector><value>bad</value></detector></set></hold>");
        assertThat(h.set, nullValue());
    }

    @Test
    @Issue("SECURITY-4225")
    void catchAllRejectsWrongTypedElementInSingletonList() {
        var h = (HoldTyped) newXStream().fromXML(
                "<hold><items class='java.util.Collections$SingletonList'><detector><value>bad</value></detector></items></hold>");
        assertThat(h.items, nullValue());
        assertTrue(ConstructionDetector.wasConstructed.get());
    }

    @Test
    @Issue("SECURITY-4225")
    void catchAllRejectsWrongTypedValueInImmutableMap() {
        var h = (HoldTyped) newXStream().fromXML(
                "<hold><map class='com.google.common.collect.ImmutableMap'>"
                + "<entry><string>k</string><detector><value>bad</value></detector></entry>"
                + "</map></hold>");
        assertThat(h.map, nullValue());
    }

    @Test
    @Issue("SECURITY-4225")
    void catchAllRejectsWrongTypedKeyInImmutableMap() {
        var h = (HoldTyped) newXStream().fromXML(
                "<hold><map class='com.google.common.collect.ImmutableMap'>"
                + "<entry><detector><value>bad</value></detector><string>v</string></entry>"
                + "</map></hold>");
        assertThat(h.map, nullValue());
    }

    @Test
    @Issue("SECURITY-4225")
    void catchAllRejectsWrongTypedValueInCopyOnWriteMapHash() {
        var h = (HoldTyped) newXStream().fromXML(
                "<hold><map class='hudson.util.CopyOnWriteMap$Hash'>"
                + "<entry><string>k</string><detector><value>bad</value></detector></entry>"
                + "</map></hold>");
        assertThat(h.map, nullValue());
    }

    @Test
    @Issue("SECURITY-4225")
    void catchAllRejectsWrongTypedValueInHashMap() {
        var h = (HoldTyped) newXStream().fromXML(
                "<hold><map><entry><string>k</string><detector><value>bad</value></detector></entry></map></hold>");
        assertNotNull(h.map);
        assertTrue(h.map.isEmpty());
    }

    @Test
    @Issue("SECURITY-4225")
    void catchAllRejectsWrongTypedValueInSingletonMap() {
        var h = (HoldTyped) newXStream().fromXML(
                "<hold><map class='java.util.Collections$SingletonMap'>"
                + "<entry><string>k</string><detector><value>bad</value></detector></entry>"
                + "</map></hold>");
        assertThat(h.map, nullValue());
    }

    @Test
    @Issue("SECURITY-4225")
    void validDataAcceptedInImmutableList() {
        var h = (HoldTyped) newXStream().fromXML(
                "<hold><items class='com.google.common.collect.ImmutableList'><string>abc</string></items></hold>");
        assertThat(h.items, equalTo(List.of("abc")));
    }

    @Test
    @Issue("SECURITY-4225")
    void validDataAcceptedInImmutableSet() {
        var h = (HoldTyped) newXStream().fromXML(
                "<hold><set class='com.google.common.collect.ImmutableSet'><string>abc</string></set></hold>");
        assertThat(h.set, equalTo(Set.of("abc")));
    }

    @Test
    @Issue("SECURITY-4225")
    void validDataAcceptedInImmutableMap() {
        var h = (HoldTyped) newXStream().fromXML(
                "<hold><map class='com.google.common.collect.ImmutableMap'>"
                + "<entry><string>k</string><string>v</string></entry>"
                + "</map></hold>");
        assertThat(h.map, equalTo(Map.of("k", "v")));
    }

    @Test
    @Issue("SECURITY-4225")
    void validDataAcceptedInCopyOnWriteMapHash() {
        var h = (HoldTyped) newXStream().fromXML(
                "<hold><map class='hudson.util.CopyOnWriteMap$Hash'>"
                + "<entry><string>k</string><string>v</string></entry>"
                + "</map></hold>");
        assertNotNull(h.map);
        assertThat(h.map.get("k"), equalTo("v"));
    }

    @Test
    @Issue("SECURITY-4225")
    void escapeHatchDisablesCatchAll() {
        RobustReflectionConverter.SKIP_FIELD_VALUE_ELEMENT_TYPE_CHECK = true;
        var h = (HoldTyped) newXStream().fromXML(
                "<hold><items class='com.google.common.collect.ImmutableList'><detector><value>bad</value></detector></items></hold>");
        assertNotNull(h.items);
        assertFalse(h.items.isEmpty());
        assertTrue(ConstructionDetector.wasConstructed.get());
    }

    @Test
    @Issue("SECURITY-4225")
    void escapeHatchDisablesCatchAllForSingletonList() {
        RobustReflectionConverter.SKIP_FIELD_VALUE_ELEMENT_TYPE_CHECK = true;
        var h = (HoldTyped) newXStream().fromXML(
                "<hold><items class='java.util.Collections$SingletonList'><detector><value>bad</value></detector></items></hold>");
        assertNotNull(h.items);
        assertTrue(ConstructionDetector.wasConstructed.get());
    }

    @Test
    @Issue("SECURITY-4225")
    void catchAllRejectsEntireImmutableListWithMixedElements() {
        var h = (HoldTyped) newXStream().fromXML(
                """
                <hold><items class='com.google.common.collect.ImmutableList'>
                  <string>good</string>
                  <detector><value>bad</value></detector>
                  <string>also-good</string>
                </items></hold>""");
        assertThat(h.items, nullValue());
    }

    @Test
    @Issue("SECURITY-4225")
    void perConverterCheckDropsIndividualBadElements() {
        var h = (HoldTyped) newXStream().fromXML(
                """
                <hold><items class='java.util.ArrayList'>
                  <string>good</string>
                  <detector><value>bad</value></detector>
                  <string>also-good</string>
                </items></hold>""");
        assertNotNull(h.items);
        assertThat("RobustCollectionConverter drops the bad element individually", h.items, equalTo(List.of("good", "also-good")));
        assertTrue(ConstructionDetector.wasConstructed.get(),
                "RobustCollectionConverter constructs the object before checking its type");
    }

    public static class DecoupledList<T> extends ArrayList<String> {
    }

    @Test
    void extractElementTypeResolvesClassHierarchyNotFieldTypeParam() {
        // DecoupledList<T> extends ArrayList<String>: the element type is String, not T
        assertThat(RobustReflectionConverter.extractElementType(
                HoldDecoupled.class.getDeclaredFields()[0].getGenericType(), Collection.class),
                equalTo(String.class));
    }

    public static class HoldDecoupled {
        DecoupledList<Integer> items;
    }

    public static class ThreadedList<T> extends ArrayList<T> {
    }

    @Test
    @Issue("SECURITY-4225")
    void extractElementTypeResolvesThreadedTypeParam() {
        // ThreadedList<T> extends ArrayList<T>: with field ThreadedList<String>, element type is String
        assertThat(RobustReflectionConverter.extractElementType(
                HoldThreaded.class.getDeclaredFields()[0].getGenericType(), Collection.class),
                equalTo(String.class));
    }

    public static class HoldThreaded {
        ThreadedList<String> items;
    }

    public static class HoldRawList implements Saveable {
        @SuppressWarnings("rawtypes")
        List items;

        @Override
        public void save() {}
    }

    @Test
    @Issue("SECURITY-4225")
    void catchAllSkipsRawTypeFields() {
        XStream2 xs = newXStream();
        xs.alias("hr", HoldRawList.class);
        var h = (HoldRawList) xs.fromXML(
                "<hr><items><int>42</int><string>abc</string></items></hr>");
        assertNotNull(h.items);
        assertThat(h.items.size(), equalTo(2));
    }

    public static class HoldNumbers implements Saveable {
        List<Number> numbers;

        @Override
        public void save() {}
    }

    @Test
    @Issue("SECURITY-4225")
    void catchAllAcceptsSubtypesOfDeclaredElementType() {
        XStream2 xs = newXStream();
        xs.alias("hn", HoldNumbers.class);
        var h = (HoldNumbers) xs.fromXML(
                """
                <hn><numbers>
                  <int>1</int>
                  <long>2</long>
                  <double>3.0</double>
                  <float>4.0</float>
                  <short>5</short>
                  <byte>6</byte>
                </numbers></hn>""");
        assertNotNull(h.numbers);
        assertThat(h.numbers.size(), equalTo(6));
        assertThat(h.numbers.get(0), instanceOf(Integer.class));
        assertThat(h.numbers.get(1), instanceOf(Long.class));
    }

    public static class HoldSerializables implements Saveable {
        List<Serializable> items;

        @Override
        public void save() {}
    }

    @Test
    @Issue("SECURITY-4225")
    void catchAllAcceptsInterfaceSubtypes() {
        XStream2 xs = newXStream();
        xs.alias("hs", HoldSerializables.class);
        var h = (HoldSerializables) xs.fromXML(
                "<hs><items><string>abc</string><int>42</int></items></hs>");
        assertNotNull(h.items);
        assertThat(h.items.size(), equalTo(2));
    }

    @Test
    @Issue("SECURITY-4225")
    void catchAllRejectsNonSerializable() {
        XStream2 xs = newXStream();
        xs.alias("hs", HoldSerializables.class);
        xs.alias("non-ser", NonSerializableType.class);
        var h = (HoldSerializables) xs.fromXML(
                "<hs><items><non-ser/></items></hs>");
        assertNotNull(h.items, "RobustCollectionConverter already dropped the bad element; catch-all sees empty list");
        assertTrue(h.items.isEmpty());
    }

    public static class NonSerializableType {
    }

    public static class HoldNestedList implements Saveable {
        List<List<String>> nested;

        @Override
        public void save() {}
    }

    @Test
    @Issue("SECURITY-4225")
    void catchAllChecksOuterElementTypeOnly() {
        XStream2 xs = newXStream();
        xs.alias("hn", HoldNestedList.class);
        var h = (HoldNestedList) xs.fromXML(
                """
                <hn><nested>
                  <list><int>1</int><int>2</int></list>
                  <list><string>a</string></list>
                </nested></hn>""");
        assertNotNull(h.nested);
        assertThat(h.nested.size(), equalTo(2));
    }

    @Test
    @Issue("SECURITY-4225")
    void catchAllRejectsNonListElementInNestedList() {
        XStream2 xs = newXStream();
        xs.alias("hn", HoldNestedList.class);
        var h = (HoldNestedList) xs.fromXML(
                "<hn><nested><string>not-a-list</string></nested></hn>");
        assertNotNull(h.nested, "RobustCollectionConverter already dropped the non-List element");
        assertTrue(h.nested.isEmpty());
    }

    public static class DecoupledMap<K, V> extends HashMap<String, Integer> {
    }

    public static class HoldDecoupledMap {
        DecoupledMap<Long, Boolean> map;
    }

    public static class ThreadedMap<K, V> extends HashMap<K, V> {
    }

    public static class HoldThreadedMap {
        ThreadedMap<String, Integer> map;
    }

    @Test
    @Issue("SECURITY-4225")
    void extractElementTypeResolvesDecoupledMapKeyType() throws Exception {
        // DecoupledMap<Long, Boolean> extends HashMap<String, Integer>: key type is String, not Long
        Type gt = HoldDecoupledMap.class.getDeclaredField("map").getGenericType();
        var baseType = Types.getBaseClass(gt, Map.class);
        assertThat(Types.erasure(Types.getTypeArgument(baseType, 0, Object.class)), equalTo(String.class));
        assertThat(Types.erasure(Types.getTypeArgument(baseType, 1, Object.class)), equalTo(Integer.class));
    }

    @Test
    @Issue("SECURITY-4225")
    void extractElementTypeResolvesThreadedMapKeyType() throws Exception {
        // ThreadedMap<String, Integer> extends HashMap<K, V>: key type is String, value type is Integer
        Type gt = HoldThreadedMap.class.getDeclaredField("map").getGenericType();
        var baseType = Types.getBaseClass(gt, Map.class);
        assertThat(Types.erasure(Types.getTypeArgument(baseType, 0, Object.class)), equalTo(String.class));
        assertThat(Types.erasure(Types.getTypeArgument(baseType, 1, Object.class)), equalTo(Integer.class));
    }

    @Test
    @Issue("SECURITY-4225")
    void catchAllSkipsRawMapFields() {
        XStream2 xs = newXStream();
        xs.alias("hrm", HoldRawMap.class);
        var h = (HoldRawMap) xs.fromXML(
                "<hrm><map><entry><int>1</int><boolean>true</boolean></entry></map></hrm>");
        assertNotNull(h.map);
        assertThat(h.map.size(), equalTo(1));
    }

    public static class HoldRawMap implements Saveable {
        @SuppressWarnings("rawtypes")
        Map map;

        @Override
        public void save() {}
    }

    @Test
    @Issue("SECURITY-4225")
    void cowlConverterDropsBadElement() {
        var h = (HoldTyped) newXStream().fromXML(
                "<hold><cowl><detector><value>bad</value></detector></cowl></hold>");
        assertNotNull(h.cowl, "CopyOnWriteList.ConverterImpl drops the bad element; catch-all sees empty COWL");
        assertThat(h.cowl.getView(), equalTo(List.of()));
    }

    @Test
    @Issue("SECURITY-4225")
    void validDataAcceptedInCOWL() {
        var h = (HoldTyped) newXStream().fromXML(
                "<hold><cowl><string>abc</string><string>def</string></cowl></hold>");
        assertNotNull(h.cowl);
        assertThat(h.cowl.getView(), equalTo(List.of("abc", "def")));
    }

    @Test
    @Issue("SECURITY-4225")
    void cowlConverterDropsBadElementFromMixed() {
        var h = (HoldTyped) newXStream().fromXML(
                "<hold><cowl><string>good</string><detector><value>bad</value></detector></cowl></hold>");
        assertNotNull(h.cowl, "CopyOnWriteList.ConverterImpl drops the bad element individually");
        assertThat(h.cowl.getView(), equalTo(List.of("good")));
    }

    public static class DecoupledCOWL<T> extends CopyOnWriteList<String> {
    }

    public static class HoldDecoupledCOWL {
        DecoupledCOWL<Integer> items;
    }

    public static class ThreadedCOWL<T> extends CopyOnWriteList<T> {
    }

    public static class HoldThreadedCOWL {
        ThreadedCOWL<String> items;
    }

    @Test
    @Issue("SECURITY-4225")
    void extractElementTypeResolvesCOWLDecoupledType() {
        // DecoupledCOWL<Integer> extends CopyOnWriteList<String>: element type is String, not Integer
        assertThat(RobustReflectionConverter.extractElementType(
                HoldDecoupledCOWL.class.getDeclaredFields()[0].getGenericType(), CopyOnWriteList.class),
                equalTo(String.class));
    }

    @Test
    @Issue("SECURITY-4225")
    void extractElementTypeResolvesCOWLThreadedType() {
        // ThreadedCOWL<String> extends CopyOnWriteList<T>: element type is String
        assertThat(RobustReflectionConverter.extractElementType(
                HoldThreadedCOWL.class.getDeclaredFields()[0].getGenericType(), CopyOnWriteList.class),
                equalTo(String.class));
    }

    @Test
    @Issue("SECURITY-4225")
    void catchAllSkipsRawCOWLFields() {
        XStream2 xs = newXStream();
        xs.alias("hrc", HoldRawCOWL.class);
        var h = (HoldRawCOWL) xs.fromXML(
                "<hrc><items><int>42</int><string>abc</string></items></hrc>");
        assertNotNull(h.items);
        assertThat(h.items.getView().size(), equalTo(2));
    }

    public static class HoldRawCOWL implements Saveable {
        @SuppressWarnings("rawtypes")
        CopyOnWriteList items;

        @Override
        public void save() {}
    }

    /**
     * A CopyOnWriteList subclass. Since no converter is registered for it, XStream uses
     * RobustReflectionConverter which creates the object via reflection without calling
     * CopyOnWriteList's constructor. The internal {@code core} field stays null, making
     * the list non-iterable.
     */
    public static class BrokenCOWLSubclass<T> extends CopyOnWriteList<T> {
    }

    public static class HoldBrokenCOWL implements Saveable {
        BrokenCOWLSubclass<String> items;

        @Override
        public void save() {}
    }

    @Test
    @Issue("SECURITY-4225")
    void nonIterableCollectionIsRejected() {
        XStream2 xs = newXStream();
        xs.alias("hb", HoldBrokenCOWL.class);
        // The BrokenCOWLSubclass is deserialized by RobustReflectionConverter (no dedicated converter),
        // leaving core=null. The catch-all cannot iterate it and rejects the field.
        var h = (HoldBrokenCOWL) xs.fromXML("<hb><items/></hb>");
        assertThat(h.items, nullValue());
    }

    @Test
    @Issue("SECURITY-4225")
    void nonIterableCollectionAcceptedWithEscapeHatch() {
        RobustReflectionConverter.ACCEPT_NON_ITERABLE_COLLECTIONS = true;
        XStream2 xs = newXStream();
        xs.alias("hb", HoldBrokenCOWL.class);
        var h = (HoldBrokenCOWL) xs.fromXML("<hb><items/></hb>");
        assertNotNull(h.items, "With escape hatch, broken collection is accepted");
        assertThrows(NullPointerException.class, () -> h.items.size());
    }

    @Test
    @Issue("SECURITY-4225")
    void nonIterableEscapeHatchDoesNotSuppressTypeMismatch() {
        RobustReflectionConverter.ACCEPT_NON_ITERABLE_COLLECTIONS = true;
        var h = (HoldTyped) newXStream().fromXML(
                "<hold><items class='com.google.common.collect.ImmutableList'><detector><value>bad</value></detector></items></hold>");
        assertThat("Type mismatch is still rejected even with the non-iterable escape hatch enabled",
                h.items, nullValue());
    }

    public static class BrokenMap<K, V> implements Map<K, V> {
        @Override public Set<K> keySet() {
            throw new NullPointerException("broken");
        }

        @Override public Collection<V> values() {
            throw new NullPointerException("broken");
        }

        @Override public int size() {
            return 0;
        }

        @Override public boolean isEmpty() {
            return true;
        }

        @Override public boolean containsKey(Object key) {
            return false;
        }

        @Override public boolean containsValue(Object value) {
            return false;
        }

        @Override public V get(Object key) {
            return null;
        }

        @Override public V put(K key, V value) {
            return null;
        }

        @Override public V remove(Object key) {
            return null;
        }

        @Override public void putAll(Map<? extends K, ? extends V> m) {
        }

        @Override public void clear() {
        }

        @Override public Set<Entry<K, V>> entrySet() {
            return Set.of();
        }
    }

    public static class HoldBrokenMap implements Saveable {
        BrokenMap<String, String> map;

        @Override
        public void save() {}
    }

    @Test
    @Issue("SECURITY-4225")
    void nonIterableMapIsRejected() {
        XStream2 xs = newXStream();
        xs.alias("hbm", HoldBrokenMap.class);
        var h = (HoldBrokenMap) xs.fromXML("<hbm><map/></hbm>");
        assertThat(h.map, nullValue());
    }

    @Test
    @Issue("SECURITY-4225")
    void nonIterableMapAcceptedWithEscapeHatch() {
        RobustReflectionConverter.ACCEPT_NON_ITERABLE_COLLECTIONS = true;
        XStream2 xs = newXStream();
        xs.alias("hbm", HoldBrokenMap.class);
        var h = (HoldBrokenMap) xs.fromXML("<hbm><map/></hbm>");
        assertThat(h.map, not(nullValue()));
    }

    @Test
    @Issue("SECURITY-4225")
    void validationBudgetRejectsOversizedCollection() {
        int originalLimit = RobustReflectionConverter.FIELD_VALUE_ELEMENT_CHECK_LIMIT;
        try {
            RobustReflectionConverter.FIELD_VALUE_ELEMENT_CHECK_LIMIT = 3;
            XStream2 xs = newXStream();
            // 5 valid elements, but budget is 3 — field must be rejected
            assertThrows(CriticalXStreamException.class, () -> xs.fromXML(
                    "<hold><items class='com.google.common.collect.ImmutableList'>"
                    + "<string>a</string><string>b</string><string>c</string><string>d</string><string>e</string>"
                    + "</items></hold>"));
        } finally {
            RobustReflectionConverter.FIELD_VALUE_ELEMENT_CHECK_LIMIT = originalLimit;
        }
    }

    @Test
    @Issue("SECURITY-4225")
    void validationBudgetAcceptsWithinLimit() {
        int originalLimit = RobustReflectionConverter.FIELD_VALUE_ELEMENT_CHECK_LIMIT;
        try {
            RobustReflectionConverter.FIELD_VALUE_ELEMENT_CHECK_LIMIT = 10;
            XStream2 xs = newXStream();
            var h = (HoldTyped) xs.fromXML(
                    "<hold><items class='com.google.common.collect.ImmutableList'>"
                    + "<string>a</string><string>b</string><string>c</string>"
                    + "</items></hold>");
            assertThat(h.items, equalTo(List.of("a", "b", "c")));
        } finally {
            RobustReflectionConverter.FIELD_VALUE_ELEMENT_CHECK_LIMIT = originalLimit;
        }
    }

    @Test
    @Issue("SECURITY-4225")
    void validationBudgetIsCumulativeAcrossFields() {
        int originalLimit = RobustReflectionConverter.FIELD_VALUE_ELEMENT_CHECK_LIMIT;
        try {
            RobustReflectionConverter.FIELD_VALUE_ELEMENT_CHECK_LIMIT = 5;
            XStream2 xs = newXStream();
            // Two fields: items (3 elements) + set (3 elements) = 6 total, budget is 5
            assertThrows(CriticalXStreamException.class, () -> xs.fromXML(
                    "<hold>"
                    + "<items><string>a</string><string>b</string><string>c</string></items>"
                    + "<set><string>x</string><string>y</string><string>z</string></set>"
                    + "</hold>"));
        } finally {
            RobustReflectionConverter.FIELD_VALUE_ELEMENT_CHECK_LIMIT = originalLimit;
        }
    }

    private static String immutableListXml(int count) {
        var sb = new StringBuilder("<hold><items class='com.google.common.collect.ImmutableList'>");
        for (int i = 0; i < count; i++) {
            sb.append("<string>e").append(i).append("</string>");
        }
        sb.append("</items></hold>");
        return sb.toString();
    }

    @Test
    @Issue("SECURITY-4225")
    void largeCollectionBelowDefaultLimitAccepted() {
        var h = (HoldTyped) newXStream().fromXML(immutableListXml(999_999));
        assertNotNull(h.items);
        assertThat(h.items.size(), equalTo(999_999));
    }

    @Test
    @Issue("SECURITY-4225")
    void largeCollectionAboveDefaultLimitRejected() {
        assertThrows(CriticalXStreamException.class, () -> newXStream().fromXML(immutableListXml(1_000_001)));
    }

    public static class ParentWithList implements Saveable {
        List<Integer> items;

        @Override
        public void save() {}
    }

    public static class ChildHidingList extends ParentWithList implements Saveable {
        List<String> items;

        @Override
        public void save() {}
    }

    @Test
    @Issue("SECURITY-4225")
    void hiddenFieldRoundTripPreservesParentFieldType() {
        XStream2 xs = newXStream();
        xs.alias("child", ChildHidingList.class);
        var h = (ChildHidingList) xs.fromXML(
                "<child>"
                + "<items defined-in='hudson.util.RobustReflectionConverterTest$ParentWithList'><int>1</int><int>2</int></items>"
                + "<items><string>a</string></items>"
                + "</child>");
        assertThat(((ParentWithList) h).items, equalTo(List.of(1, 2)));
        assertThat(h.items, equalTo(List.of("a")));
    }
}
