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
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.ExtensionList;
import hudson.PluginManager;
import hudson.PluginManagerUtil;
import hudson.PluginWrapper;
import java.util.ArrayList;
import java.util.List;
import jenkins.plugins.DetachedPluginsUtil;
import jenkins.plugins.DetachedPluginsUtil.DetachedPlugin;
import jenkins.security.UpdateSiteWarningsMonitor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;
import org.jvnet.hudson.test.recipes.LocalData;

@Tag("SmokeTest")
class LoadDetachedPluginsTest {

    @RegisterExtension
    private final JenkinsSessionExtension rr = PluginManagerUtil.newJenkinsSessionExtension();

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
