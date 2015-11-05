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

import com.thoughtworks.xstream.XStreamException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.AbstractCollectionConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Arrays;

import jenkins.util.xstream.CriticalXStreamException;


/**
 * {@link List}-like implementation that has copy-on-write semantics.
 *
 * <p>
 * This class is suitable where highly concurrent access is needed, yet
 * the write operation is relatively uncommon.
 *
 * @author Kohsuke Kawaguchi
 */
public class CopyOnWriteList<E> implements Iterable<E> {
    private volatile List<? extends E> core;

    public CopyOnWriteList(List<E> core) {
        this(core,false);
    }

    private CopyOnWriteList(List<E> core, boolean noCopy) {
        this.core = noCopy ? core : new ArrayList<E>(core);
    }

    public CopyOnWriteList() {
        this.core = Collections.emptyList();
    }

    public synchronized void add(E e) {
        List<E> n = new ArrayList<E>(core);
        n.add(e);
        core = n;
    }

    public synchronized void addAll(Collection<? extends E> items) {
        List<E> n = new ArrayList<E>(core);
        n.addAll(items);
        core = n;
    }

    /**
     * Removes an item from the list.
     *
     * @return
     *      true if the list contained the item. False if it didn't,
     *      in which case there's no change.
     */
    public synchronized boolean remove(E e) {
        List<E> n = new ArrayList<E>(core);
        boolean r = n.remove(e);
        core = n;
        return r;
    }

    /**
     * Returns an iterator.
     */
    public Iterator<E> iterator() {
        final Iterator<? extends E> itr = core.iterator();
        return new Iterator<E>() {
            private E last;
            public boolean hasNext() {
                return itr.hasNext();
            }

            public E next() {
                return last=itr.next();
            }

            public void remove() {
                CopyOnWriteList.this.remove(last);
            }
        };
    }

    /**
     * Completely replaces this list by the contents of the given list.
     */
    public void replaceBy(CopyOnWriteList<? extends E> that) {
        this.core = that.core;
    }

    /**
     * Completely replaces this list by the contents of the given list.
     */
    public void replaceBy(Collection<? extends E> that) {
        this.core = new ArrayList<E>(that);
    }

    /**
     * Completely replaces this list by the contents of the given list.
     */
    public void replaceBy(E... that) {
        replaceBy(Arrays.asList(that));
    }

    public void clear() {
        this.core = new ArrayList<E>();
    }

    public <E> E[] toArray(E[] array) {
        return core.toArray(array);
    }

    public List<E> getView() {
        return Collections.unmodifiableList(core);
    }

    public void addAllTo(Collection<? super E> dst) {
        dst.addAll(core);
    }

    public E get(int index) {
        return core.get(index);
    }

    public boolean isEmpty() {
        return core.isEmpty();
    }

    public int size() {
        return core.size();
    }

    public boolean contains(Object item) {
        return core.contains(item);
    }

    @Override public String toString() {
        return core.toString();
    }

    /**
     * {@link Converter} implementation for XStream.
     */
    public static final class ConverterImpl extends AbstractCollectionConverter {
        public ConverterImpl(Mapper mapper) {
            super(mapper);
        }

        public boolean canConvert(Class type) {
            return type==CopyOnWriteList.class;
        }

        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            for (Object o : (CopyOnWriteList) source)
                writeItem(o, context, writer);
        }

        @SuppressWarnings("unchecked")
        public CopyOnWriteList unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            // read the items from xml into a list
            List items = new ArrayList();
            while (reader.hasMoreChildren()) {
                reader.moveDown();
                try {
                    Object item = readItem(reader, context, items);
                    items.add(item);
                } catch (CriticalXStreamException e) {
                    throw e;
                } catch (XStreamException e) {
                    RobustReflectionConverter.addErrorInContext(context, e);
                } catch (LinkageError e) {
                    RobustReflectionConverter.addErrorInContext(context, e);
                }
                reader.moveUp();
            }

            return new CopyOnWriteList(items,true);
        }
    }

}
