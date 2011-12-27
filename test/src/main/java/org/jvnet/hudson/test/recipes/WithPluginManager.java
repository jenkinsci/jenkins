/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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

import hudson.PluginManager;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.JenkinsRecipe;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * Runs the test case with a custom plugin manager.
 *
 * @author Kohsuke Kawaguchi
 */
@Documented
@Recipe(WithPluginManager.RunnerImpl.class)
@JenkinsRecipe(WithPluginManager.RuleRunnerImpl.class)
@Target(METHOD)
@Retention(RUNTIME)
public @interface WithPluginManager {
    Class<? extends PluginManager> value();

    class RunnerImpl extends Recipe.Runner<WithPluginManager> {
        private WithPluginManager recipe;
        @Override
        public void setup(HudsonTestCase testCase, WithPluginManager recipe) throws Exception {
            this.recipe = recipe;
        }

        @Override
        public void decorateHome(HudsonTestCase testCase, File home) throws Exception {
            Class<? extends PluginManager> c = recipe.value();
            Constructor ctr = c.getConstructors()[0];

            // figure out parameters
            Class[] pt = ctr.getParameterTypes();
            Object[] args = new Object[pt.length];
            for (int i=0; i<args.length; i++) {
                Class t = pt[i];
                if (t==File.class) {
                    args[i] = home;
                    continue;
                }
                if (t.isAssignableFrom(testCase.getClass())) {
                    args[i] = testCase;
                    continue;
                }
            }

            testCase.setPluginManager((PluginManager)ctr.newInstance(args));
        }
    }

    class RuleRunnerImpl extends JenkinsRecipe.Runner<WithPluginManager> {
        private WithPluginManager recipe;
        @Override
        public void setup(JenkinsRule jenkinsRule, WithPluginManager recipe) throws Exception {
            this.recipe = recipe;
        }

        @Override
        public void decorateHome(JenkinsRule jenkinsRule, File home) throws Exception {
            Class<? extends PluginManager> c = recipe.value();
            Constructor ctr = c.getDeclaredConstructor(File.class);
            jenkinsRule.setPluginManager((PluginManager)ctr.newInstance(home));
        }
    }
}
