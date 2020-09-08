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

import hudson.ClassicPluginStrategy;
import jenkins.plugins.DetachedPluginsUtil;
import jenkins.plugins.DetachedPluginsUtil.DetachedPlugin;
import hudson.PluginManager;
import hudson.PluginManagerUtil;
import hudson.PluginWrapper;
import hudson.util.VersionNumber;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.SmokeTest;
import org.jvnet.hudson.test.recipes.LocalData;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import org.jvnet.hudson.test.LoggerRule;

@Category(SmokeTest.class)
public class LoadDetachedPluginsTest {

    @Rule public RestartableJenkinsRule rr = PluginManagerUtil.newRestartableJenkinsRule();
    @Rule public LoggerRule logging = new LoggerRule();

    @Issue("JENKINS-48365")
    @Test
    @LocalData
    public void upgradeFromJenkins1() throws IOException {
        VersionNumber since = new VersionNumber("1.550");
        rr.then(r -> {
            List<DetachedPlugin> detachedPlugins = DetachedPluginsUtil.getDetachedPlugins(since);
            assertThat("Plugins have been detached since the pre-upgrade version",
                    detachedPlugins.size(), greaterThan(4));
            assertThat("Plugins detached between the pre-upgrade version and the current version should be installed",
                    getInstalledDetachedPlugins(r, detachedPlugins).size(), equalTo(detachedPlugins.size()));
            assertNoFailedPlugins(r);
        });
    }

    @Issue("JENKINS-48365")
    @Test
    @LocalData
    public void upgradeFromJenkins2() {
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
    public void newInstallation() {
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
    public void installDetachedDependencies() {
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
        assertNotNull("did not expect to be loading " + c + " from " + c.getClassLoader(), pw);
        assertEquals(expectedPlugin, pw.getShortName());
    }

    @Issue("JENKINS-55582")
    @LocalData
    @Test
    public void nonstandardFilenames() {
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
                assertTrue("Detached plugins should be active if installed", wrapper.isActive());
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
