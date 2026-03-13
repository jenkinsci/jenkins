/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

package jenkins.install;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.ClassicPluginStrategy;
import hudson.ExtensionList;
import hudson.PluginManager;
import hudson.PluginManagerUtil;
import hudson.PluginWrapper;
import hudson.util.VersionNumber;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import jenkins.plugins.DetachedPluginsUtil;
import jenkins.plugins.DetachedPluginsUtil.DetachedPlugin;
import jenkins.security.UpdateSiteWarningsMonitor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;
import org.jvnet.hudson.test.recipes.LocalData;

@Tag("SmokeTest")
class LoadDetachedPluginsTest {

    @RegisterExtension
    private final JenkinsSessionExtension rr = PluginManagerUtil.newJenkinsSessionExtension();

    private final LogRecorder logging = new LogRecorder();

    @Issue("JENKINS-48365")
    @Test
    @LocalData
    void upgradeFromJenkins1() throws Throwable {
        VersionNumber since = new VersionNumber("1.490");
        rr.then(r -> {
            List<DetachedPlugin> detachedPlugins = DetachedPluginsUtil.getDetachedPlugins(since);
            assertThat("Plugins have been detached since the pre-upgrade version",
                    detachedPlugins.size(), greaterThan(4));
            assertThat("Plugins detached between the pre-upgrade version and the current version should be installed",
                    getInstalledDetachedPlugins(r, detachedPlugins).size(), equalTo(detachedPlugins.size()));
            assertNoFailedPlugins(r);
        });
    }

    @Test
    @Disabled("Only useful while updating bundled plugins, otherwise new security warnings fail unrelated builds")
    @LocalData
    void noUpdateSiteWarnings() throws Throwable {
        rr.then(r -> {
            r.jenkins.getUpdateCenter().updateAllSites();
            final UpdateSiteWarningsMonitor monitor = ExtensionList.lookupSingleton(UpdateSiteWarningsMonitor.class);
            assertThat("There should be no active plugin security warnings", monitor.getActivePluginWarningsByPlugin().keySet(), empty());
        });
    }

    @Issue("JENKINS-48365")
    @Test
    @LocalData
    void upgradeFromJenkins2() throws Throwable {
        VersionNumber since = new VersionNumber("2.0");
        rr.then(r -> {
            List<DetachedPlugin> detachedPlugins = DetachedPluginsUtil.getDetachedPlugins(since);
            assertThat("Plugins have been detached since the pre-upgrade version",
                    detachedPlugins.size(), greaterThan(1));
            assertThat("Plugins detached between the pre-upgrade version and the current version should be installed",
                    getInstalledDetachedPlugins(r, detachedPlugins).size(), equalTo(detachedPlugins.size()));
            assertNoFailedPlugins(r);
        });
    }

    @Test
    void newInstallation() throws Throwable {
        rr.then(r -> {
            List<DetachedPlugin> detachedPlugins = DetachedPluginsUtil.getDetachedPlugins();
            assertThat("Detached plugins should exist", detachedPlugins, not(empty()));
            assertThat("Detached plugins should not be installed on a new instance",
                    getInstalledDetachedPlugins(r, detachedPlugins), empty());
            assertNoFailedPlugins(r);
        });
        rr.then(r -> {
            List<DetachedPlugin> detachedPlugins = DetachedPluginsUtil.getDetachedPlugins();
            assertThat("Detached plugins should exist", detachedPlugins, not(empty()));
            assertThat("Detached plugins should not be installed after restarting",
                    getInstalledDetachedPlugins(r, detachedPlugins), empty());
            assertNoFailedPlugins(r);
        });
    }

    @Issue("JENKINS-55582")
    @LocalData
    @Test
    void installDetachedDependencies() throws Throwable {
        logging.record(PluginManager.class, Level.FINE).record(ClassicPluginStrategy.class, Level.FINE);
        rr.then(r -> {
            List<String> activePlugins = r.jenkins.getPluginManager().getPlugins().stream().filter(PluginWrapper::isActive).map(PluginWrapper::getShortName).collect(Collectors.toList());
            assertThat("we precreated $JENKINS_HOME/plugins/example.jpi so it had better be loaded", activePlugins, hasItem("example"));
            { // Check that it links correctly against an implied dependency from a detached plugin:
                Class<?> callerC = r.jenkins.pluginManager.uberClassLoader.loadClass("io.jenkins.plugins.example.Caller");
                assertLoader(callerC, "example", r);
                Object jdkInstaller = callerC.getMethod("use").invoke(null);
                assertLoader(jdkInstaller.getClass(), "jdk-tool", r);
            }
            assertThat("it had various implicit detached dependencies so those should have been loaded too", activePlugins, hasSize(greaterThan(1)));
        });
    }

    private void assertLoader(Class<?> c, String expectedPlugin, JenkinsRule r) {
        PluginWrapper pw = r.jenkins.pluginManager.whichPlugin(c);
        assertNotNull(pw, "did not expect to be loading " + c + " from " + c.getClassLoader());
        assertEquals(expectedPlugin, pw.getShortName());
    }

    @Issue("JENKINS-55582")
    @LocalData
    @Test
    void nonstandardFilenames() throws Throwable {
        logging.record(PluginManager.class, Level.FINE).record(ClassicPluginStrategy.class, Level.FINE);
        rr.then(r -> {
            assertTrue(r.jenkins.pluginManager.getPlugin("build-token-root").isActive());
            assertEquals("1.2", r.jenkins.pluginManager.getPlugin("jdk-tool").getVersion());
            /* TODO currently still loads the detached 1.0, since we only skip $shortName.[jh]pi not $shortName-$version.[jh]pi; during PLUGINS_LISTED there is a list of known filenames but not short names
            assertEquals("1.3", r.jenkins.pluginManager.getPlugin("command-launcher").getVersion());
            */
        });
    }

    private List<PluginWrapper> getInstalledDetachedPlugins(JenkinsRule r, List<DetachedPlugin> detachedPlugins) {
        PluginManager pluginManager = r.jenkins.getPluginManager();
        List<PluginWrapper> installedPlugins = new ArrayList<>();
        for (DetachedPlugin plugin : detachedPlugins) {
            PluginWrapper wrapper = pluginManager.getPlugin(plugin.getShortName());
            if (wrapper != null) {
                installedPlugins.add(wrapper);
                assertTrue(wrapper.isActive(), "Detached plugins should be active if installed");
                assertThat("Detached plugins should not have dependency errors", wrapper.getDependencyErrors(), empty());
            }
        }
        return installedPlugins;
    }

    private void assertNoFailedPlugins(JenkinsRule r) {
        assertThat("Detached plugins and their dependencies should not fail to install",
                r.jenkins.getPluginManager().getFailedPlugins(), empty());
    }

}
