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

import com.google.common.collect.Lists;
import hudson.init.InitMilestone;
import hudson.model.Hudson;
import jenkins.ExtensionComponentSet;
import jenkins.model.Jenkins;
import hudson.util.AdaptedIterator;
import hudson.util.DescriptorList;
import hudson.util.Memoizer;
import hudson.util.Iterators;
import hudson.ExtensionPoint.LegacyInstancesAreScopedToHudson;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Retains the known extension instances for the given type 'T'.
 *
 * <p>
 * Extensions are loaded lazily on demand and automatically by using {@link ExtensionFinder}, but this
 * class also provides a mechanism to provide compatibility with the older {@link DescriptorList}-based
 * manual registration,
 *
 * <p>
 * All {@link ExtensionList} instances should be owned by {@link jenkins.model.Jenkins}, even though
 * extension points can be defined by anyone on any type. Use {@link jenkins.model.Jenkins#getExtensionList(Class)}
 * and {@link jenkins.model.Jenkins#getDescriptorList(Class)} to obtain the instances.
 *
 * @param <T>
 *      Type of the extension point. This class holds instances of the subtypes of 'T'. 
 *
 * @author Kohsuke Kawaguchi
 * @since 1.286
 * @see jenkins.model.Jenkins#getExtensionList(Class)
 * @see jenkins.model.Jenkins#getDescriptorList(Class)
 */
public class ExtensionList<T> extends AbstractList<T> {
    /**
     * @deprecated as of 1.417
     *      Use {@link #jenkins}
     */
    @Deprecated
    public final Hudson hudson;
    public final @CheckForNull Jenkins jenkins;
    public final Class<T> extensionType;

    /**
     * Once discovered, extensions are retained here.
     */
    @CopyOnWrite
    private volatile List<ExtensionComponent<T>> extensions;

    private List<ExtensionListListener> listeners = new CopyOnWriteArrayList<ExtensionListListener>();

    /**
     * Place to store manually registered instances with the per-Hudson scope.
     * {@link CopyOnWriteArrayList} is used here to support concurrent iterations and mutation.
     */
    private final CopyOnWriteArrayList<ExtensionComponent<T>> legacyInstances;

    /**
     * @deprecated as of 1.416
     *      Use {@link #ExtensionList(Jenkins, Class)}
     */
    @Deprecated
    protected ExtensionList(Hudson hudson, Class<T> extensionType) {
        this((Jenkins)hudson,extensionType);
    }

    protected ExtensionList(Jenkins jenkins, Class<T> extensionType) {
        this(jenkins,extensionType,new CopyOnWriteArrayList<ExtensionComponent<T>>());
    }

    /**
     * @deprecated as of 1.416
     *      Use {@link #ExtensionList(Jenkins, Class, CopyOnWriteArrayList)}
     */
    @Deprecated
    protected ExtensionList(Hudson hudson, Class<T> extensionType, CopyOnWriteArrayList<ExtensionComponent<T>> legacyStore) {
        this((Jenkins)hudson,extensionType,legacyStore);
    }

    /**
     *
     * @param legacyStore
     *      Place to store manually registered instances. The version of the constructor that
     *      omits this uses a new {@link Vector}, making the storage lifespan tied to the life of  {@link ExtensionList}.
     *      If the manually registered instances are scoped to VM level, the caller should pass in a static list. 
     */
    protected ExtensionList(Jenkins jenkins, Class<T> extensionType, CopyOnWriteArrayList<ExtensionComponent<T>> legacyStore) {
        this.hudson = (Hudson)jenkins;
        this.jenkins = jenkins;
        this.extensionType = extensionType;
        this.legacyInstances = legacyStore;
        if (jenkins == null) {
            extensions = Collections.emptyList();
        }
    }

    /**
     * Add a listener to the extension list.
     * @param listener The listener.
     */
    public void addListener(@Nonnull ExtensionListListener listener) {
        listeners.add(listener);
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

    @Override
    public Iterator<T> iterator() {
        // we need to intercept mutation, so for now don't allow Iterator.remove 
        return new AdaptedIterator<ExtensionComponent<T>,T>(Iterators.readOnly(ensureLoaded().iterator())) {
            protected T adapt(ExtensionComponent<T> item) {
                return item.getInstance();
            }
        };
    }

    /**
     * Gets the same thing as the 'this' list represents, except as {@link ExtensionComponent}s.
     */
    public List<ExtensionComponent<T>> getComponents() {
        return Collections.unmodifiableList(ensureLoaded());
    }

    public T get(int index) {
        return ensureLoaded().get(index).getInstance();
    }
    
    public int size() {
        return ensureLoaded().size();
    }

    /**
     * Gets the read-only view of this {@link ExtensionList} where components are reversed.
     */
    public List<T> reverseView() {
        return new AbstractList<T>() {
            @Override
            public T get(int index) {
                return ExtensionList.this.get(size()-index-1);
            }

            @Override
            public int size() {
                return ExtensionList.this.size();
            }
        };
    }

    @Override
    public boolean remove(Object o) {
        try {
            return removeSync(o);
        } finally {
            if(extensions!=null) {
                fireOnChangeListeners();
            }
        }
    }

    private synchronized boolean removeSync(Object o) {
        boolean removed = removeComponent(legacyInstances, o);
        if(extensions!=null) {
            List<ExtensionComponent<T>> r = new ArrayList<ExtensionComponent<T>>(extensions);
            removed |= removeComponent(r,o);
            extensions = sort(r);
        }
        return removed;
    }

    private <T> boolean removeComponent(Collection<ExtensionComponent<T>> collection, Object t) {
        for (Iterator<ExtensionComponent<T>> itr = collection.iterator(); itr.hasNext();) {
            ExtensionComponent<T> c =  itr.next();
            if (c.getInstance().equals(t)) {
                return collection.remove(c);
            }
        }
        return false;
    }

    @Override
    public final synchronized T remove(int index) {
        T t = get(index);
        remove(t);
        return t;
    }

    /**
     * Write access will put the instance into a legacy store.
     *
     * @deprecated since 2009-02-23.
     *      Prefer automatic registration.
     */
    @Override
    @Deprecated
    public boolean add(T t) {
        try {
            return addSync(t);
        } finally {
            if(extensions!=null) {
                fireOnChangeListeners();
            }
        }
    }

    private synchronized boolean addSync(T t) {
        legacyInstances.add(new ExtensionComponent<T>(t));
        // if we've already filled extensions, add it
        if(extensions!=null) {
            List<ExtensionComponent<T>> r = new ArrayList<ExtensionComponent<T>>(extensions);
            r.add(new ExtensionComponent<T>(t));
            extensions = sort(r);
        }
        return true;
    }

    @Override
    public void add(int index, T element) {
        add(element);
    }

    /**
     * Used to bind extension to URLs by their class names.
     *
     * @since 1.349
     */
    public T getDynamic(String className) {
        for (T t : this)
            if (t.getClass().getName().equals(className))
                return t;
        return null;
    }

    private List<ExtensionComponent<T>> ensureLoaded() {
        if(extensions!=null)
            return extensions; // already loaded
        if (jenkins.getInitLevel().compareTo(InitMilestone.PLUGINS_PREPARED)<0)
            return legacyInstances; // can't perform the auto discovery until all plugins are loaded, so just make the legacy instances visible

        synchronized (getLoadLock()) {
            if(extensions==null) {
                List<ExtensionComponent<T>> r = load();
                r.addAll(legacyInstances);
                extensions = sort(r);
            }
            return extensions;
        }
    }

    /**
     * Chooses the object that locks the loading of the extension instances.
     */
    protected Object getLoadLock() {
        return jenkins.lookup.setIfNull(Lock.class,new Lock());
    }

    /**
     * Used during {@link Jenkins#refreshExtensions()} to add new components into existing {@link ExtensionList}s.
     * Do not call from anywhere else.
     */
    public void refresh(ExtensionComponentSet delta) {
        boolean fireOnChangeListeners = false;
        synchronized (getLoadLock()) {
            if (extensions==null)
                return;     // not yet loaded. when we load it, we'll load everything visible by then, so no work needed

            Collection<ExtensionComponent<T>> found = load(delta);
            if (!found.isEmpty()) {
                List<ExtensionComponent<T>> l = Lists.newArrayList(extensions);
                l.addAll(found);
                extensions = sort(l);
                fireOnChangeListeners = true;
            }
        }
        if (fireOnChangeListeners) {
            fireOnChangeListeners();
        }
    }

    private void fireOnChangeListeners() {
        for (ExtensionListListener listener : listeners) {
            try {
                listener.onChange();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error firing ExtensionListListener.onChange().", e);
            }
        }
    }

    /**
     * Loading an {@link ExtensionList} can result in a nested loading of another {@link ExtensionList}.
     * What that means is that we need a single lock that spans across all the {@link ExtensionList}s,
     * or else we can end up in a dead lock.
     */
    private static final class Lock {}

    /**
     * Loads all the extensions.
     */
    protected List<ExtensionComponent<T>> load() {
        if (LOGGER.isLoggable(Level.FINE))
            LOGGER.log(Level.FINE,"Loading ExtensionList: "+extensionType, new Throwable());

        return jenkins.getPluginManager().getPluginStrategy().findComponents(extensionType, hudson);
    }

    /**
     * Picks up extensions that we care from the given list.
     */
    protected Collection<ExtensionComponent<T>> load(ExtensionComponentSet delta) {
        return delta.find(extensionType);
    }


    /**
     * If the {@link ExtensionList} implementation requires sorting extensions,
     * override this method to do so.
     *
     * <p>
     * The implementation should copy a list, do a sort, and return the new instance.
     */
    protected List<ExtensionComponent<T>> sort(List<ExtensionComponent<T>> r) {
        r = new ArrayList<ExtensionComponent<T>>(r);
        Collections.sort(r);
        return r;
    }

    /**
     * @deprecated as of 1.416
     *      Use {@link #create(Jenkins, Class)}
     */
    @Deprecated
    public static <T> ExtensionList<T> create(Hudson hudson, Class<T> type) {
        return create((Jenkins)hudson,type);
    }

    public static <T> ExtensionList<T> create(Jenkins jenkins, Class<T> type) {
        if(type.getAnnotation(LegacyInstancesAreScopedToHudson.class)!=null)
            return new ExtensionList<T>(jenkins,type);
        else {
            return new ExtensionList<T>(jenkins,type,staticLegacyInstances.get(type));
        }
    }

    /**
     * Gets the extension list for a given type.
     * Normally calls {@link Jenkins#getExtensionList(Class)} but falls back to an empty list
     * in case {@link Jenkins#getInstance} is null.
     * Thus it is useful to call from {@code all()} methods which need to behave gracefully during startup or shutdown.
     * @param type the extension point type
     * @return some list
     * @since 1.572
     */
    public static @Nonnull <T> ExtensionList<T> lookup(Class<T> type) {
        Jenkins j = Jenkins.getInstance();
        return j == null ? create((Jenkins) null, type) : j.getExtensionList(type);
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

    private static final Logger LOGGER = Logger.getLogger(ExtensionList.class.getName());
}
