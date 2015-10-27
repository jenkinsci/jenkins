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
import org.apache.commons.io.FileUtils;

import hudson.util.JenkinsReloadFailed;

import java.io.File;
import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.net.URL;

/**
 * Installs the specified plugins before launching Jenkins.
 *
 * @author Kohsuke Kawaguchi
 */
@Documented
@Recipe(WithPlugin.RunnerImpl.class)
@JenkinsRecipe(WithPlugin.RuleRunnerImpl.class)
@Target(METHOD)
@Retention(RUNTIME)
public @interface WithPlugin {
    /**
     * Comma separated list of plugin names.
     *
     * For now, this has to be one or more of the plugins statically available in resources
     * "/plugins/NAME". TODO: support retrieval through Maven repository.
     * TODO: load the HPI file from $M2_REPO or $USER_HOME/.m2 by naming e.g. org.jvnet.hudson.plugins:monitoring:hpi:1.34.0
     * (used in conjunction with the depepdency in POM to ensure it's available)
     */
    String value();

    class RunnerImpl extends Recipe.Runner<WithPlugin> {
        private WithPlugin a;

        @Override
        public void setup(HudsonTestCase testCase, WithPlugin recipe) throws Exception {
            a = recipe;
            testCase.useLocalPluginManager = true;
        }

        @Override
        public void decorateHome(HudsonTestCase testCase, File home) throws Exception {
            for (String plugin : a.value().split("\\s*,\\s*")) {
                URL res = getClass().getClassLoader().getResource("plugins/" + plugin);
                FileUtils.copyURLToFile(res, new File(home, "plugins/" + plugin));
            }
        }
    }

    class RuleRunnerImpl extends JenkinsRecipe.Runner<WithPlugin> {
        private WithPlugin a;

        @Override
        public void setup(JenkinsRule jenkinsRule, WithPlugin recipe) throws Exception {
            a = recipe;
            jenkinsRule.useLocalPluginManager = true;
        }

        @Override
        public void decorateHome(JenkinsRule jenkinsRule, File home) throws Exception {
            for (String plugin : a.value().split("\\s*,\\s*")) {
                URL res = getClass().getClassLoader().getResource("plugins/" + plugin);
                FileUtils.copyURLToFile(res, new File(home, "plugins/" + plugin));
            }
        }
    }
}
