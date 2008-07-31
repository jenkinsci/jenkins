package hudson.matrix;

import com.thoughtworks.xstream.alias.CannotResolveClassException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.reflection.SerializableConverter;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.converters.collections.AbstractCollectionConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.util.RobustCollectionConverter;

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
        public ConverterImpl(Mapper mapper, ReflectionProvider reflectionProvider) {
            super(mapper,reflectionProvider);
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
