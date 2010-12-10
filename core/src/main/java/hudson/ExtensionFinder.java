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
import hudson.util.AdaptedIterator;
import hudson.util.Iterators;
import net.java.sezpoz.Index;
import net.java.sezpoz.IndexItem;
import hudson.model.Hudson;
import hudson.model.Descriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static java.util.logging.Level.*;

/**
 * Discovers the implementations of an extension point.
 *
 * <p>
 * This extension point allows you to write your implementations of {@link ExtensionPoint}s
 * in arbitrary DI containers, and have Hudson discover them.
 *
 * <p>
 * {@link ExtensionFinder} itself is an extension point, but to avoid infinite recursion,
 * Hudson discovers {@link ExtensionFinder}s through {@link Sezpoz} and that alone.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.286
 */
public abstract class ExtensionFinder implements ExtensionPoint {
    /**
     * @deprecated as of 1.356
     *      Use and implement {@link #find(Class, Hudson)} that allows us to put some metadata.
     */
    @Restricted(NoExternalUse.class)
    public <T> Collection<T> findExtensions(Class<T> type, Hudson hudson) {
        return Collections.emptyList();
    }

    /**
     * Discover extensions of the given type.
     *
     * <p>
     * This method is called only once per the given type after all the plugins are loaded,
     * so implementations need not worry about caching.
     *
     * @param <T>
     *      The type of the extension points. This is not bound to {@link ExtensionPoint} because
     *      of {@link Descriptor}, which by itself doesn't implement {@link ExtensionPoint} for
     *      a historical reason.
     * @param hudson
     *      Hudson whose behalf this extension finder is performing lookup.
     * @return
     *      Can be empty but never null.
     * @since 1.356
     *      Older implementations provide {@link #findExtensions(Class, Hudson)}
     */
    public abstract <T> Collection<ExtensionComponent<T>> find(Class<T> type, Hudson hudson);

