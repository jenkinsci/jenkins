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

import org.junit.runner.Description;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.HudsonHomeLoader.Local;
import org.jvnet.hudson.test.JenkinsRecipe;


import java.lang.annotation.Documented;
import java.lang.annotation.Target;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.annotation.ElementType.METHOD;

/**
 * Runs a test case with a data set local to test method or the test class.
 *
 * <p>
 * This recipe allows your test case to start with the preset <tt>JENKINS_HOME</tt> data loaded
 * either from your test method or from the test class.
 *
 * <p>
 * For example, if the test method if <tt>org.acme.FooTest.testBar()</tt>, then
 * you can have your test data in one of the following places in resources folder
 * (typically <tt>src/test/resources</tt>):
 *
 * <ol>
 * <li>
 * Under <tt>org/acme/FooTest/testBar</tt> directory (that is, you'll have
 * <tt>org/acme/FooTest/testBar/config.xml</tt>), in the same layout as in the real <tt>JENKINS_HOME</tt> directory.
 * <li>
 * In <tt>org/acme/FooTest/testBar.zip</tt> as a zip file.
 * <li>
 * Under <tt>org/acme/FooTest</tt> directory (that is, you'll have
 * <tt>org/acme/FooTest/config.xml</tt>), in the same layout as in the real <tt>JENKINS_HOME</tt> directory.
 * <li>
 * In <tt>org/acme/FooTest.zip</tt> as a zip file.
 * </ol>
 *
 * <p>
 * Search is performed in this specific order. The fall back mechanism allows you to write
 * one test class that interacts with different aspects of the same data set, by associating
 * the dataset with a test class, or have a data set local to a specific test method.
 *
 * <p>
 * The choice of zip and directory depends on the nature of the test data, as well as the size of it.
 *
 * @author Kohsuke Kawaguchi
 */
@Documented
@Recipe(LocalData.RunnerImpl.class)
@JenkinsRecipe(LocalData.RuleRunnerImpl.class)
@Target(METHOD)
@Retention(RUNTIME)
public @interface LocalData {
    class RunnerImpl extends Recipe.Runner<LocalData> {
        public void setup(HudsonTestCase testCase, LocalData recipe) throws Exception {
            testCase.with(new Local(testCase.getClass().getMethod(testCase.getName())));
        }
    }
    class RuleRunnerImpl extends JenkinsRecipe.Runner<LocalData> {
        public void setup(JenkinsRule jenkinsRule, LocalData recipe) throws Exception {
            Description desc = jenkinsRule.getTestDescription();
            jenkinsRule.with(new Local(desc.getTestClass().getMethod(desc.getMethodName())));
        }
    }
}
