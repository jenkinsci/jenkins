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
import hudson.model.Descriptor;
import hudson.model.Describable;
import hudson.util.DescriptorList;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;

import org.jvnet.tiger_types.Types;

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
public class ExtensionList<T> implements Iterable<T> {
    public final Hudson hudson;
    public final Class<T> extensionType;

    /**
     * Once discovered, extensions are retained here.
     */
    private volatile CopyOnWriteArrayList<T> extensions;

    /**
     * Place to store manually registered instances with the per-Hudson scope.
     */
    private final List<T> legacyInstances;

    /**
     * View of the {@link ExtensionList} as a mutable list.
     *
     * <p>
     * Read access on this instance will see the full list that {@link ExtensionList} has,
     * and the write access will allow the legacy manual registrations.
     */
    private final List<T> listView = new AbstractList<T>() {
        @Override
        public T get(int index) {
            ensureLoaded();
            return extensions.get(index);
        }

        @Override
        public int size() {
            ensureLoaded();
            return extensions.size();
        }

        @Override
        public Iterator<T> iterator() {
            return ExtensionList.this.iterator(); // performance optimization as this is a common path
        }

        @Override
        public void add(int index, T element) {
            legacyInstances.add(element);
            // if we've already filled extensions, add it
            if(extensions!=null)
                extensions.add(element);
        }
    };

    protected ExtensionList(Hudson hudson, Class<T> extensionType) {
        this(hudson,extensionType,new Vector<T>());
    }

    /**
     *
     * @param legacyStore
     *      Place to store manually registered instances. The version of the constructor that
     *      omits this uses a new {@link Vector}, making the storage lifespan tied to the life of  {@link ExtensionList}.
     *      If the manually registerd instances are scoped to VM level, the caller should pass in a static list. 
     */
    protected ExtensionList(Hudson hudson, Class<T> extensionType, List<T> legacyStore) {
        this.hudson = hudson;
        this.extensionType = extensionType;
        this.legacyInstances = legacyStore;
    }

    /**
     * Looks for the extension instance of the given type (subclasses excluded),
     * or return null.
     */
    public <U extends T> U of(Class<U> type) {
        for (T ext : this)
            if(ext.getClass()==type)
                return type.cast(ext);
        return null;
    }

    public Iterator<T> iterator() {
        ensureLoaded();
        return extensions.iterator();

    }

    /**
     * Returns {@link ExtensionFinder}s used to search for the extension instances.
     */
    protected Iterable<? extends ExtensionFinder> finders() {
        return hudson.getExtensionList(ExtensionFinder.class);
    }

    private void ensureLoaded() {
        if(extensions!=null)
            return; // already loaded
        synchronized (this) {
            if(extensions==null) {
                List<T> r = load();
                r.addAll(legacyInstances);
                extensions = new CopyOnWriteArrayList<T>(r);
            }
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
     * Provides the {@link List} adapter for the extension list, in case
     * the compatibility with older Hudson requires that there be a mutable list.
     *
     * <p>
     * Read access to this list will see the same thing as {@link ExtensionList#iterator()},
     * and write acecss will keep the objects in a separate list, so that they can be merged
     * to the list of auto-disovered instances.
     */
    public List<T> asList() {
        return listView;
    }

    /**
     * {@link ExtensionList} for holding a subset of {@link Descriptor}s, which is a group of descriptors for
     * the same extension point.
     */
    public static final class DescriptorSubList<T extends Describable<T>> extends ExtensionList<Descriptor<T>> {
        /**
         * Type of the {@link Describable} that this extension list retains.
         */
        private final Class<T> describableType;

        public DescriptorSubList(Hudson hudson, Class<T> describableType) {
            super(hudson, (Class)Descriptor.class,allocateLegacyInstanceStore(describableType));
            this.describableType = describableType;
        }

        /**
         * Loading the descriptors in this case means filtering the descriptor from the master {@link ExtensionList}.
         */
        @Override
        protected List<Descriptor<T>> load() {
            List r = new ArrayList();
            for( Descriptor d : hudson.getExtensionList(Descriptor.class) ) {
                Type subTyping = Types.getBaseClass(d.getClass(), Descriptor.class);
                if (!(subTyping instanceof ParameterizedType)) {
                    LOGGER.severe(d.getClass()+" doesn't extend Descriptor with a type parameter");
                    continue;   // skip this one
                }
                if(Types.getTypeArgument(subTyping,0)==describableType)
                    r.add(d);
            }
            return r;
        }

        /**
         * Obtain the statically-scoped storage to store manulaly registered {@link Descriptor}s.
         */
        private static List allocateLegacyInstanceStore(Class describableType) {
            List r = legacyDescriptors.get(describableType);
            if(r!=null)     return r;

            r = new CopyOnWriteArrayList();
            List x = legacyDescriptors.putIfAbsent(describableType, r);
            if(x==null) return r;
            return x;
        }

        /**
         * Stores manually registered Descriptor instances.
         */
        private static final ConcurrentHashMap<Class,List> legacyDescriptors = new ConcurrentHashMap<Class,List>();
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
        return new ExtensionList<T>(hudson,type);
    }

    private static final Logger LOGGER = Logger.getLogger(ExtensionList.class.getName());
}
