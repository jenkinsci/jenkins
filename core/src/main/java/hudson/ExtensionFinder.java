/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., InfraDNA, Inc., CloudBees, Inc.
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

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.google.common.collect.ImmutableList;
import hudson.init.InitMilestone;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import jenkins.ExtensionComponentSet;
import jenkins.ExtensionFilter;
import jenkins.ExtensionRefreshException;
import jenkins.ProxyInjector;
import jenkins.model.Jenkins;
import net.java.sezpoz.Index;
import net.java.sezpoz.IndexItem;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Discovers the implementations of an extension point.
 *
 * <p>
 * This extension point allows you to write your implementations of {@link ExtensionPoint}s
 * in arbitrary DI containers, and have Hudson discover them.
 *
 * <p>
 * {@link ExtensionFinder} itself is an extension point, but to avoid infinite recursion,
 * Jenkins discovers {@link ExtensionFinder}s through {@link Sezpoz} and that alone.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.286
 * @see ExtensionFilter
 */
public abstract class ExtensionFinder implements ExtensionPoint {
    /**
     * @deprecated as of 1.356
     *      Use and implement {@link #find(Class,Hudson)} that allows us to put some metadata.
     */
    @Restricted(NoExternalUse.class)
    @Deprecated
    public <T> Collection<T> findExtensions(Class<T> type, Hudson hudson) {
        return Collections.emptyList();
    }