    /**
     * A pointless function to work around what appears to be a HotSpot problem. See HUDSON-5756 and bug 6933067
     * on BugParade for more details.
     */
    public <T> Collection<ExtensionComponent<T>> _find(Class<T> type, Hudson hudson) {
        return find(type,hudson);
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
     * Such activitiy locks class initialization first, then locks extension list.
     *
     * <p>
     * This inconsistent locking order results in a dead lock, you see.
     *
     * <p>
     * So to reduce the likelihood, this method is called in prior to {@link #find(Class, Hudson)} invocation,
     * but from outside the lock. The implementation is expected to perform all the class initialization activities
     * from here.
     *
     * <p>
     * See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6459208 for how to force a class initialization.
     * Also see http://kohsuke.org/2010/09/01/deadlock-that-you-cant-avoid/ for how class initialization
     * can results in a dead lock.
     */
    public void scout(Class extensionType, Hudson hudson) {
    }

    @Extension
    public static final class GuiceFinder extends AbstractGuiceFinder<Extension> {
        public GuiceFinder() {
            super(Extension.class);
        }

        @Override
        protected boolean isOptional(Extension annotation) {
            return annotation.optional();
        }
    }

    /**
     * Discovers components via sezpoz but instantiates them by using Guice.
     */
    public static abstract class AbstractGuiceFinder<T extends Annotation> extends ExtensionFinder {
        private Injector container;

        public AbstractGuiceFinder(final Class<T> annotationType) {
            List<Module> modules = new ArrayList<Module>();
            modules.add(new AbstractModule() {
                @Override
                protected void configure() {
                    int id=0;

                    for (final AnnotatedElement e : combinedIndex(Extension.class, Hudson.getInstance().getPluginManager().uberClassLoader)) {
                        if (e==null)    continue;
                        final T a = e.getAnnotation(annotationType);
                        if (a==null)    continue;   // huh?

                        if (!isActive(e)) continue;

                        if (e instanceof Class) {
                            bind((Class<?>)e).in(FAULT_TOLERANT_SCOPE);
                        } else {
                            Class extType = getInstanceType(e);

                            // use arbitrary id to disambiguate
                            bind(extType).annotatedWith(Names.named(String.valueOf(id++)))
                                .toProvider(new Provider() {
                                    public Object get() {
                                        try {
                                            return InstanceFactory._for(e).create();
                                        } catch (InstantiationException e) {
                                            LOGGER.log(isOptional(a) ? FINE : WARNING, "Failed to load "+e, e);
                                            return null;
                                        }
                                    }
                                }).in(FAULT_TOLERANT_SCOPE);
                        }
                    }
                }
            });

            for (ExtensionComponent<Module> ec : new Sezpoz().find(Module.class, Hudson.getInstance())) {
                modules.add(ec.getInstance());
            }

            container = Guice.createInjector(modules);
        }

        /**
         * Hook to enable subtypes to control which ones to pick up and which ones to ignore.
         */
        protected boolean isActive(AnnotatedElement e) {
            return true;
        }

        protected abstract boolean isOptional(T annotation);

        public <T> Collection<ExtensionComponent<T>> find(Class<T> type, Hudson hudson) {
            List<ExtensionComponent<T>> result = new ArrayList<ExtensionComponent<T>>();

            for (Entry<Key<?>, Binding<?>> e : container.getBindings().entrySet()) {
                if (type.isAssignableFrom(e.getKey().getTypeLiteral().getRawType())) {
                    // TODO: how do we get ordinal?
                    Object o = e.getValue().getProvider().get();
                    if (o!=null)
                        result.add(new ExtensionComponent<T>(type.cast(o)));
                }
            }

            return result;
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
         * This is necessary as a failure to load one plugin shouldn't fail the startup of the entire Hudson.
         * Instead, we should just drop the failing plugins.
         */
        public static final Scope FAULT_TOLERANT_SCOPE = new Scope() {
            public <T> Provider<T> scope(Key<T> key, Provider<T> unscoped) {
                final Provider<T> base = Scopes.SINGLETON.scope(key,unscoped);
                return new Provider<T>() {
                    public T get() {
                        try {
                            return base.get();
                        } catch (Exception e) {
                            LOGGER.log(WARNING,"Failed to instantiate",e);
                            return null;
                        }
                    }
                };
            }
        };

        private static final Logger LOGGER = Logger.getLogger(GuiceFinder.class.getName());
    }

    /**
     * The bootstrap implementation that looks for the {@link Extension} marker.
     *
     * <p>
     * Uses Sezpoz as the underlying mechanism.
     */
    public static final class Sezpoz extends ExtensionFinder {
        public <T> Collection<ExtensionComponent<T>> find(Class<T> type, Hudson hudson) {

            List<ExtensionComponent<T>> result = new ArrayList<ExtensionComponent<T>>();
            for (AnnotatedElement e : combinedIndex(Extension.class, hudson.getPluginManager().uberClassLoader)) {
                if (e==null)    continue;
                Extension a = e.getAnnotation(Extension.class);
                if (a==null)    continue;   // huh?
                try {
                    Class<?> extType = getInstanceType(e);
                    if(type.isAssignableFrom(extType)) {
                        Object instance = InstanceFactory._for(e).create();
                        if (instance!=null)
                            result.add(new ExtensionComponent<T>(type.cast(instance), a));
                    }
                } catch (LinkageError x) {
                    // sometimes the instantiation fails in an indirect classloading failure,
                    // which results in a LinkageError
                    LOGGER.log(a.optional() ? FINE : WARNING, "Failed to load "+e, x);
                } catch (InstantiationException x) {
                    LOGGER.log(a.optional() ? FINE : WARNING, "Failed to load "+e, x);
                }
            }

            return result;
        }

        @Override
        public void scout(Class extensionType, Hudson hudson) {
            ClassLoader cl = hudson.getPluginManager().uberClassLoader;
            for (IndexItem<Extension,Object> item : Index.load(Extension.class, Object.class, cl)) {
                try {
                    Class<?> extType = getInstanceType(item.element());
                    
                    // accroding to http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6459208
                    // this appears to be the only way to force a class initialization
                    Class.forName(extType.getName(),true,extType.getClassLoader());
                } catch (InstantiationException e) {
                    LOGGER.log(item.annotation().optional() ? FINE : WARNING,
                               "Failed to scout "+item.className(), e);
                } catch (ClassNotFoundException e) {
                    LOGGER.log(WARNING,"Failed to scout "+item.className(), e);
                } catch (LinkageError e) {
                    LOGGER.log(WARNING,"Failed to scout "+item.className(), e);
                }
            }
        }
    }

    /**
     * Loads the combined index from sezpoz and annotation indexer.
     * We used to use sezpoz then we switched to annotation-indexer, so to load old plugins we need
     * to look for both.
     */
    private static <T extends Annotation> Iterable<AnnotatedElement> combinedIndex(Class<T> annotationType, ClassLoader cl) {
        final Index<T, Object> sezpoz = Index.load(annotationType, Object.class, cl);

        // load index from sezpoz
        Iterable<AnnotatedElement> itr = new Iterable<AnnotatedElement>() {
                    public Iterator<AnnotatedElement> iterator() {
                        return new AdaptedIterator<IndexItem<T, Object>, AnnotatedElement>(sezpoz.iterator()) {
                            protected AnnotatedElement adapt(IndexItem<T, Object> item) {
                                try {
                                    return item.element();
                                } catch (LinkageError e) {
                                    // sometimes the instantiation fails in an indirect classloading failure,
                                    // which results in a LinkageError
                                    LOGGER.log(WARNING, "Failed to load " + item.className(), e);
                                } catch (InstantiationException e) {
                                    LOGGER.log(WARNING, "Failed to load " + item.className(), e);
                                }
                                return null;
                            }
                        };
                    }
                };

        // also load index from annotation-indexer
        try {
            itr = Iterators.sequence(itr, org.jvnet.hudson.annotation_indexer.Index.list(annotationType, cl));
        } catch (IOException e) {
            LOGGER.log(WARNING, "Failed to list index",e);
        }

        // TODO: remove nulls

        // during development it's possible to have both indices if earlier artifacts remain in target/classes,
        // so remove duplicates to avoid double-counting.
        return Iterators.removeDups(itr);
    }

    static Class getInstanceType(AnnotatedElement e) {
        if (e instanceof Class)
            return (Class) e;
        if (e instanceof Field)
            return ((Field)e).getType();
        if (e instanceof Method)
            return ((Method)e).getReturnType();
        throw new AssertionError();
    }

    /**
     * Abstracts away how we obtain an instance from constructor/method/field.
     */
    static abstract class InstanceFactory {
        abstract Object create() throws InstantiationException;

        static InstanceFactory _for(final AnnotatedElement e) {
            if (e instanceof Class) {
                return new InstanceFactory() {
                    Object create() throws InstantiationException {
                        try {
                            return ((Class) e).newInstance();
                        } catch (IllegalAccessException x) {
                            throw (InstantiationException)new InstantiationException("Failed to instantiate "+e).initCause(x);
                        } catch (LinkageError x) {
                            // sometimes the instantiation fails in an indirect classloading failure,
                            // which results in a LinkageError
                            throw (InstantiationException)new InstantiationException("Failed to instantiate "+e).initCause(x);
                        }
                    }
                };
            }
            if (e instanceof Field) {
                return new InstanceFactory() {
                    Object create() throws InstantiationException {
                        try {
                            return ((Field) e).get(null);
                        } catch (IllegalAccessException x) {
                            throw (InstantiationException)new InstantiationException("Failed to instantiate "+e).initCause(x);
                        } catch (LinkageError x) {
                            throw (InstantiationException)new InstantiationException("Failed to instantiate "+e).initCause(x);
                        }
                    }
                };
            }
            if (e instanceof Method) {
                return new InstanceFactory() {
                    Object create() throws InstantiationException {
                        try {
                            return ((Method) e).invoke(null);
                        } catch (IllegalAccessException x) {
                            throw (InstantiationException)new InstantiationException("Failed to instantiate "+e).initCause(x);
                        } catch (InvocationTargetException x) {
                            throw (InstantiationException)new InstantiationException("Failed to instantiate "+e).initCause(x);
                        } catch (LinkageError x) {
                            throw (InstantiationException)new InstantiationException("Failed to instantiate "+e).initCause(x);
                        }
                    }
                };
            }

            throw new AssertionError();
        }

        static InstanceFactory wrap(final IndexItem<?,Object> item) {
            return new InstanceFactory() {
                Object create() throws InstantiationException {
                    try {
                        return item.instance();
                    } catch (LinkageError x) {
                        throw (InstantiationException)new InstantiationException("Failed to instantiate "+item.className()).initCause(x);
                    }
                }
            };
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ExtensionFinder.class.getName());
}
