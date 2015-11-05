/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson;

import hudson.model.Descriptor;
import hudson.model.Describable;
import hudson.model.Hudson;
import jenkins.ExtensionComponentSet;
import jenkins.model.Jenkins;
import hudson.model.ViewDescriptor;
import hudson.model.Descriptor.FormException;
import hudson.util.AdaptedIterator;
import hudson.util.Memoizer;
import hudson.util.Iterators.FlattenIterator;
import hudson.slaves.NodeDescriptor;
import hudson.tasks.Publisher;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.CheckForNull;

import org.kohsuke.stapler.Stapler;
import net.sf.json.JSONObject;

/**
 * {@link ExtensionList} for holding a set of {@link Descriptor}s, which is a group of descriptors for
 * the same extension point.
 *
 * Use {@link jenkins.model.Jenkins#getDescriptorList(Class)} to obtain instances.
 *
 * @param <D>
 *      Represents the descriptor type. This is {@code Descriptor<T>} normally but often there are subtypes
 *      of descriptors, like {@link ViewDescriptor}, {@link NodeDescriptor}, etc, and this parameter points
 *      to those for better type safety of users.
 *
 *      The actual value of 'D' is not necessary for the operation of this code, so it's purely for convenience
 *      of the users of this class.
 *
 * @since 1.286
 */
public class DescriptorExtensionList<T extends Describable<T>, D extends Descriptor<T>> extends ExtensionList<D> {
    /**
     * Creates a new instance.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Describable<T>,D extends Descriptor<T>>
    DescriptorExtensionList<T,D> createDescriptorList(Jenkins jenkins, Class<T> describableType) {
        if (describableType == (Class) Publisher.class) {
            return (DescriptorExtensionList) new Publisher.DescriptorExtensionListImpl(jenkins);
        }
        return new DescriptorExtensionList<T,D>(jenkins,describableType);
    }

    /**
     * @deprecated as of 1.416
     *      Use {@link #create(Jenkins, Class)}
     */
    @Deprecated
    public static <T extends Describable<T>,D extends Descriptor<T>>
    DescriptorExtensionList<T,D> createDescriptorList(Hudson hudson, Class<T> describableType) {
        return (DescriptorExtensionList)createDescriptorList((Jenkins)hudson,describableType);
    }

    /**
     * Type of the {@link Describable} that this extension list retains.
     */
    private final Class<T> describableType;

    /**
     * @deprecated as of 1.416
     *      Use {@link #DescriptorExtensionList(Jenkins, Class)}
     */
    @Deprecated
    protected DescriptorExtensionList(Hudson hudson, Class<T> describableType) {
        this((Jenkins)hudson,describableType);
    }

    protected DescriptorExtensionList(Jenkins jenkins, Class<T> describableType) {
        super(jenkins, (Class)Descriptor.class, (CopyOnWriteArrayList)getLegacyDescriptors(describableType));
        this.describableType = describableType;
    }

    /**
     * Finds the descriptor that has the matching fully-qualified class name.
     *
     * @param fqcn
     *      Fully qualified name of the descriptor, not the describable.
     * @deprecated {@link Descriptor#getId} is supposed to be used for new code, not the descriptor class name.
     */
    public D find(String fqcn) {
        return Descriptor.find(this,fqcn);
    }

    /**
     * Finds the descriptor that describes the given type.
     * That is, if this method returns d, {@code d.clazz==type}
     */
    public D find(Class<? extends T> type) {
        for (D d : this)
            if (d.clazz==type)
                return d;
        return null;
    }

    /**
     * Creates a new instance of a {@link Describable}
     * from the structured form submission data posted
     * by a radio button group.
     */
    public T newInstanceFromRadioList(JSONObject config) throws FormException {
        if(config.isNullObject())
            return null;    // none was selected
        int idx = config.getInt("value");
        return get(idx).newInstance(Stapler.getCurrentRequest(),config);
    }

    public T newInstanceFromRadioList(JSONObject parent, String name) throws FormException {
        return newInstanceFromRadioList(parent.getJSONObject(name));
    }

    /**
     * Finds a descriptor by their {@link Descriptor#getId()}.
     *
     * If none is found, null is returned.
     */
    public @CheckForNull D findByName(String id) {
        for (D d : this)
            if(d.getId().equals(id))
                return d;
        return null;
    }

    @Override
    public boolean add(D d) {
        boolean r = super.add(d);
        hudson.getExtensionList(Descriptor.class).add(d);
        return r;
    }

    @Override
    public boolean remove(Object o) {
        hudson.getExtensionList(Descriptor.class).remove(o);
        return super.remove(o);
    }

    /**
     * {@link #load()} in the descriptor is not a real load activity, so locking against "this" is enough.
     */
    @Override
    protected Object getLoadLock() {
        return this;
    }

    /**
     * Loading the descriptors in this case means filtering the descriptor from the master {@link ExtensionList}.
     */
    @Override
    protected List<ExtensionComponent<D>> load() {
        return _load(jenkins.getExtensionList(Descriptor.class).getComponents());
    }

    @Override
    protected Collection<ExtensionComponent<D>> load(ExtensionComponentSet delta) {
        return _load(delta.find(Descriptor.class));
    }

    private List<ExtensionComponent<D>> _load(Iterable<ExtensionComponent<Descriptor>> set) {
        List<ExtensionComponent<D>> r = new ArrayList<ExtensionComponent<D>>();
        for( ExtensionComponent<Descriptor> c : set ) {
            Descriptor d = c.getInstance();
            try {
                if(d.getT()==describableType)
                    r.add((ExtensionComponent)c);
            } catch (IllegalStateException e) {
                LOGGER.log(Level.SEVERE, d.getClass() + " doesn't extend Descriptor with a type parameter", e); // skip this one
            }
        }
        return r;
    }

    /**
     * Stores manually registered Descriptor instances. Keyed by the {@link Describable} type.
     */
    private static final Memoizer<Class,CopyOnWriteArrayList<ExtensionComponent<Descriptor>>> legacyDescriptors = new Memoizer<Class,CopyOnWriteArrayList<ExtensionComponent<Descriptor>>>() {
        public CopyOnWriteArrayList compute(Class key) {
            return new CopyOnWriteArrayList();
        }
    };

    private static <T extends Describable<T>> CopyOnWriteArrayList<ExtensionComponent<Descriptor<T>>> getLegacyDescriptors(Class<T> type) {
        return (CopyOnWriteArrayList)legacyDescriptors.get(type);
    }

    /**
     * List up all the legacy instances currently in use.
     */
    public static Iterable<Descriptor> listLegacyInstances() {
        return new Iterable<Descriptor>() {
            public Iterator<Descriptor> iterator() {
                return new AdaptedIterator<ExtensionComponent<Descriptor>,Descriptor>(
                    new FlattenIterator<ExtensionComponent<Descriptor>,CopyOnWriteArrayList<ExtensionComponent<Descriptor>>>(legacyDescriptors.values()) {
                        protected Iterator<ExtensionComponent<Descriptor>> expand(CopyOnWriteArrayList<ExtensionComponent<Descriptor>> v) {
                            return v.iterator();
                        }
                    }) {

                    protected Descriptor adapt(ExtensionComponent<Descriptor> item) {
                        return item.getInstance();
                    }
                };
            }
        };
    }

    /**
     * Exposed just for the test harness. Clear legacy instances.
     */
    public static void clearLegacyInstances() {
        legacyDescriptors.clear();
    }

    private static final Logger LOGGER = Logger.getLogger(DescriptorExtensionList.class.getName());
}