    /**
     * Returns true if this extension finder supports the {@link #refresh()} operation.
     */
    public boolean isRefreshable() {
        try {
            return getClass().getMethod("refresh").getDeclaringClass()!=ExtensionFinder.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Rebuilds the internal index, if any, so that future {@link #find(Class, Hudson)} calls
     * will discover components newly added to {@link PluginManager#uberClassLoader}.
     *
     * <p>
     * The point of the refresh operation is not to disrupt instances of already loaded {@link ExtensionComponent}s,
     * and only instantiate those that are new. Otherwise this will break the singleton semantics of various
     * objects, such as {@link Descriptor}s.
     *
     * <p>
     * The behaviour is undefined if {@link #isRefreshable()} is returning false.
     *
     * @since 1.442
     * @see #isRefreshable()
     * @return never null
     */
    public abstract ExtensionComponentSet refresh() throws ExtensionRefreshException;

    /**
     * Discover extensions of the given type.
     *
     * <p>
     * This method is called only once per the given type after all the plugins are loaded,
     * so implementations need not worry about caching.
     *
     * <p>
     * This method should return all the known components at the time of the call, including
     * those that are discovered later via {@link #refresh()}, even though those components
     * are separately returned in {@link ExtensionComponentSet}.
     *
     * @param <T>
     *      The type of the extension points. This is not bound to {@link ExtensionPoint} because
     *      of {@link Descriptor}, which by itself doesn't implement {@link ExtensionPoint} for
     *      a historical reason.
     * @param jenkins
     *      Jenkins whose behalf this extension finder is performing lookup.
     * @return
     *      Can be empty but never null.
     * @since 1.356
     *      Older implementations provide {@link #findExtensions(Class,Hudson)}
     */
    public abstract <T> Collection<ExtensionComponent<T>> find(Class<T> type, Hudson jenkins);

    @Deprecated
    public <T> Collection<ExtensionComponent<T>> _find(Class<T> type, Hudson hudson) {
        return find(type, hudson);
    }

    /**
     * Performs class initializations without creating instances. 
     *
     * If two threads try to initialize classes in the opposite order, a dead lock will ensue,
     * and we can get into a similar situation with {@link ExtensionFinder}s.
     *
     * <p>
     * That is, one thread can try to list extensions, which results in {@link ExtensionFinder}
     * loading and initializing classes. This happens inside a context of a lock, so that
     * another thread that tries to list the same extensions don't end up creating different
     * extension instances. So this activity locks extension list first, then class initialization next.
     *
     * <p>
     * In the mean time, another thread can load and initialize a class, and that initialization
     * can eventually results in listing up extensions, for example through static initializer.
     * Such activity locks class initialization first, then locks extension list.
     *
     * <p>
     * This inconsistent locking order results in a dead lock, you see.
     *
     * <p>
     * So to reduce the likelihood, this method is called in prior to {@link #find(Class,Hudson)} invocation,
     * but from outside the lock. The implementation is expected to perform all the class initialization activities
     * from here.
     *
     * <p>
     * See https://bugs.openjdk.java.net/browse/JDK-4993813 for how to force a class initialization.
     * Also see http://kohsuke.org/2010/09/01/deadlock-that-you-cant-avoid/ for how class initialization
     * can results in a dead lock.
     */
    public void scout(Class extensionType, Hudson hudson) {
    }

    @Extension
    public static final class DefaultGuiceExtensionAnnotation extends GuiceExtensionAnnotation<Extension> {
        public DefaultGuiceExtensionAnnotation() {
            super(Extension.class);
        }

        @Override
        protected boolean isOptional(Extension annotation) {
            return annotation.optional();
        }

        @Override
        protected double getOrdinal(Extension annotation) {
            return annotation.ordinal();
        }

        @Override
        protected boolean isActive(AnnotatedElement e) {
            return true;
        }
    }


    /**
     * Captures information about the annotation that we use to mark Guice-instantiated components.
     */
    public static abstract class GuiceExtensionAnnotation<T extends Annotation> {
        public final Class<T> annotationType;

        protected GuiceExtensionAnnotation(Class<T> annotationType) {
            this.annotationType = annotationType;
        }

        protected abstract double getOrdinal(T annotation);

        /**
         * Hook to enable subtypes to control which ones to pick up and which ones to ignore.
         */
        protected abstract boolean isActive(AnnotatedElement e);

        protected abstract boolean isOptional(T annotation);
    }
    
    /**
     * Discovers components via sezpoz but instantiates them by using Guice.
     */
    @Extension
    public static class GuiceFinder extends ExtensionFinder {
        /**
         * Injector that we find components from.
         * <p>
         * To support refresh when Guice doesn't let us alter the bindings, we'll create
         * a child container to house newly discovered components. This field points to the
         * youngest such container.
         */
        private volatile Injector container;

        /**
         * Sezpoz index we are currently using in {@link #container} (and its ancestors.)
         * Needed to compute delta.
         */
        private List<IndexItem<?,Object>> sezpozIndex;

        private final Map<Key,Annotation> annotations = new HashMap<>();
        private final Sezpoz moduleFinder = new Sezpoz();

        /**
         * Map from {@link GuiceExtensionAnnotation#annotationType} to {@link GuiceExtensionAnnotation}
         */
        private Map<Class<? extends Annotation>,GuiceExtensionAnnotation<?>> extensionAnnotations = Maps.newHashMap();

        public GuiceFinder() {
            for (ExtensionComponent<GuiceExtensionAnnotation> ec : moduleFinder.find(GuiceExtensionAnnotation.class, Hudson.getInstance())) {
                GuiceExtensionAnnotation gea = ec.getInstance();
                extensionAnnotations.put(gea.annotationType,gea);
            }

            sezpozIndex = loadSezpozIndices(Jenkins.getInstance().getPluginManager().uberClassLoader);

            List<Module> modules = new ArrayList<>();
            modules.add(new AbstractModule() {
                @Override
                protected void configure() {
                    Jenkins j = Jenkins.getInstance();
                    bind(Jenkins.class).toInstance(j);
                    bind(PluginManager.class).toInstance(j.getPluginManager());
                }
            });
            modules.add(new SezpozModule(sezpozIndex));

            for (ExtensionComponent<Module> ec : moduleFinder.find(Module.class, Hudson.getInstance())) {
                modules.add(ec.getInstance());
            }

            try {
                container = Guice.createInjector(modules);
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Failed to create Guice container from all the plugins",e);
                // failing to load all bindings are disastrous, so recover by creating minimum that works
                // by just including the core
                container = Guice.createInjector(new SezpozModule(loadSezpozIndices(Jenkins.class.getClassLoader())));
            }

            // expose Injector via lookup mechanism for interop with non-Guice clients
            Jenkins.getInstance().lookup.set(Injector.class,new ProxyInjector() {
                protected Injector resolve() {
                    return getContainer();
                }
            });
        }

        private ImmutableList<IndexItem<?, Object>> loadSezpozIndices(ClassLoader classLoader) {
            List<IndexItem<?,Object>> indices = Lists.newArrayList();
            for (GuiceExtensionAnnotation<?> gea : extensionAnnotations.values()) {
                Iterables.addAll(indices, Index.load(gea.annotationType, Object.class, classLoader));
            }
            return ImmutableList.copyOf(indices);
        }

        public Injector getContainer() {
            return container;
        }

        /**
         * The basic idea is:
         *
         * <ul>
         *     <li>List up delta as a series of modules
         *     <li>
         * </ul>
         */
        @Override
        public synchronized ExtensionComponentSet refresh() throws ExtensionRefreshException {
            // figure out newly discovered sezpoz components
            List<IndexItem<?, Object>> delta = Lists.newArrayList();
            for (Class<? extends Annotation> annotationType : extensionAnnotations.keySet()) {
                delta.addAll(Sezpoz.listDelta(annotationType,sezpozIndex));
            }
            List<IndexItem<?, Object>> l = Lists.newArrayList(sezpozIndex);
            l.addAll(delta);
            sezpozIndex = l;

            List<Module> modules = new ArrayList<>();
            modules.add(new SezpozModule(delta));
            for (ExtensionComponent<Module> ec : moduleFinder.refresh().find(Module.class)) {
                modules.add(ec.getInstance());
            }

            try {
                final Injector child = container.createChildInjector(modules);
                container = child;

                return new ExtensionComponentSet() {
                    @Override
                    public <T> Collection<ExtensionComponent<T>> find(Class<T> type) {
                        List<ExtensionComponent<T>> result = new ArrayList<>();
                        _find(type, result, child);
                        return result;
                    }
                };
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Failed to create Guice container from newly added plugins",e);
                throw new ExtensionRefreshException(e);
            }
        }

        private Object instantiate(IndexItem<?,Object> item) {
            try {
                return item.instance();
            } catch (LinkageError | Exception e) {
                // sometimes the instantiation fails in an indirect classloading failure,
                // which results in a LinkageError
                LOGGER.log(isOptional(item.annotation()) ? Level.FINE : Level.WARNING,
                           "Failed to load "+item.className(), e);
            }
            return null;
        }

        private boolean isOptional(Annotation annotation) {
            GuiceExtensionAnnotation gea = extensionAnnotations.get(annotation.annotationType());
            return gea.isOptional(annotation);
        }

        private boolean isActive(Annotation annotation, AnnotatedElement e) {
            GuiceExtensionAnnotation gea = extensionAnnotations.get(annotation.annotationType());
            return gea.isActive(e);
        }

        public <U> Collection<ExtensionComponent<U>> find(Class<U> type, Hudson jenkins) {
            // the find method contract requires us to traverse all known components
            List<ExtensionComponent<U>> result = new ArrayList<>();
            for (Injector i=container; i!=null; i=i.getParent()) {
                _find(type, result, i);
            }
            return result;
        }

        private <U> void _find(Class<U> type, List<ExtensionComponent<U>> result, Injector container) {
            for (Entry<Key<?>, Binding<?>> e : container.getBindings().entrySet()) {
                if (type.isAssignableFrom(e.getKey().getTypeLiteral().getRawType())) {
                    Annotation a = annotations.get(e.getKey());
                    Object o = e.getValue().getProvider().get();
                    if (o!=null) {
                        GuiceExtensionAnnotation gea = a!=null ? extensionAnnotations.get(a.annotationType()) : null;
                        result.add(new ExtensionComponent<>(type.cast(o), gea != null ? gea.getOrdinal(a) : 0));
                    }
                }
            }
        }

        /**
         * TODO: need to learn more about concurrent access to {@link Injector} and how it interacts
         * with classloading.
         */
        @Override
        public void scout(Class extensionType, Hudson hudson) {
        }

        /**
         * {@link Scope} that allows a failure to create a component,
         * and change the value to null.
         *
         * <p>
         * This is necessary as a failure to load one plugin shouldn't fail the startup of the entire Jenkins.
         * Instead, we should just drop the failing plugins.
         */
        public static final Scope FAULT_TOLERANT_SCOPE = new FaultTolerantScope(true);
        private static final Scope QUIET_FAULT_TOLERANT_SCOPE = new FaultTolerantScope(false);
        
        private static final class FaultTolerantScope implements Scope {
            private final boolean verbose;
            FaultTolerantScope(boolean verbose) {
                this.verbose = verbose;
            }
            public <T> Provider<T> scope(final Key<T> key, final Provider<T> unscoped) {
                final Provider<T> base = Scopes.SINGLETON.scope(key,unscoped);
                return new Provider<T>() {
                    public T get() {
                        try {
                            return base.get();
                        } catch (Exception | LinkageError e) {
                            error(key, e);
                            return null;
                        }
                    }
                    void error(Key<T> key, Throwable x) {
                        if (verbose) {
                            LOGGER.log(Level.WARNING, "Failed to instantiate " + key + "; skipping this component", x);
                        } else {
                            LOGGER.log(Level.WARNING, "Failed to instantiate optional component {0}; skipping", key.getTypeLiteral());
                            LOGGER.log(Level.FINE, key.toString(), x);
                        }
                    }
                };
            }
        }

        private static final Logger LOGGER = Logger.getLogger(GuiceFinder.class.getName());

        /**
         * {@link Module} that finds components via sezpoz index.
         * Instead of using SezPoz to instantiate, we'll instantiate them by using Guice,
         * so that we can take advantage of dependency injection.
         */
        private class SezpozModule extends AbstractModule {
            private final List<IndexItem<?,Object>> index;

            public SezpozModule(List<IndexItem<?,Object>> index) {
                this.index = index;
            }

            /**
             * Guice performs various reflection operations on the class to figure out the dependency graph,
             * and that process can cause additional classloading problems, which will fail the injector creation,
             * which in turn has disastrous effect on the startup.
             *
             * <p>
             * Ultimately I'd like to isolate problems to plugins and selectively disable them, allowing
             * Jenkins to start with plugins that work, but I haven't figured out how.
             *
             * So this is an attempt to detect subset of problems eagerly, by invoking various reflection
             * operations and try to find non-existent classes early.
             */
            private void resolve(Class c) {
                try {
                    c.getGenericSuperclass();
                    c.getGenericInterfaces();
                    ClassLoader ecl = c.getClassLoader();
                    Method m = ClassLoader.class.getDeclaredMethod("resolveClass", Class.class);
                    m.setAccessible(true);
                    m.invoke(ecl, c);
                    c.getConstructors();
                    c.getMethods();
                    for (Field f : c.getFields()) {
                        if (f.getAnnotation(javax.inject.Inject.class) != null || f.getAnnotation(com.google.inject.Inject.class) != null) {
                            resolve(f.getType());
                        }
                    }
                    LOGGER.log(Level.FINER, "{0} looks OK", c);
                    while (c != Object.class) {
                        c.getGenericSuperclass();
                        c = c.getSuperclass();
                    }
                } catch (Exception x) {
                    throw (LinkageError)new LinkageError("Failed to resolve "+c).initCause(x);
                }
            }

            @SuppressWarnings({"unchecked", "ChainOfInstanceofChecks"})
            @Override
            protected void configure() {
                for (final IndexItem<?,Object> item : index) {
                    boolean optional = isOptional(item.annotation());
                    try {
                        AnnotatedElement e = item.element();
                        Annotation a = item.annotation();
                        if (!isActive(a,e))   continue;

                        Scope scope = optional ? QUIET_FAULT_TOLERANT_SCOPE : FAULT_TOLERANT_SCOPE;
                        if (e instanceof Class) {
                            Key key = Key.get((Class)e);
                            resolve((Class)e);
                            annotations.put(key,a);
                            bind(key).in(scope);
                        } else {
                            Class extType;
                            if (e instanceof Field) {
                                extType = ((Field)e).getType();
                            } else
                            if (e instanceof Method) {
                                extType = ((Method)e).getReturnType();
                            } else
                                throw new AssertionError();

                            resolve(extType);

                            // make unique key, because Guice wants that.
                            Key key = Key.get(extType, Names.named(item.className() + "." + item.memberName()));
                            annotations.put(key,a);
                            bind(key).toProvider(new Provider() {
                                    public Object get() {
                                        return instantiate(item);
                                    }
                                }).in(scope);
                        }
                    } catch (Exception|LinkageError e) {
                        // sometimes the instantiation fails in an indirect classloading failure,
                        // which results in a LinkageError
                        LOGGER.log(optional ? Level.FINE : Level.WARNING,
                                   "Failed to load "+item.className(), e);
                    }
                }
            }
        }
    }

    /**
     * The bootstrap implementation that looks for the {@link Extension} marker.
     *
     * <p>
     * Uses Sezpoz as the underlying mechanism.
     */
    public static final class Sezpoz extends ExtensionFinder {

        private volatile List<IndexItem<Extension,Object>> indices;

        /**
         * Loads indices (ideally once but as few times as possible), then reuse them later.
         * {@link ExtensionList#ensureLoaded()} guarantees that this method won't be called until
         * {@link InitMilestone#PLUGINS_PREPARED} is attained, so this method is guaranteed to
         * see all the classes and indices.
         */
        private List<IndexItem<Extension,Object>> getIndices() {
            // this method cannot be synchronized because of a dead lock possibility in the following order of events:
            // 1. thread X can start listing indices, locking this object 'SZ'
            // 2. thread Y starts loading a class, locking a classloader 'CL'
            // 3. thread X needs to load a class, now blocked on CL
            // 4. thread Y decides to load extensions, now blocked on SZ.
            // 5. dead lock
            if (indices==null) {
                ClassLoader cl = Jenkins.getInstance().getPluginManager().uberClassLoader;
                indices = ImmutableList.copyOf(Index.load(Extension.class, Object.class, cl));
            }
            return indices;
        }

        /**
         * {@inheritDoc}
         *
         * <p>
         * SezPoz implements value-equality of {@link IndexItem}, so
         */
        @Override
        public synchronized ExtensionComponentSet refresh() {
            final List<IndexItem<Extension,Object>> old = indices;
            if (old==null)      return ExtensionComponentSet.EMPTY; // we haven't loaded anything

            final List<IndexItem<Extension, Object>> delta = listDelta(Extension.class,old);

            List<IndexItem<Extension,Object>> r = Lists.newArrayList(old);
            r.addAll(delta);
            indices = ImmutableList.copyOf(r);

            return new ExtensionComponentSet() {
                @Override
                public <T> Collection<ExtensionComponent<T>> find(Class<T> type) {
                    return _find(type,delta);
                }
            };
        }

        static <T extends Annotation> List<IndexItem<T,Object>> listDelta(Class<T> annotationType, List<? extends IndexItem<?,Object>> old) {
            // list up newly discovered components
            final List<IndexItem<T,Object>> delta = Lists.newArrayList();
            ClassLoader cl = Jenkins.getInstance().getPluginManager().uberClassLoader;
            for (IndexItem<T,Object> ii : Index.load(annotationType, Object.class, cl)) {
                if (!old.contains(ii)) {
                    delta.add(ii);
                }
            }
            return delta;
        }

        public <T> Collection<ExtensionComponent<T>> find(Class<T> type, Hudson jenkins) {
            return _find(type,getIndices());
        }

        /**
         * Finds all the matching {@link IndexItem}s that match the given type and instantiate them.
         */
        private <T> Collection<ExtensionComponent<T>> _find(Class<T> type, List<IndexItem<Extension,Object>> indices) {
            List<ExtensionComponent<T>> result = new ArrayList<>();

            for (IndexItem<Extension,Object> item : indices) {
                try {
                    AnnotatedElement e = item.element();
                    Class<?> extType;
                    if (e instanceof Class) {
                        extType = (Class) e;
                    } else
                    if (e instanceof Field) {
                        extType = ((Field)e).getType();
                    } else
                    if (e instanceof Method) {
                        extType = ((Method)e).getReturnType();
                    } else
                        throw new AssertionError();

                    if(type.isAssignableFrom(extType)) {
                        Object instance = item.instance();
                        if(instance!=null)
                            result.add(new ExtensionComponent<>(type.cast(instance),item.annotation()));
                    }
                } catch (LinkageError|Exception e) {
                    // sometimes the instantiation fails in an indirect classloading failure,
                    // which results in a LinkageError
                    LOGGER.log(logLevel(item), "Failed to load "+item.className(), e);
                }
            }

            return result;
        }

        @Override
        public void scout(Class extensionType, Hudson hudson) {
            for (IndexItem<Extension,Object> item : getIndices()) {
                try {
                    // we might end up having multiple threads concurrently calling into element(),
                    // but we can't synchronize this --- if we do, the one thread that's supposed to load a class
                    // can block while other threads wait for the entry into the element call().
                    // looking at the sezpoz code, it should be safe to do so
                    AnnotatedElement e = item.element();
                    Class<?> extType;
                    if (e instanceof Class) {
                        extType = (Class) e;
                    } else
                    if (e instanceof Field) {
                        extType = ((Field)e).getType();
                    } else
                    if (e instanceof Method) {
                        extType = ((Method)e).getReturnType();
                    } else
                        throw new AssertionError();
                    // according to JDK-4993813 this is the only way to force class initialization
                    Class.forName(extType.getName(),true,extType.getClassLoader());
                } catch (Exception | LinkageError e) {
                    LOGGER.log(logLevel(item), "Failed to scout "+item.className(), e);
                }
            }
        }

        private Level logLevel(IndexItem<Extension, Object> item) {
            return item.annotation().optional() ? Level.FINE : Level.WARNING;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ExtensionFinder.class.getName());
}
