/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Alan Harder
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import hudson.model.Hudson;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRecipe;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * @author Alan Harder
 */
@Tag("SmokeTest")
class ClassicPluginStrategyTest {

    @RegisterExtension
    private final JenkinsSessionExtension session = new CustomPluginManagerExtension();

    /**
     * Test finding resources via DependencyClassLoader.
     */
    @LocalData
    @Test
    void testDependencyClassLoader() throws Throwable {
        session.then(j -> {
            // Test data has: foo3 depends on foo2,foo1; foo2 depends on foo1
            // (thus findResources from foo3 can find foo1 resources via 2 dependency paths)
            PluginWrapper p = j.jenkins.getPluginManager().getPlugin("foo3");
            String res;

            // In the current impl, the dependencies are the parent ClassLoader so resources
            // are found there before checking the plugin itself.  Adjust the expected results
            // below if this is ever changed to check the plugin first.
            Enumeration<URL> en = p.classLoader.getResources("test-resource");
            for (int i = 0; en.hasMoreElements(); i++) {
                res = en.nextElement().toString();
                if (i < 2)
                    assertTrue(res.contains("/foo1/") || res.contains("/foo2/"),
                            "In current impl, " + res + "should be foo1 or foo2");
                else
                    assertTrue(res.contains("/foo3/"), "In current impl, " + res + "should be foo3");
            }
            res = p.classLoader.getResource("test-resource").toString();
            assertTrue(res.contains("/foo1/") || res.contains("/foo2/"),
                    "In current impl, " + res + " should be foo1 or foo2");
        });
    }

    /**
     * Test finding resources via DependencyClassLoader.
     * Check transitive dependency exclude disabled plugins
     */
    @LocalData
    @Issue("JENKINS-18654")
    @Test
    void testDisabledDependencyClassLoader() throws Throwable {
        session.then(j -> {
            PluginWrapper p = j.jenkins.getPluginManager().getPlugin("foo4");

            Enumeration<URL> en = p.classLoader.getResources("test-resource");
            for (int i = 0; en.hasMoreElements(); i++) {
                String res = en.nextElement().toString();
                if (i == 0)
                    assertTrue(res.contains("/foo4/"), "expected foo4, found " + res);
                else
                    fail("disabled dependency should not be included");
            }
        });
    }

    /**
     * Test finding resources under masking.
     * "foo1" plugin contains attribute of Mask-Classes: org.apache.http.
     */
    @LocalData
    @Issue("JENKINS-27289")
    @Test
    void testMaskResourceClassLoader() throws Throwable {
        session.then(j -> {
            PluginWrapper pw = j.jenkins.getPluginManager().getPlugin("foo1");
            Class<?> clazz = pw.classLoader.loadClass("org.apache.http.impl.io.SocketInputBuffer");
            ClassLoader cl = clazz.getClassLoader();
            URL url = cl.getResource("org/apache/http/impl/io/SocketInputBuffer.class");
            assertNotNull(url);
            assertTrue(url.toString().contains("plugins/foo1"), "expected to find the class from foo1 plugin");
        });
    }

    private static final class CustomPluginManagerExtension extends JenkinsSessionExtension {

        private int port;
        private org.junit.runner.Description description;

        @Override
        public void beforeEach(ExtensionContext context) {
            super.beforeEach(context);
            description = org.junit.runner.Description.createTestDescription(
                    context.getTestClass().map(Class::getName).orElse(null),
                    context.getTestMethod().map(Method::getName).orElse(null),
                    context.getTestMethod().map(Method::getAnnotations).orElse(null));
        }

        @Override
        public void then(Step s) throws Throwable {
            CustomJenkinsRule r = new CustomJenkinsRule(getHome(), port);
            r.apply(
                     new org.junit.runners.model.Statement() {
                        @Override
                        public void evaluate() throws Throwable {
                            port = r.getPort();
                            s.run(r);
                        }
                    },
                    description
            ).evaluate();
        }

        private static final class CustomJenkinsRule extends JenkinsRule {

            CustomJenkinsRule(File home, int port) {
                with(() -> home);
                localPort = port;
            }

            int getPort() {
                return localPort;
            }

            @Override
            protected Hudson newHudson() throws Exception {
                File home = homeLoader.allocate();

                for (JenkinsRecipe.Runner r : recipes) {
                    r.decorateHome(this, home);
                }
                LocalPluginManager pluginManager = new LocalPluginManager(home) {
                    @Override
                    protected Collection<String> loadBundledPlugins() {
                        // Overriding so we can force loading of the detached plugins for testing
                        Set<String> names = new LinkedHashSet<>();
                        names.addAll(loadPluginsFromWar("/WEB-INF/plugins"));
                        names.addAll(loadPluginsFromWar("/WEB-INF/detached-plugins"));
                        return names;
                    }
                };
                setPluginManager(pluginManager);
                return new Hudson(home, createWebServer2(), pluginManager);
            }
        }
    }
}
