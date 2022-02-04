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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.CollectionConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.security.InputManipulationException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import jenkins.util.xstream.CriticalXStreamException;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

//TODO merge back to RobustReflectionConverterTest
public class RobustReflectionConverterSEC2602Test {
    @Test(timeout = 30 * 1000)
    @Issue("SECURITY-2602")
    public void robustDoesNotSwallowDosException() {
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

        try {
            xstream2.fromXML(xml);
            fail("Thrown " + CriticalXStreamException.class.getName() + " expected");
        } catch (final CriticalXStreamException e) {
            Throwable cause = e.getCause();
            assertNotNull("A non-null cause of CriticalXStreamException is expected", cause);
            assertTrue("Cause of CriticalXStreamException is expected to be InputManipulationException", cause instanceof InputManipulationException);
            InputManipulationException ime = (InputManipulationException) cause;
            assertTrue("Limit expected in message", ime.getMessage().contains("exceeds 5 seconds"));
        }
    }

    @Test(timeout = 30 * 1000)
    public void customConverterUseDefaultXStreamException() {
        XStream2 xstream2 = new XStream2();
        xstream2.registerConverter(new CustomSet.ConverterImpl(xstream2.getMapper()));

        CustomSet customSet = preparePayloadUsingCustomSet();

        // Will use the ConverterImpl without going with RobustReflectionConverter
        final String xml = xstream2.toXML(customSet);

        try {
            xstream2.fromXML(xml);
            fail("Thrown " + InputManipulationException.class.getName() + " expected");
        } catch (final InputManipulationException e) {
            assertTrue("Limit expected in message", e.getMessage().contains("exceeds 5 seconds"));
        }
    }

    @Test(timeout = 30 * 1000)
    @Issue("SECURITY-2602")
    public void wrappedCustomSetUseCriticalXStreamException() {
        XStream2 xstream2 = new XStream2();

        CustomSet customSet = preparePayloadUsingCustomSet();
        // enforce the use of RobustReflectionConverter
        ParentObject parentObject = new ParentObject();
        parentObject.customSet1 = customSet;
        parentObject.customSet2 = customSet;
        parentObject.customSet3 = customSet;

        final String xml = xstream2.toXML(parentObject);

        try {
            xstream2.fromXML(xml);
            // Without the InputManipulationException catch in RobustReflectionConverter,
            // the parsing is continued despite the DoS prevention being triggered due to the robustness
            fail("Thrown " + CriticalXStreamException.class.getName() + " expected");
        } catch (final CriticalXStreamException e) {
            Throwable cause = e.getCause();
            assertNotNull("A non-null cause of CriticalXStreamException is expected", cause);
            assertTrue("Cause of CriticalXStreamException is expected to be InputManipulationException", cause instanceof InputManipulationException);
            InputManipulationException ime = (InputManipulationException) cause;
            assertTrue("Limit expected in message", ime.getMessage().contains("exceeds 5 seconds"));
        }
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
