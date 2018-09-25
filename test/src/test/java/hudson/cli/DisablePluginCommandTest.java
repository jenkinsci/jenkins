/*
 * The MIT License
 *
 * Copyright (c) 2018 CloudBees, Inc.
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

package hudson.cli;

import hudson.PluginWrapper;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.WithPlugin;

import java.io.IOException;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.*;

public class DisablePluginCommandTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-27177")
    @WithPlugin({"depender-0.0.2.hpi", "dependee-0.0.2.hpi"})
    public void disablePluginWithOptionalDepender() {
        assertThat(disablePlugins("-restart", "dependee"), failedWith(16));
        assertPluginEnabled("dependee");
        assertJenkinsNotInQuietMode();
    }

    @Test
    @Issue("JENKINS-27177")
    @WithPlugin({"mandatory-depender-0.0.2.hpi", "dependee-0.0.2.hpi"})
    public void disablePluginWithMandatoryDepender() {
        assertThat(disablePlugins("-restart", "dependee"), failedWith(16));
        assertPluginEnabled("dependee");
        assertJenkinsNotInQuietMode();
    }

    @Test
    @Issue("JENKINS-27177")
    @WithPlugin({"mandatory-depender-0.0.2.hpi", "dependee-0.0.2.hpi"})
    public void disableDependentPluginsWrongOrder() {
        assertThat(disablePlugins("-restart", "dependee", "mandatory-depender"), failedWith(16));
        assertPluginDisabled("mandatory-depender");
        assertJenkinsInQuietMode(); // one plugin was disabled (mandatory-depender)
    }

    @Test
    @Issue("JENKINS-27177")
    @WithPlugin({"mandatory-depender-0.0.2.hpi", "dependee-0.0.2.hpi"})
    public void disableDependentPluginsRightOrder() {
        assertThat(disablePlugins("-restart", "mandatory-depender", "dependee"), succeeded());

        assertPluginDisabled("dependee");
        assertPluginDisabled("mandatory-depender");
        assertJenkinsInQuietMode();
    }

    @Test
    @Issue("JENKINS-27177")
    @WithPlugin("dependee-0.0.2.hpi")
    public void disablePluginWithoutDepender() {
        assertThat(disablePlugins("-restart", "dependee"), succeeded());
        assertPluginDisabled("dependee");
        assertJenkinsInQuietMode();
    }

    @Test
    @Issue("JENKINS-27177")
    @WithPlugin("dependee-0.0.2.hpi")
    public void disablePluginWithoutDependerNorRestart() {
        assertThat(disablePlugins("dependee"), succeeded());
        assertPluginDisabled("dependee");
        assertJenkinsNotInQuietMode();
    }

    @Test
    @Issue("JENKINS-27177")
    @WithPlugin("dependee-0.0.2.hpi")
    public void disableInvalidPlugin() {
        assertThat(disablePlugins("-restart", "wrongname"), failedWith(3));
        assertJenkinsNotInQuietMode();
    }

    @Test
    @Issue("JENKINS-27177")
    @WithPlugin("dependee-0.0.2.hpi")
    public void disableAlreadyDisabledPluginNoRestart() throws IOException {
        disablePlugin("dependee");

        assertPluginDisabled("dependee");
        assertThat(disablePlugins("-restart", "dependee"), succeeded());
        assertPluginDisabled("dependee");
        assertJenkinsNotInQuietMode();
    }

    @Test
    @Issue("JENKINS-27177")
    @WithPlugin({"variant.hpi", "depender-0.0.2.hpi", "mandatory-depender-0.0.2.hpi", "plugin-first.hpi", "dependee-0.0.2.hpi", })
    public void disablePluginsWithError16() {
        assertThat(disablePlugins("-restart", "variant", "dependee", "depender", "plugin-first", "mandatory-depender"), failedWith(16));
        assertPluginDisabled("variant");
        assertPluginEnabled("dependee");
        assertPluginDisabled("depender");
        assertPluginDisabled("plugin-first");
        assertPluginDisabled("mandatory-depender");
        assertJenkinsInQuietMode(); // some plugins were disabled
    }

    private void disablePlugin(String name) throws IOException {
        PluginWrapper plugin = j.getPluginManager().getPlugin(name);
        assertThat(plugin, is(notNullValue()));
        plugin.disable();
    }

    /**
     * Disable a list of plugins using the CLI command.
     * @param args Arguments to pass to the command.
     * @return Result of the command. 0 if succeed, 16 if some plugin couldn't be disabled due to dependant plugins.
     */
    private CLICommandInvoker.Result disablePlugins(String... args) {
        return new CLICommandInvoker(j, new DisablePluginCommand()).invokeWithArgs(args);
    }

    private void assertPluginDisabled(String name) {
        PluginWrapper plugin = j.getPluginManager().getPlugin(name);
        assertThat(plugin, is(notNullValue()));
        assertFalse(plugin.isEnabled());
    }

    private void assertPluginEnabled(String name) {
        PluginWrapper plugin = j.getPluginManager().getPlugin(name);
        assertThat(plugin, is(notNullValue()));
        assertTrue(plugin.isEnabled());
    }

    private void assertJenkinsInQuietMode() {
        QuietDownCommandTest.assertJenkinsInQuietMode(j);
    }

    private void assertJenkinsNotInQuietMode() {
        QuietDownCommandTest.assertJenkinsNotInQuietMode(j);
    }
}
