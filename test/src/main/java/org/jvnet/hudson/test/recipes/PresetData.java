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
import org.jvnet.hudson.test.JenkinsRecipe;
import org.jvnet.hudson.test.JenkinsRule;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.util.Locale;

/**
 * Runs a test case with one of the preset JENKINS_HOME data set.
 *
 * @author Kohsuke Kawaguchi
 * @see LocalData
 */
@Documented
@Recipe(PresetData.RunnerImpl.class)
@JenkinsRecipe(PresetData.RuleRunnerImpl.class)
@Target(METHOD)
@Retention(RUNTIME)
public @interface PresetData {
    /**
     * One of the preset data to choose from.
     */
    DataSet value();

    enum DataSet {
        /**
         * Secured Hudson that has no anonymous read access.
         * Any logged in user can do anything.
         */
        NO_ANONYMOUS_READACCESS,
        /**
         * Secured Hudson where anonymous user is read-only,
         * and any logged in user has a full access.
         */
        ANONYMOUS_READONLY,

        SECURED_ACEGI,
    }

    class RunnerImpl extends Recipe.Runner<PresetData> {
        public void setup(HudsonTestCase testCase, PresetData recipe) {
            testCase.withPresetData(recipe.value().name().toLowerCase(Locale.ENGLISH).replace('_','-'));
        }
    }
    class RuleRunnerImpl extends JenkinsRecipe.Runner<PresetData> {
        public void setup(JenkinsRule jenkinsRule, PresetData recipe) {
            jenkinsRule.withPresetData(recipe.value().name().toLowerCase(Locale.ENGLISH).replace('_','-'));
        }
    }
}
