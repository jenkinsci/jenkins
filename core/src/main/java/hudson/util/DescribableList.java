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

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.AbstractCollectionConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;
import hudson.model.AbstractProject;
import hudson.model.DependecyDeclarer;
import hudson.model.DependencyGraph;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Saveable;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Persisted list of {@link Describable}s with some operations specific
 * to {@link Descriptor}s.
 *
 * <p>
 * This class allows multiple instances of the same descriptor. Some clients
 * use this semantics, while other clients use it as "up to one instance per
 * one descriptor" model.
 *
 * Some of the methods defined in this class only makes sense in the latter model,
 * such as {@link #remove(Descriptor)}.
 *
 * @author Kohsuke Kawaguchi
 */
public class DescribableList<T extends Describable<T>, D extends Descriptor<T>> implements Iterable<T> {
    private final CopyOnWriteList<T> data = new CopyOnWriteList<T>();
    private Saveable owner;

    protected DescribableList() {
    }

    /**
     * @deprecated
     *      Use {@link #DescribableList(Saveable)} 
     */
    public DescribableList(Owner owner) {
        setOwner(owner);
    }

    public DescribableList(Saveable owner) {
        setOwner(owner);
    }

    /**
     * @deprecated
     *      Use {@link #setOwner(Saveable)}
     */
    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    public void setOwner(Saveable owner) {
        this.owner = owner;
    }

    public void add(T item) throws IOException {
        data.add(item);
        onModified();
    }

    /**
     * Removes all instances of the same type, then add the new one.
     */
    public void replace(T item) throws IOException {
        removeAll((Class)item.getClass());
        data.add(item);
        onModified();
    }

    public void replaceBy(Collection<? extends T> col) throws IOException {
        data.replaceBy(col);
        onModified();
    }

    public T get(D descriptor) {
        for (T t : data)
            if(t.getDescriptor()==descriptor)
                return t;
        return null;
    }

    public <U extends T> U get(Class<U> type) {
        for (T t : data)
            if(type.isInstance(t))
                return type.cast(t);
        return null;
    }

    /**
     * Gets all instances that matches the given type.
     */
    public <U extends T> List<U> getAll(Class<U> type) {
        List<U> r = new ArrayList<U>();
        for (T t : data)
            if(type.isInstance(t))
                r.add(type.cast(t));
        return r;
    }

    public boolean contains(D d) {
        return get(d)!=null;
    }

    public int size() {
        return data.size();
    }

    /**
     * Removes an instance by its type.
     */
    public void remove(Class<? extends T> type) throws IOException {
        for (T t : data) {
            if(t.getClass()==type) {
                data.remove(t);
                onModified();
                return;
            }
        }
    }

    public void removeAll(Class<? extends T> type) throws IOException {
        boolean modified=false;
        for (T t : data) {
            if(t.getClass()==type) {
                data.remove(t);
                modified=true;
            }
        }
        if(modified)
            onModified();
    }

    public void remove(D descriptor) throws IOException {
        for (T t : data) {
            if(t.getDescriptor()==descriptor) {
                data.remove(t);
                onModified();
                return;
            }
        }
    }

    public void clear() {
        data.clear();
    }

    public Iterator<T> iterator() {
        return data.iterator();
    }

    /**
     * Called when a list is mutated.
     */
    protected void onModified() throws IOException {
        owner.save();
    }

    @SuppressWarnings("unchecked")
    public Map<D,T> toMap() {
        return (Map)Descriptor.toMap(data);
    }

    /**
     * Returns the snapshot view of instances as list.
     */
    public List<T> toList() {
        return data.getView();
    }

    /**
     * Gets all the {@link Describable}s in an array.
     */
    public T[] toArray(T[] array) {
        return data.toArray(array);
    }

    public void addAllTo(Collection<? super T> dst) {
        data.addAllTo(dst);
    }

    /**
     * Rebuilds the list by creating a fresh instances from the submitted form.
     *
     * <p>
     * This method is almost always used by the owner.
     * This method does not invoke the save method.
     *
     * @param json
     *      Structured form data that includes the data for nested descriptor list.
     */
    public void rebuild(StaplerRequest req, JSONObject json, List<? extends Descriptor<T>> descriptors) throws FormException {
        List<T> newList = new ArrayList<T>();

        for (Descriptor<T> d : descriptors) {
            String name = d.getJsonSafeClassName();
            if (json.has(name)) {
                T instance = d.newInstance(req, json.getJSONObject(name));
                newList.add(instance);
            }
        }

        data.replaceBy(newList);
    }

    /**
     * @deprecated as of 1.271
     *      Use {@link #rebuild(StaplerRequest, JSONObject, List)} instead.
     */
    public void rebuild(StaplerRequest req, JSONObject json, List<? extends Descriptor<T>> descriptors, String prefix) throws FormException {
        rebuild(req,json,descriptors);
    }

    /**
     * Rebuilds the list by creating a fresh instances from the submitted form.
     *
     * <p>
     * This version works with the the &lt;f:hetero-list> UI tag, where the user
     * is allowed to create multiple instances of the same descriptor. Order is also
     * significant.
     */
    public void rebuildHetero(StaplerRequest req, JSONObject formData, Collection<? extends Descriptor<T>> descriptors, String key) throws FormException {
        data.replaceBy(Descriptor.newInstancesFromHeteroList(req,formData,key,descriptors));
    }

    /**
     * Picks up {@link DependecyDeclarer}s and allow it to build dependencies.
     */
    public void buildDependencyGraph(AbstractProject owner,DependencyGraph graph) {
        for (Object o : this) {
            if (o instanceof DependecyDeclarer) {
                DependecyDeclarer dd = (DependecyDeclarer) o;
                dd.buildDependencyGraph(owner,graph);
            }
        }
    }

    /**
     * @deprecated 
     *      Just implement {@link Saveable}.
     */
    public interface Owner extends Saveable {
    }

    /**
     * {@link Converter} implementation for XStream.
     *
     * Serializaion form is compatible with plain {@link List}.
     */
    public static class ConverterImpl extends AbstractCollectionConverter {
        CopyOnWriteList.ConverterImpl copyOnWriteListConverter;

        public ConverterImpl(Mapper mapper) {
            super(mapper);
            copyOnWriteListConverter = new CopyOnWriteList.ConverterImpl(mapper());
        }

        public boolean canConvert(Class type) {
            // handle subtypes in case the onModified method is overridden.
            return DescribableList.class.isAssignableFrom(type);
        }

        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            for (Object o : (DescribableList) source)
                writeItem(o, context, writer);
        }

        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            CopyOnWriteList core = copyOnWriteListConverter.unmarshal(reader, context);

            try {
                DescribableList r = (DescribableList)context.getRequiredType().newInstance();
                r.data.replaceBy(core);
                return r;
            } catch (InstantiationException e) {
                InstantiationError x = new InstantiationError();
                x.initCause(e);
                throw x;
            } catch (IllegalAccessException e) {
                IllegalAccessError x = new IllegalAccessError();
                x.initCause(e);
                throw x;
            }
        }
    }
}
