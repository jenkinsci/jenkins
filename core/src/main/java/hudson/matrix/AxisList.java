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
package hudson.matrix;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import hudson.util.RobustCollectionConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Arrays;

/**
 * List of {@link Axis}.
 * 
 * @author Kohsuke Kawaguchi
 */
public class AxisList extends ArrayList<Axis> {
    public AxisList() {
    }

    public AxisList(Collection<Axis> c) {
        super(c);
    }

    public AxisList(Axis... c) {
        this(Arrays.asList(c));
    }

    public Axis find(String name) {
        for (Axis a : this) {
            if(a.name.equals(name))
                return a;
        }
        return null;
    }

    public boolean add(Axis axis) {
        return axis!=null && super.add(axis);
    }

    /**
     * List up all the possible combinations of this list.
     */
    public Iterable<Combination> list() {
        final int[] base = new int[size()];

        int b = 1;
        for( int i=size()-1; i>=0; i-- ) {
            base[i] = b;
            b *= get(i).size();
        }

        final int total = b;    // number of total combinations

        return new Iterable<Combination>() {
            public Iterator<Combination> iterator() {
                return new Iterator<Combination>() {
                    private int counter = 0;

                    public boolean hasNext() {
                        return counter<total;
                    }

                    public Combination next() {
                        String[] data = new String[size()];
                        int x = counter++;
                        for( int i=0; i<data.length; i++) {
                            data[i] = get(i).value(x/base[i]);
                            x %= base[i];
                        }
                        assert x==0;
                        return new Combination(AxisList.this,data);
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    /**
     * {@link Converter} implementation for XStream.
     */
    public static final class ConverterImpl extends RobustCollectionConverter {
        public ConverterImpl(XStream xs) {
            super(xs);
        }

        public boolean canConvert(Class type) {
            return type==AxisList.class;
        }

        @Override
        protected Object createCollection(Class type) {
            return new AxisList();
        }
    }
}
