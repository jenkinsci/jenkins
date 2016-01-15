/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package org.jvnet.hudson.test.recipes;

import org.jvnet.hudson.test.HudsonTestCase;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.lang.annotation.Annotation;
import java.io.File;

import junit.framework.TestCase;
import org.jvnet.hudson.test.JenkinsRecipe;


/**
 * Meta-annotation for recipe annotations, which controls
 * the test environment set up.
 *
 * This is JUnit3 version of {@link JenkinsRecipe}
 * 
 * @author Kohsuke Kawaguchi
 */
@Retention(RUNTIME)
@Documented
@Target(ANNOTATION_TYPE)
public @interface Recipe {
    /**
     * Specifies the class that sets up the test environment.
     *
     * <p>
     * When a recipe annotation is placed on a test method, 
     */
    Class<? extends Runner> value();

    /**
     * The code that implements the recipe semantics.
     *
     * @param <T>
     *      The recipe annotation associated with this runner.
     */
    abstract class Runner<T extends Annotation> {
        /**
         * Called during {@link TestCase#setUp()} to prepare the test environment.
         */
        public void setup(HudsonTestCase testCase, T recipe) throws Exception {}

        /**
         * Called right before {@link jenkins.model.Jenkins#Hudson(File, ServletContext)} is invoked
         * to decorate the hudson home directory.
         */
        public void decorateHome(HudsonTestCase testCase, File home) throws Exception {}

        /**
         * Called during {@link TestCase#tearDown()} to shut down the test environment.
         */
        public void tearDown(HudsonTestCase testCase, T recipe) throws Exception {}
    }
}
