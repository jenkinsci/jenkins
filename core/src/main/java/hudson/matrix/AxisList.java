/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.thoughtworks.xstream.XStream;
import hudson.Util;
import hudson.util.RobustCollectionConverter;

import javax.annotation.Nullable;
import java.util.*;

/**
 * List of {@link Axis}.
 * 
 * @author Kohsuke Kawaguchi
 */
public class AxisList extends ArrayList<Axis> {
    public AxisList() {
    }

    public AxisList(Collection<? extends Axis> c) {
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

    /**
     * Creates a subset of the list that only contains the type assignable to the specified type.
     */
    public AxisList subList(Class<? extends Axis> subType) {
        return new AxisList(Util.filter(this,subType));
    }

    @Override
    public boolean add(Axis axis) {
        return axis!=null && super.add(axis);
    }

    /**
     * List up all the possible combinations of this list.
     */
    public Iterable<Combination> list() {
        List<Set<String>> axesList = Lists.newArrayList();
        for (Axis axis : this)
            axesList.add(new LinkedHashSet<String>(axis.getValues()));

        return Iterables.transform(Sets.cartesianProduct(axesList), new Function<List<String>, Combination>() {
            public Combination apply(@Nullable List<String> strings) {
                assert strings != null;
                return new Combination(AxisList.this, (String[]) strings.toArray(new String[0]));
            }
        });
    }

    /**
     * {@link com.thoughtworks.xstream.converters.Converter} implementation for XStream.
     */
    public static final class ConverterImpl extends RobustCollectionConverter {
        public ConverterImpl(XStream xs) {
            super(xs);
        }

        @Override
        public boolean canConvert(Class type) {
            return type==AxisList.class;
        }

        @Override
        protected Object createCollection(Class type) {
            return new AxisList();
        }
    }
}
