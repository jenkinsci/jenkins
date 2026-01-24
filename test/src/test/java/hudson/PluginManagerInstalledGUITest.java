/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlElementUtil;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlTableRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestPluginManager;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;
import org.xml.sax.SAXException;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
class PluginManagerInstalledGUITest {

    @RegisterExtension
    private final JenkinsSessionExtension session = new CustomPluginManagerExtension();

    @Issue("JENKINS-33843")
    @Test
    void test_enable_disable_uninstall() throws Throwable {
        session.then(j -> {
            InstalledPlugins installedPlugins = new InstalledPlugins(j);

            InstalledPlugin matrixAuthPlugin = installedPlugins.get("matrix-auth");
            InstalledPlugin dependeePlugin = installedPlugins.get("dependee");
            InstalledPlugin dependerPlugin = installedPlugins.get("depender");
            InstalledPlugin mandatoryDependerPlugin = installedPlugins.get("mandatory-depender");

            // As a detached plugin, it is an optional dependency of others built against a newer baseline.
            matrixAuthPlugin.assertHasNoDependents();
            // Has a mandatory dependency:
            dependeePlugin.assertHasDependents();
            // Leaf plugins:
            dependerPlugin.assertHasNoDependents();
            mandatoryDependerPlugin.assertHasNoDependents();

            // This plugin should be enabled and it should be possible to disable it
            // because no other plugins depend on it.
            mandatoryDependerPlugin.assertEnabled();
            mandatoryDependerPlugin.assertEnabledStateChangeable();
            mandatoryDependerPlugin.assertUninstallable();

            // This plugin should be enabled, but it should not be possible to disable or uninstall it
            // because another plugin depends on it.
            dependeePlugin.assertEnabled();
            dependeePlugin.assertEnabledStateNotChangeable();
            dependeePlugin.assertNotUninstallable();

            // Disable one plugin
            mandatoryDependerPlugin.clickEnabledWidget();

            // Now that plugin should be disabled, but it should be possible to re-enable it
            // and it should still be uninstallable.
            mandatoryDependerPlugin.assertNotEnabled(); // this is different to earlier
            mandatoryDependerPlugin.assertEnabledStateChangeable();
            mandatoryDependerPlugin.assertUninstallable();

            // The dependee plugin should still be enabled, but it should now be possible to disable it because
            // the mandatory depender plugin is no longer enabled. Should still not be possible to uninstall it.
            // Note that the depender plugin does not block its disablement.
            dependeePlugin.assertEnabled();
            dependeePlugin.assertEnabledStateChangeable(); // this is different to earlier
            dependeePlugin.assertNotUninstallable();
            dependerPlugin.assertEnabled();

            // Disable the dependee plugin
            dependeePlugin.clickEnabledWidget();

            // Now it should NOT be possible to change the enable state of the depender plugin because one
            // of the plugins it depends on is not enabled.
            mandatoryDependerPlugin.assertNotEnabled();
            mandatoryDependerPlugin.assertEnabledStateNotChangeable();  // this is different to earlier
            mandatoryDependerPlugin.assertUninstallable();
            dependerPlugin.assertEnabled();

            // You can disable a detached plugin if there is no explicit dependency on it.
            matrixAuthPlugin.assertEnabled();
            matrixAuthPlugin.assertEnabledStateChangeable();
            matrixAuthPlugin.assertUninstallable();
            matrixAuthPlugin.clickEnabledWidget();
            matrixAuthPlugin.assertNotEnabled();
            matrixAuthPlugin.assertEnabledStateChangeable();
            matrixAuthPlugin.assertUninstallable();
        });
    }

    private static class InstalledPlugins {

        private final List<InstalledPlugin> installedPlugins;

        private InstalledPlugins(JenkinsRule jenkinsRule) throws IOException, SAXException {
            JenkinsRule.WebClient webClient = jenkinsRule.createWebClient();
            HtmlPage installedPage = webClient.goTo("pluginManager/installed");
            final boolean healthScoresAvailable = jenkinsRule.jenkins.getUpdateCenter().isHealthScoresAvailable();

            // Note for debugging... simply print installedPage to get the JenkinsRule
            // Jenkins URL and then add a long Thread.sleep here. It's useful re being
            // able to see what the code is testing.

            DomElement pluginsTable = installedPage.getElementById("plugins");
            HtmlElement tbody = pluginsTable.getElementsByTagName("TBODY").getFirst();

            installedPlugins = new ArrayList<>();
            for (DomElement htmlTableRow : tbody.getChildElements()) {
                installedPlugins.add(new InstalledPlugin((HtmlTableRow) htmlTableRow, healthScoresAvailable));
            }
        }

