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

import net.java.sezpoz.Index;
import net.java.sezpoz.IndexItem;
import hudson.model.Hudson;
import hudson.model.Descriptor;

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
 * Hudson discovers {@link ExtensionFinder}s through {@link Sezpoz} and that alone.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.286
 */
public abstract class ExtensionFinder implements ExtensionPoint {
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
     */
    public abstract <T> Collection<T> findExtensions(Class<T> type, Hudson hudson);

    /**
     * The default implementation that looks for the {@link Extension} marker.
     *
     * <p>
     * Uses Sezpoz as the underlying mechanism.
     */
    @Extension
    public static final class Sezpoz extends ExtensionFinder {
        public <T> Collection<T> findExtensions(Class<T> type, Hudson hudson) {
            List<T> result = new ArrayList<T>();

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
                            result.add(type.cast(instance));
                    }
                } catch (InstantiationException e) {
                    LOGGER.log(Level.WARNING, "Failed to load "+item.className(),e);
                }
            }

            return result;
        }
    }
    
    private static final Logger LOGGER = Logger.getLogger(ExtensionFinder.class.getName());
}
