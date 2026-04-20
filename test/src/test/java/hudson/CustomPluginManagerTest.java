/*
 * The MIT License
 *
 * Copyright (c) 2016 CloudBees, Inc.
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

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.ServletContext;
import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRecipe;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;
import org.jvnet.hudson.test.recipes.WithPlugin;

/**
 * Tests for the use of a custom plugin manager in custom wars.
 */
class CustomPluginManagerTest {

    @RegisterExtension
    private final JenkinsSessionExtension session = new JenkinsSessionExtension();

    // TODO: Move to jenkins-test-harness
    @JenkinsRecipe(WithCustomLocalPluginManager.RuleRunnerImpl.class)
    @Target(METHOD)
    @Retention(RUNTIME)
    public @interface WithCustomLocalPluginManager {
        Class<? extends LocalPluginManager> value();

        class RuleRunnerImpl extends JenkinsRecipe.Runner<WithCustomLocalPluginManager> {
            private String oldValue;

            @Override
            public void setup(JenkinsRule jenkinsRule, WithCustomLocalPluginManager recipe) {
                jenkinsRule.useLocalPluginManager = true;
                oldValue = System.getProperty(PluginManager.CUSTOM_PLUGIN_MANAGER);
                System.setProperty(PluginManager.CUSTOM_PLUGIN_MANAGER, recipe.value().getName());
            }

            @Override
            public void tearDown(JenkinsRule jenkinsRule, WithCustomLocalPluginManager recipe) {
                if (oldValue != null) {
                    System.setProperty(PluginManager.CUSTOM_PLUGIN_MANAGER, oldValue);
                } else {
                    System.clearProperty(PluginManager.CUSTOM_PLUGIN_MANAGER);
                }
            }
        }
    }

    private void check(Class<? extends CustomPluginManager> klass) throws Throwable {
        session.then(r -> {
            assertTrue(klass.isAssignableFrom(r.getPluginManager().getClass()), "Correct plugin manager installed");
            assertNotNull(r.jenkins.getPlugin("htmlpublisher"), "Plugin 'htmlpublisher' installed");
        });
    }

    // An interface not to override every constructor.
    interface CustomPluginManager {
    }

    @Issue("JENKINS-34681")
    @WithPlugin("htmlpublisher.jpi")
    @WithCustomLocalPluginManager(CustomPluginManager1.class)
    @Test
    void customPluginManager1() throws Throwable {
        check(CustomPluginManager1.class);
    }

    public static class CustomPluginManager1 extends LocalPluginManager implements CustomPluginManager {
        @SuppressWarnings("checkstyle:redundantmodifier")
        public CustomPluginManager1(Jenkins jenkins) {
            super(jenkins);
        }
    }

    @Issue("JENKINS-34681")
    @WithPlugin("htmlpublisher.jpi")
    @WithCustomLocalPluginManager(CustomPluginManager2.class)
    @Test
    void customPluginManager2() throws Throwable {
        check(CustomPluginManager2.class);
    }

    public static class CustomPluginManager2 extends LocalPluginManager implements CustomPluginManager {
        @SuppressWarnings("checkstyle:redundantmodifier")
        public CustomPluginManager2(ServletContext ctx, File root) {
            super(ctx, root);
        }
    }

    @Issue("JENKINS-34681")
    @WithPlugin("htmlpublisher.jpi")
    @WithCustomLocalPluginManager(CustomPluginManager3.class)
    @Test
    void customPluginManager3() throws Throwable {
        check(CustomPluginManager3.class);
    }

    public static class CustomPluginManager3 extends LocalPluginManager implements CustomPluginManager {
        @SuppressWarnings("checkstyle:redundantmodifier")
        public CustomPluginManager3(File root) {
            super(root);
        }
    }

    @Issue("JENKINS-34681")
    @WithPlugin("htmlpublisher.jpi")
    @WithCustomLocalPluginManager(BadCustomPluginManager.class)
    @Test
    void badCustomPluginManager() throws Throwable {
        session.then(r ->
            assertThat("Custom plugin manager not installed", r.getPluginManager(), not(instanceOf(CustomPluginManager.class)))
        );
    }

    public static class BadCustomPluginManager extends LocalPluginManager implements CustomPluginManager {
        @SuppressWarnings("checkstyle:redundantmodifier")
        public BadCustomPluginManager(File root, ServletContext ctx) {
            super(ctx, root);
        }
    }

}