        public InstalledPlugin get(String pluginId) {
            for (InstalledPlugin plugin : installedPlugins) {
                if (plugin.isPlugin(pluginId)) {
                    return plugin;
                }
            }
            fail("No pluginManager/installed row for plugin " + pluginId);
            return null;
        }

    }

    private static class InstalledPlugin {

        private final HtmlTableRow pluginRow;
        private final boolean hasHealth;

        InstalledPlugin(HtmlTableRow pluginRow, boolean hasHealth) {
            this.pluginRow = pluginRow;
            this.hasHealth = hasHealth;
        }

        public String getId() {
            return pluginRow.getAttribute("data-plugin-id");
        }

        public boolean isPlugin(String pluginId) {
            return pluginId.equals(getId());
        }

        private HtmlInput getEnableWidget() {
            HtmlElement input = pluginRow.getCells().get(hasHealth ? 2 : 1).getElementsByTagName("input").getFirst();
            return (HtmlInput) input;
        }

        public void assertEnabled() {
            HtmlInput enableWidget = getEnableWidget();
            assertTrue(enableWidget.isChecked(), "Plugin '" + getId() + "' is expected to be enabled.");
        }

        public void assertNotEnabled() {
            HtmlInput enableWidget = getEnableWidget();
            assertFalse(enableWidget.isChecked(), "Plugin '" + getId() + "' is not expected to be enabled.");
        }

        public void clickEnabledWidget() throws IOException {
            HtmlInput enableWidget = getEnableWidget();
            HtmlElementUtil.click(enableWidget);
        }

        public void assertEnabledStateChangeable() {
            if (!hasDependents() && !hasDisabledDependency() && !allDependentsDisabled()) {
                return;
            }
            if (allDependentsDisabled() && !hasDisabledDependency()) {
                return;
            }

            fail("The enable/disable state of plugin '" + getId() + "' cannot be changed.");
        }

        public void assertEnabledStateNotChangeable() {
            if (hasDependents() && !hasDisabledDependency() && !allDependentsDisabled()) {
                return;
            }
            if (!hasDependents() && hasDisabledDependency()) {
                return;
            }

            fail("The enable/disable state of plugin '" + getId() + "' cannot be changed.");
        }

        public void assertUninstallable() {
            assertFalse(hasDependents(), "Plugin '" + getId() + "' cannot be uninstalled.");
        }

        public void assertNotUninstallable() {
            assertTrue(hasDependents(), "Plugin '" + getId() + "' can be uninstalled.");
        }

        public void assertHasDependents() {
            assertTrue(hasDependents(), "Plugin '" + getId() + "' is expected to have dependents.");
        }

        public void assertHasNoDependents() {
            assertFalse(hasDependents(), "Plugin '" + getId() + "' is expected to have no dependents.");
        }

        private boolean hasClassName(String className) {
            String classAttribute = pluginRow.getAttribute("class");
            Set<String> classes = new HashSet<>(Arrays.asList(classAttribute.split(" ")));
            return classes.contains(className);
        }

        private boolean hasDisabledDependency() {
            return hasClassName("has-disabled-dependency");
        }

        private boolean allDependentsDisabled() {
            return hasClassName("all-dependents-disabled");
        }

        private boolean hasDependents() {
            return hasClassName("has-dependents");
        }
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
            public PluginManager getPluginManager() {
                try {
                    return new TestPluginManager() {
                        @Override
                        protected Collection<String> loadBundledPlugins() throws Exception {
                            try {
                                return super.loadBundledPlugins();
                            } finally {
                                copyBundledPlugin(PluginManagerInstalledGUITest.class.getResource("/WEB-INF/detached-plugins/matrix-auth.hpi"), "matrix-auth.jpi"); // cannot use installDetachedPlugin at this point
                                copyBundledPlugin(PluginManagerInstalledGUITest.class.getResource("/plugins/dependee-0.0.2.hpi"), "dependee.jpi");
                                copyBundledPlugin(PluginManagerInstalledGUITest.class.getResource("/plugins/depender-0.0.2.hpi"), "depender.jpi");
                                copyBundledPlugin(PluginManagerInstalledGUITest.class.getResource("/plugins/mandatory-depender-0.0.2.hpi"), "mandatory-depender.jpi");
                            }
                        }
                    };
                } catch (IOException e) {
                    return fail(e.getMessage());
                }
            }
        }
    }
}
