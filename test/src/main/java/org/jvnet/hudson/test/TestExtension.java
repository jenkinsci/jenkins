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
import net.java.sezpoz.Indexable;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Works like {@link Extension} except used for inserting extensions during unit tests.
 *
 * <p>
 * This annotation must be used on a method/field of a test case class, or an nested type of the test case.
 * The extensions are activated only when the outer test class is being run.
 *
 * @author Kohsuke Kawaguchi
 * @see TestExtensionLoader
 */
@Indexable
@Retention(RUNTIME)
@Target({TYPE, FIELD, METHOD})
@Documented
public @interface TestExtension {
    /**
     * To make this extension only active for one test case, specify the test method name.
     * Otherwise, leave it unspecified and it'll apply to all the test methods defined in the same class.
     *
     * <h2>Example</h2>
     * <pre>
     * class FooTest extends HudsonTestCase {
     *     public void test1() { ... }
     *     public void test2() { ... }
     *
     *     // this only kicks in during test1
     *     &#64;TestExtension("test1")
     *     class Foo extends ConsoleAnnotator { ... }
     *
     *     // this kicks in both for test1 and test2
     *     &#64;TestExtension
     *     class Bar extends ConsoleAnnotator { ... }
     * }
     * </pre>
     */
    String value() default "";
}
