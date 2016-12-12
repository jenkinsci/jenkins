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

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.Stapler;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.CheckForNull;

/**
 * List of {@link Descriptor}s.
 *
 * <p>
 * Before Hudson 1.286, this class stored {@link Descriptor}s directly, but since 1.286,
 * this class works in two modes that are rather different.
 *
 * <p>
 * One is the compatibility mode, where it works just like pre 1.286 and store everything locally,
 * disconnected from any of the additions of 1.286. This is necessary for situations where
 * {@link DescriptorList} is owned by pre-1.286 plugins where this class doesn't know 'T'.
 * In this mode, {@link #legacy} is non-null but {@link #type} is null.
 *
 * <p>
 * The other mode is the new mode, where the {@link Descriptor}s are actually stored in {@link ExtensionList}
 * (see {@link jenkins.model.Jenkins#getDescriptorList(Class)}) and this class acts as a view to it. This enables
 * bi-directional interoperability &mdash; both descriptors registred automatically and descriptors registered
 * manually are visible from both {@link DescriptorList} and {@link ExtensionList}. In this mode,
 * {@link #legacy} is null but {@link #type} is non-null.
 *
 * <p>
 * The number of plugins that define extension points are limited, so we expect to be able to remove
 * this dual behavior first, then when everyone stops using {@link DescriptorList},  we can remove this class
 * altogether.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.161
 */
public final class DescriptorList<T extends Describable<T>> extends AbstractList<Descriptor<T>> {

    private final Class<T> type;

    private final CopyOnWriteArrayList<Descriptor<T>> legacy;

    /**
     * This will create a legacy {@link DescriptorList} that is disconnected from
     * {@link ExtensionList}.
     *
     * @deprecated
     *      As of 1.286. Use {@link #DescriptorList(Class)} instead.
     */
    @Deprecated
    public DescriptorList(Descriptor<T>... descriptors) {
        this.type = null;
        this.legacy = new CopyOnWriteArrayList<Descriptor<T>>(descriptors);
    }

    /**
     * Creates a {@link DescriptorList} backed by {@link ExtensionList}.
     */
    public DescriptorList(Class<T> type) {
        this.type = type;
        this.legacy = null;
    }

    @Override
    public Descriptor<T> get(int index) {
        return store().get(index);
    }

    @Override
    public int size() {
        return store().size();
    }

    @Override
    public Iterator<Descriptor<T>> iterator() {
        return store().iterator();
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated
     *      As of 1.286. Put {@link Extension} on your descriptor to have it auto-registered,
     *      instead of registering a descriptor manually.
     */
    @Override
    @Deprecated
    public boolean add(Descriptor<T> d) {
        return store().add(d);
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated
     *      As of 1.286. Put {@link Extension} on your descriptor to have it auto-registered,
     *      instead of registering a descriptor manually.
     */
    @Override
    @Deprecated
    public void add(int index, Descriptor<T> element) {
        add(element); // order is ignored
    }

    @Override
    public boolean remove(Object o) {
        return store().remove(o);
    }

    /**
     * Gets the actual data store. This is the key to control the dual-mode nature of {@link DescriptorList}
     */
    private List<Descriptor<T>> store() {
        if(type==null)
            return legacy;
        else
            return Jenkins.getInstance().<T,Descriptor<T>>getDescriptorList(type);
    }

    /**
     * Creates a new instance of a {@link Describable}
     * from the structured form submission data posted
     * by a radio button group. 
     * @param config Submitted configuration for Radio List
     * @return new instance or {@code null} if none was selected in the radio list
     * @throws FormException Data submission error
     */
    @CheckForNull
    public T newInstanceFromRadioList(JSONObject config) throws FormException {
        if(config.isNullObject())
            return null;    // none was selected
        int idx = config.getInt("value");
        return get(idx).newInstance(Stapler.getCurrentRequest(),config);
    }

    /**
     * Creates a new instance of a {@link Describable}
     * from the structured form submission data posted
     * by a radio button group. 
     * @param parent JSON, which contains the configuration entry for the radio list
     * @param name Name of the configuration entry for the radio list
     * @return new instance or {@code null} if none was selected in the radio list
     * @throws FormException Data submission error
     */
    @CheckForNull
    public T newInstanceFromRadioList(JSONObject parent, String name) throws FormException {
        return newInstanceFromRadioList(parent.getJSONObject(name));
    }

    /**
     * Finds a descriptor by their {@link Descriptor#getId()}.
     * @param id Descriptor ID
     * @return If none is found, {@code null} is returned.
     */
    @CheckForNull
    public Descriptor<T> findByName(String id) {
        for (Descriptor<T> d : this)
            if(d.getId().equals(id))
                return d;
        return null;
    }

    /**
     * No-op method used to force the class initialization of the given class.
     * The class initialization in turn is expected to put the descriptor
     * into the {@link DescriptorList}.
     *
     * <p>
     * This is necessary to resolve the class initialization order problem.
     * Often a {@link DescriptorList} is defined in the base class, and
     * when it tries to initialize itself by listing up descriptors of known
     * sub-classes, they might not be available in time.
     *
     * @since 1.162
     */
    public void load(Class<? extends Describable> c) {
        try {
            Class.forName(c.getName(), true, c.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);  // Can't happen
        }
    }

    /**
     * Finds the descriptor that has the matching fully-qualified class name.
     * @deprecated Underspecified what the parameter is. {@link Descriptor#getId}? A {@link Describable} class name?
     */
    @CheckForNull
    public Descriptor<T> find(String fqcn) {
        return Descriptor.find(this,fqcn);
    }
}
