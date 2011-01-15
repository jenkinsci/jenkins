/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., InfraDNA, Inc.
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
import hudson.model.Hudson;
import net.java.sezpoz.Index;
import net.java.sezpoz.IndexItem;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    public static class SpringExtensionFinder extends AbstractSpringExtensionFinder<Extension> {

        public SpringExtensionFinder() {
            super(Extension.class);
        }

        @Override
        protected boolean isOptional(Extension annotation) {
            return false;
        }
    }

    public static abstract class AbstractSpringExtensionFinder<T extends Annotation> extends ExtensionFinder {
        private final AnnotationConfigApplicationContext applicationContext;

        public AbstractSpringExtensionFinder(final Class<T> annotationType) {

            applicationContext = new AnnotationConfigApplicationContext();
            applicationContext.addBeanFactoryPostProcessor(new BeanFactoryPostProcessor() {
                public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
                    for (String name: beanFactory.getBeanDefinitionNames()) {
                        BeanDefinition beanDefinition = beanFactory.getBeanDefinition(name);
                        beanDefinition.setLazyInit(true);
                    }
                }
            });
            ClassLoader cl = Hudson.getInstance().getPluginManager().uberClassLoader;
            int id=0;

            for (final IndexItem<T,Object> item : Index.load(annotationType, Object.class, cl)) {
                id++;
                try {
                    AnnotatedElement e = item.element();
                    if (!isActive(e))   continue;
                    T a = item.annotation();

                    if (e instanceof Class) {
                        applicationContext.register((Class) e);
                    } else {
                        Class extType;
                        if (e instanceof Field) {
                            extType = ((Field)e).getType();
                        } else
                        if (e instanceof Method) {
                            extType = ((Method)e).getReturnType();
                        } else
                            throw new AssertionError();

                        final Class extTypeFinal = extType;
                        // use arbitrary
                        applicationContext.getBeanFactory().registerSingleton(String.valueOf(id), createProvider(extType, item));
                    }
                } catch (LinkageError e) {
                    // sometimes the instantiation fails in an indirect classloading failure,
                    // which results in a LinkageError
                    LOGGER.log(isOptional(item.annotation()) ? Level.FINE : Level.WARNING,
                               "Failed to load "+item.className(), e);
                } catch (InstantiationException e) {
                    LOGGER.log(isOptional(item.annotation()) ? Level.FINE : Level.WARNING,
                               "Failed to load "+item.className(), e);
                }
            }
            applicationContext.refresh();

        }

        private <S> FactoryBean<S> createProvider(final Class<S> type, final IndexItem<T,Object> item) {
            return new FactoryBean<S>() {

                public S getObject() throws Exception {
                    return (S) instantiate(item);
                }

                public Class<?> getObjectType() {
                    return type;
                }

                public boolean isSingleton() {
                    return false;
                }
            };
        }

        protected abstract boolean isOptional(T annotation);

        private Object instantiate(IndexItem<T,Object> item) {
            try {
                return item.instance();
            } catch (LinkageError e) {
                // sometimes the instantiation fails in an indirect classloading failure,
                // which results in a LinkageError
                LOGGER.log(isOptional(item.annotation()) ? Level.FINE : Level.WARNING,
                           "Failed to load "+item.className(), e);
            } catch (InstantiationException e) {
                LOGGER.log(isOptional(item.annotation()) ? Level.FINE : Level.WARNING,
                           "Failed to load "+item.className(), e);
            }
            return null;
        }

        public <U> Collection<ExtensionComponent<U>> find(Class<U> type, Hudson hudson) {
            List<ExtensionComponent<U>> result = new ArrayList<ExtensionComponent<U>>();

            Map<String, U> beans = applicationContext.getBeansOfType(type);
            for (Map.Entry<String, U> bean: beans.entrySet()) {
                Object o = bean.getValue();
                if (o!=null)
                    result.add(new ExtensionComponent<U>(type.cast(o),0));
            }

            return result;
        }



        /**
         * Hook to enable subtypes to control which ones to pick up and which ones to ignore.
         */
        protected boolean isActive(AnnotatedElement e) {
            return true;
        }
    }

    /**
     * The default implementation that looks for the {@link Extension} marker.
     *
     * <p>
     * Uses Sezpoz as the underlying mechanism.
     */
    public static final class Sezpoz extends ExtensionFinder {
        public <T> Collection<ExtensionComponent<T>> find(Class<T> type, Hudson hudson) {
            List<ExtensionComponent<T>> result = new ArrayList<ExtensionComponent<T>>();

            ClassLoader cl = hudson.getPluginManager().uberClassLoader;
            for (IndexItem<Extension,Object> item : Index.load(Extension.class, Object.class, cl)) {
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
                            result.add(new ExtensionComponent<T>(type.cast(instance),item.annotation()));
                    }
                } catch (LinkageError e) {
                    // sometimes the instantiation fails in an indirect classloading failure,
                    // which results in a LinkageError
                    LOGGER.log(item.annotation().optional() ? Level.FINE : Level.WARNING,
                               "Failed to load "+item.className(), e);
                } catch (InstantiationException e) {
                    LOGGER.log(item.annotation().optional() ? Level.FINE : Level.WARNING,
                               "Failed to load "+item.className(), e);
                }
            }

            return result;
        }

        @Override
        public void scout(Class extensionType, Hudson hudson) {
            ClassLoader cl = hudson.getPluginManager().uberClassLoader;
            for (IndexItem<Extension,Object> item : Index.load(Extension.class, Object.class, cl)) {
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
                    // accroding to http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6459208
                    // this appears to be the only way to force a class initialization
                    Class.forName(extType.getName(),true,extType.getClassLoader());
                } catch (InstantiationException e) {
                    LOGGER.log(item.annotation().optional() ? Level.FINE : Level.WARNING,
                               "Failed to scout "+item.className(), e);
                } catch (ClassNotFoundException e) {
                    LOGGER.log(Level.WARNING,"Failed to scout "+item.className(), e);
                } catch (LinkageError e) {
                    LOGGER.log(Level.WARNING,"Failed to scout "+item.className(), e);
                }
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ExtensionFinder.class.getName());
}
