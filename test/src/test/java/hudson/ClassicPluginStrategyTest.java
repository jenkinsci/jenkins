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
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
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
    @Issue("JENKINS-18654")
    @Test
    void testDisabledDependencyClassLoader() throws Throwable {
        File plugins = new File(session.getHome(), "plugins");
        Files.createDirectories(plugins.toPath());
        // foo4 has an optional dependency on foo5, which is disabled: foo4 must still load, but must
        // not see foo5's resources through its DependencyClassLoader.
        writeSyntheticPlugin(new File(plugins, "foo5.jpi"), "foo5", "0.5", null, "test-resource for FOO5\n");
        assertTrue(new File(plugins, "foo5.jpi.disabled").createNewFile());
        writeSyntheticPlugin(new File(plugins, "foo4.jpi"), "foo4", "0.4", "foo5:0.5;resolution:=optional", "test-resource for FOO4\n");

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
     * Builds a minimal synthetic plugin jar directly into a JenkinsRule home's {@code plugins}
     * directory, so tests don't need to check in binary {@code .hpi}/{@code .jpi} fixtures.
     */
    private static void writeSyntheticPlugin(File dest, String shortName, String version, String pluginDependencies, String testResourceContent) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attr = manifest.getMainAttributes();
        attr.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attr.putValue("Short-Name", shortName);
        attr.putValue("Long-Name", shortName);
        attr.putValue("Plugin-Version", version);
        attr.putValue("Hudson-Version", "1.450");
        if (pluginDependencies != null) {
            attr.putValue("Plugin-Dependencies", pluginDependencies);
        }
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(dest), manifest)) {
            if (testResourceContent != null) {
                jos.putNextEntry(new JarEntry("WEB-INF/classes/test-resource"));
                jos.write(testResourceContent.getBytes(StandardCharsets.UTF_8));
                jos.closeEntry();
            }
        }
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
