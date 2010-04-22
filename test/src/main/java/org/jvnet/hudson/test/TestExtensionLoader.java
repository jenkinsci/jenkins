/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
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
package org.jvnet.hudson.test;

import hudson.Extension;
import hudson.ExtensionComponent;
import hudson.ExtensionFinder;
import hudson.model.Hudson;
import net.java.sezpoz.Index;
import net.java.sezpoz.IndexItem;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads {@link TestExtension}s.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class TestExtensionLoader extends ExtensionFinder {
    @Override
    public <T> Collection<ExtensionComponent<T>> find(Class<T> type, Hudson hudson) {
        TestEnvironment env = TestEnvironment.get();

        List<ExtensionComponent<T>> result = new ArrayList<ExtensionComponent<T>>();

        ClassLoader cl = hudson.getPluginManager().uberClassLoader;
        for (IndexItem<TestExtension,Object> item : Index.load(TestExtension.class, Object.class, cl)) {
            try {
                AnnotatedElement e = item.element();
                Class<?> extType;
                if (e instanceof Class) {
                    extType = (Class) e;
                    if (!isActive(env, extType)) continue;
                } else
                if (e instanceof Field) {
                    Field f = (Field) e;
                    if (!f.getDeclaringClass().isInstance(env.testCase))
                        continue;      // not executing the enclosing test
                    extType = f.getType();
                } else
                if (e instanceof Method) {
                    Method m = (Method) e;
                    if (!m.getDeclaringClass().isInstance(env.testCase))
                        continue;      // not executing the enclosing test
                    extType = m.getReturnType();
                } else
                    throw new AssertionError();

                String testName = item.annotation().value();
                if (testName.length()>0 && !env.testCase.getName().equals(testName))
                    continue;   // doesn't apply to this test

                if(type.isAssignableFrom(extType)) {
                    Object instance = item.instance();
                    if(instance!=null)
                        result.add(new ExtensionComponent<T>(type.cast(instance)));
                }
            } catch (InstantiationException e) {
                LOGGER.log(Level.WARNING, "Failed to load "+item.className(),e);
            }
        }

        return result;
    }

    private boolean isActive(TestEnvironment env, Class<?> extType) {
        for (Class<?> outer = extType; outer!=null; outer=outer.getEnclosingClass())
            if (outer.isInstance(env.testCase))
                return true;      // enclosed
        return false;
    }

    private static final Logger LOGGER = Logger.getLogger(TestExtensionLoader.class.getName());
}
