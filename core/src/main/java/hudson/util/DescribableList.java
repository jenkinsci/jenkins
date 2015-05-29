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
import jenkins.model.DependencyDeclarer;
import hudson.model.DependencyGraph;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.ReconfigurableDescribable;
import hudson.model.Saveable;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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
public class DescribableList<T extends Describable<T>, D extends Descriptor<T>> extends PersistedList<T> {
    protected DescribableList() {
    }

    /**
     * @deprecated since 2008-08-15.
     *      Use {@link #DescribableList(Saveable)} 
     */
    @Deprecated
    public DescribableList(Owner owner) {
        setOwner(owner);
    }

    public DescribableList(Saveable owner) {
        setOwner(owner);
    }

    public DescribableList(Saveable owner, Collection<? extends T> initialList) {
        super(initialList);
        setOwner(owner);
    }

    /**
     * @deprecated since 2008-08-15.
     *      Use {@link #setOwner(Saveable)}
     */
    @Deprecated
    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    /**
     * Removes all instances of the same type, then add the new one.
     */
    public void replace(T item) throws IOException {
        removeAll((Class)item.getClass());
        data.add(item);
        onModified();
    }

    /**
     * Binds items in the collection to URL.
     */
    public T getDynamic(String id) {
        // by ID
        for (T t : data)
            if(t.getDescriptor().getId().equals(id))
                return t;

        // by position
        try {
            return data.get(Integer.parseInt(id));
        } catch (NumberFormatException e) {
            // fall through
        }

        return null;
    }

    public T get(D descriptor) {
        for (T t : data)
            if(t.getDescriptor()==descriptor)
                return t;
        return null;
    }

    public boolean contains(D d) {
        return get(d)!=null;
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

    /**
     * Creates a detached map from the current snapshot of the data, keyed from a descriptor to an instance.
     */
    @SuppressWarnings("unchecked")
    public Map<D,T> toMap() {
        return (Map)Descriptor.toMap(data);
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
    public void rebuild(StaplerRequest req, JSONObject json, List<? extends Descriptor<T>> descriptors) throws FormException, IOException {
        List<T> newList = new ArrayList<T>();

        for (Descriptor<T> d : descriptors) {
            T existing = get((D)d);
            String name = d.getJsonSafeClassName();
            JSONObject o = json.optJSONObject(name);

            T instance = null;
            if (o!=null) {
                if (existing instanceof ReconfigurableDescribable)
                    instance = (T)((ReconfigurableDescribable)existing).reconfigure(req,o);
                else
                    instance = d.newInstance(req, o);
            } else {
                if (existing instanceof ReconfigurableDescribable)
                    instance = (T)((ReconfigurableDescribable)existing).reconfigure(req,null);
            }

            if (instance!=null)
                newList.add(instance);
        }

        replaceBy(newList);
    }

    /**
     * @deprecated as of 1.271
     *      Use {@link #rebuild(StaplerRequest, JSONObject, List)} instead.
     */
    @Deprecated
    public void rebuild(StaplerRequest req, JSONObject json, List<? extends Descriptor<T>> descriptors, String prefix) throws FormException, IOException {
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
    public void rebuildHetero(StaplerRequest req, JSONObject formData, Collection<? extends Descriptor<T>> descriptors, String key) throws FormException, IOException {
        replaceBy(Descriptor.newInstancesFromHeteroList(req,formData,key,descriptors));
    }

    /**
     * Picks up {@link DependencyDeclarer}s and allow it to build dependencies.
     */
    public void buildDependencyGraph(AbstractProject owner,DependencyGraph graph) {
        for (Object o : this) {
            if (o instanceof DependencyDeclarer) {
                DependencyDeclarer dd = (DependencyDeclarer) o;
                try {
                    dd.buildDependencyGraph(owner,graph);
                } catch (RuntimeException e) {
                    LOGGER.log(Level.SEVERE, "Failed to build dependency graph for " + owner,e);
                }
            }
        }
    }

/*
    The following two seemingly pointless method definitions are necessary to produce
    backward compatible binary signatures. Without this we only get
    get(Ljava/lang/Class;)Ljava/lang/Object; from PersistedList where we need
    get(Ljava/lang/Class;)Lhudson/model/Describable;
 */
    public <U extends T> U get(Class<U> type) {
        return super.get(type);
    }

    public T[] toArray(T[] array) {
        return super.toArray(array);
    }

    /**
     * @deprecated since 2008-08-15.
     *      Just implement {@link Saveable}.
     */
    @Deprecated
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

    private final static Logger LOGGER = Logger.getLogger(DescribableList.class.getName());
}
