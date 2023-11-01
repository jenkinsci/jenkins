/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

import com.thoughtworks.xstream.converters.basic.AbstractSingleValueConverter;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Saveable;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

public class DescribableListTest {

    @Issue("JENKINS-49054")
    @Test
    public void exceptionDuringUnmarshalling() {
        Data data = new Data();
        data.list.add(new Datum(1));
        data.list.add(new Datum(2));
        data.list.add(new Datum(3));
        XStream2 xs = new XStream2();
        xs.addCriticalField(Data.class, "list");
        String xml = xs.toXML(data);
        data = (Data) xs.fromXML(xml);
        assertEquals("[1, 3]", data.toString());
    }

    @Test
    public void replace() throws Exception {
        AtomicInteger count = new AtomicInteger();
        DescribableList<Datum, Descriptor<Datum>> list = new DescribableList<>((Saveable) count::incrementAndGet);
        list.add(new Datum(1));
        list.add(new Datum(2));
        assertThat(count.get(), is(2));
        list.replace(new Datum(3));
        assertThat(list.stream().map(d -> d.val).toArray(Integer[]::new), arrayContaining(3));
        assertThat(count.get(), is(3));
    }

    private static final class Data {

        final DescribableList<Datum, Descriptor<Datum>> list = new DescribableList<>();

        @Override
        public String toString() {
            return list.toString();
        }

    }

    private static final class Datum implements Describable<Datum> {

        final int val;

        Datum(int val) {
            this.val = val;
        }

        @Override
        public Descriptor<Datum> getDescriptor() {
            return new Descriptor<>(Datum.class) {};
        }

        @Override
        public String toString() {
            return Integer.toString(val);
        }

        public static final class ConverterImpl extends AbstractSingleValueConverter {

            @Override
            public boolean canConvert(Class type) {
                return type == Datum.class;
            }

            @Override
            public Object fromString(String str) {
                int val = Integer.parseInt(str);
                if (val == 2) {
                    throw new IllegalStateException("oops");
                }
                return new Datum(val);
            }

        }

    }

}
