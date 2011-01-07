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
import hudson.ExtensionFinder;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Loads {@link TestExtension}s.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class TestExtensionLoader extends ExtensionFinder.AbstractSpringExtensionFinder<TestExtension> {
    public TestExtensionLoader() {
        super(TestExtension.class);
    }

    @Override
    protected boolean isOptional(TestExtension annotation) {
        return false;
    }

    @Override
    protected boolean isActive(AnnotatedElement e) {
        TestEnvironment env = TestEnvironment.get();

        if (e instanceof Class) {
            return isActive(env, (Class)e);
        }
        if (e instanceof Field) {
            Field f = (Field) e;
            return f.getDeclaringClass().isInstance(env.testCase);
        }
        if (e instanceof Method) {
            Method m = (Method) e;
            return m.getDeclaringClass().isInstance(env.testCase);
        }
        return false;
    }

    private boolean isActive(TestEnvironment env, Class<?> extType) {
        for (Class<?> outer = extType; outer!=null; outer=outer.getEnclosingClass())
            if (outer.isInstance(env.testCase))
                return true;      // enclosed
        return false;
    }
}
