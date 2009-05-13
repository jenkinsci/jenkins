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

import hudson.model.Hudson;
import hudson.util.DescriptorList;
import hudson.util.Memoizer;
import hudson.util.Iterators;
import hudson.ExtensionPoint.LegacyInstancesAreScopedToHudson;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.Comparator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Retains the known extension instances for the given type 'T'.
 *
 * <p>
 * Extensions are loaded lazily on demand and automatically by using {@link ExtensionFinder}, but this
 * class also provides a mechanism to provide compatibility with the older {@link DescriptorList}-based
 * manual registration,
 *
 * <p>
 * All {@link ExtensionList} instances should be owned by {@link Hudson}, even though
 * extension points can be defined by anyone on any type. Use {@link Hudson#getExtensionList(Class)}
 * and {@link Hudson#getDescriptorList(Class)} to obtain the instances.
 *
 * @param <T>
 *      Type of the extension point. This class holds instances of the subtypes of 'T'. 
 *
 * @author Kohsuke Kawaguchi
 * @since 1.286
 * @see Hudson#getExtensionList(Class)
 * @see Hudson#getDescriptorList(Class)
 */
public class ExtensionList<T> extends AbstractList<T> {
    public final Hudson hudson;
    public final Class<T> extensionType;

    /**
     * Once discovered, extensions are retained here.
     */
    @CopyOnWrite
    private volatile List<T> extensions;

    /**
     * Place to store manually registered instances with the per-Hudson scope.
     * {@link CopyOnWriteArrayList} is used here to support concurrent iterations and mutation.
     */
    private final CopyOnWriteArrayList<T> legacyInstances;

    protected ExtensionList(Hudson hudson, Class<T> extensionType) {
        this(hudson,extensionType,new CopyOnWriteArrayList<T>());
    }

    /**
     *
     * @param legacyStore
     *      Place to store manually registered instances. The version of the constructor that
     *      omits this uses a new {@link Vector}, making the storage lifespan tied to the life of  {@link ExtensionList}.
     *      If the manually registerd instances are scoped to VM level, the caller should pass in a static list. 
     */
    protected ExtensionList(Hudson hudson, Class<T> extensionType, CopyOnWriteArrayList<T> legacyStore) {
        this.hudson = hudson;
        this.extensionType = extensionType;
        this.legacyInstances = legacyStore;
    }

    /**
     * Looks for the extension instance of the given type (subclasses excluded),
     * or return null.
     */
    public <U extends T> U get(Class<U> type) {
        for (T ext : this)
            if(ext.getClass()==type)
                return type.cast(ext);
        return null;
    }

    public Iterator<T> iterator() {
        // we need to intercept mutation, so for now don't allow Iterator.remove 
        return Iterators.readOnly(ensureLoaded().iterator());
    }

    public T get(int index) {
        return ensureLoaded().get(index);
    }
    
    public int size() {
        return ensureLoaded().size();
    }

    @Override
    public synchronized boolean remove(Object o) {
        legacyInstances.remove(o);
        if(extensions!=null) {
            List<T> r = new ArrayList<T>(extensions);
            r.remove(o);
            extensions = sort(r);
        }
        return true;
    }

    @Override
    public synchronized T remove(int index) {
        T t = get(index);
        remove(t);
        return t;
    }

    /**
     * Write access will put the instance into a legacy store.
     *
     * @deprecated
     *      Prefer automatic registration.
     */
    @Override
    public synchronized boolean add(T t) {
        legacyInstances.add(t);
        // if we've already filled extensions, add it
        if(extensions!=null) {
            List<T> r = new ArrayList<T>(extensions);
            r.add(t);
            extensions = sort(r);
        }
        return true;
    }

    @Override
    public void add(int index, T element) {
        add(element);
    }



    /**
     * Returns {@link ExtensionFinder}s used to search for the extension instances.
     */
    protected Iterable<? extends ExtensionFinder> finders() {
        return hudson.getExtensionList(ExtensionFinder.class);
    }

    private List<T> ensureLoaded() {
        if(extensions!=null)
            return extensions; // already loaded
        if(Hudson.getInstance().getPluginManager()==null)
            return legacyInstances; // can't perform the auto discovery until all plugins are loaded, so just make the legacy instances visisble

        synchronized (this) {
            if(extensions==null) {
                List<T> r = load();
                r.addAll(legacyInstances);
                extensions = sort(r);
            }
            return extensions;
        }
    }

    /**
     * Loads all the extensions.
     */
    protected List<T> load() {
        List<T> r = new ArrayList<T>();
        for (ExtensionFinder finder : finders())
            r.addAll(finder.findExtensions(extensionType, hudson));
        return r;
    }

    /**
     * If the {@link ExtensionList} implementation requires sorting extensions,
     * override this method to do so.
     *
     * <p>
     * The implementation should copy a list, do a sort, and return the new instance.
     */
    protected List<T> sort(List<T> r) {
        r = new ArrayList<T>(r);
        Collections.sort(r,new Comparator<T>() {
            public int compare(T lhs, T rhs) {
                Extension el = lhs.getClass().getAnnotation(Extension.class);
                Extension er = rhs.getClass().getAnnotation(Extension.class);
                // Extension used to be Retention.SOURCE, and we may also end up loading extensions from other places
                // like Plexus that don't have any annotations, so we need to be defensive
                double l = el!=null ? el.ordinal() : 0;
                double r = er!=null ? er.ordinal() : 0;
                if(l>r) return -1;
                if(l<r) return 1;
                return 0;
            }
        });
        return r;
    }

    public static <T> ExtensionList<T> create(Hudson hudson, Class<T> type) {
        if(type==ExtensionFinder.class)
            return new ExtensionList<T>(hudson,type) {
                /**
                 * If this ExtensionList is searching for ExtensionFinders, calling hudosn.getExtensionList
                 * results in infinite recursion.
                 */
                @Override
                protected Iterable<? extends ExtensionFinder> finders() {
                    return Collections.singleton(new ExtensionFinder.Sezpoz());
                }
            };
        if(type.getAnnotation(LegacyInstancesAreScopedToHudson.class)!=null)
            return new ExtensionList<T>(hudson,type);
        else {
            return new ExtensionList<T>(hudson,type,staticLegacyInstances.get(type));
        }
    }

    /**
     * Places to store static-scope legacy instances.
     */
    private static final Memoizer<Class,CopyOnWriteArrayList> staticLegacyInstances = new Memoizer<Class,CopyOnWriteArrayList>() {
        public CopyOnWriteArrayList compute(Class key) {
            return new CopyOnWriteArrayList();
        }
    };

    /**
     * Exposed for the test harness to clear all legacy extension instances.
     */
    public static void clearLegacyInstances() {
        staticLegacyInstances.clear();
    }
}
