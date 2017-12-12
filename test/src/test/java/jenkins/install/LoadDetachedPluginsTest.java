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
import hudson.ClassicPluginStrategy.DetachedPlugin;
import hudson.PluginManager;
import hudson.PluginManagerUtil;
import hudson.PluginWrapper;
import hudson.util.VersionNumber;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class LoadDetachedPluginsTest {

    @Rule public RestartableJenkinsRule rr = PluginManagerUtil.newRestartableJenkinsRule();

    @Issue("JENKINS-48365")
    @Test
    public void detachedPluginsInstalledAfterUpgrade() throws IOException {
        VersionNumber since = new VersionNumber("1.1");
        rr.then(r -> {
            List<DetachedPlugin> detachedPlugins = ClassicPluginStrategy.getDetachedPlugins(since);
            assertFalse("Many plugins have been detached since the given version", detachedPlugins.isEmpty());
            assertTrue("Detached plugins should not be installed on a new instance",
                    getInstalledDetachedPlugins(r, detachedPlugins).isEmpty());
           // Trick PluginManager into thinking an upgrade happened when Jenkins restarts.
           InstallUtil.saveLastExecVersion(since.toString());
        });
        rr.then(r -> {
            List<DetachedPlugin> detachedPlugins = ClassicPluginStrategy.getDetachedPlugins(since);
            assertThat("Plugins have been detached since the pre-upgrade version",
                    detachedPlugins.size(), greaterThan(15));
            assertThat("Plugins detached between the pre-upgrade version and the current version should be installed",
                    getInstalledDetachedPlugins(r, detachedPlugins).size(), equalTo(detachedPlugins.size()));
        });
    }

    private List<PluginWrapper> getInstalledDetachedPlugins(JenkinsRule r, List<DetachedPlugin> detachedPlugins) {
        PluginManager pluginManager = r.jenkins.getPluginManager();
        List<PluginWrapper> installedPlugins = new ArrayList<>();
        for (DetachedPlugin plugin : detachedPlugins) {
            PluginWrapper wrapper = pluginManager.getPlugin(plugin.getShortName());
            if (wrapper != null) {
                installedPlugins.add(wrapper);
                assertTrue(wrapper.isActive());
            }
        }
        return installedPlugins;
    }

}
