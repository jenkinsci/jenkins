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

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import hudson.PluginManager;
import hudson.PluginWrapper;
import java.io.IOException;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class EnablePluginCommandTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private CLICommandInvoker.Result installTestPlugin(String name) {
        return new CLICommandInvoker(j, new InstallPluginCommand())
                .withStdin(EnablePluginCommandTest.class.getResourceAsStream("/plugins/" + name + ".hpi"))
                .invokeWithArgs("-name", name, "-deploy", "=");
    }

    private CLICommandInvoker.Result enablePlugins(String... names) {
        return new CLICommandInvoker(j, new EnablePluginCommand()).invokeWithArgs(names);
    }

    private void assertPluginEnabled(String name) {
        PluginWrapper plugin = j.getPluginManager().getPlugin(name);
        assertThat(plugin, is(notNullValue()));
        assertTrue(plugin.isEnabled());
    }

    private void disablePlugin(String name) throws IOException {
        PluginWrapper plugin = j.getPluginManager().getPlugin(name);
        assertThat(plugin, is(notNullValue()));
        plugin.disable();
    }

    private void assertPluginDisabled(String name) {
        PluginWrapper plugin = j.getPluginManager().getPlugin(name);
        assertThat(plugin, is(notNullValue()));
        assertFalse(plugin.isEnabled());
    }

    private void assumeNotWindows() {
        Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows"));
    }

    private void assertJenkinsInQuietMode() {
        QuietDownCommandTest.assertJenkinsInQuietMode(j);
    }

    private void assertJenkinsNotInQuietMode() {
        QuietDownCommandTest.assertJenkinsNotInQuietMode(j);
    }

    @Test
    @Issue("JENKINS-52822")
    public void enableSinglePlugin() throws IOException {
        String name = "token-macro";
        PluginManager m = j.getPluginManager();
        assertThat(m.getPlugin(name), is(nullValue()));
        assertThat(installTestPlugin(name), succeeded());
        assertPluginEnabled(name);
        disablePlugin(name);
        assertPluginDisabled(name);
        assertThat(enablePlugins(name), succeeded());
        assertPluginEnabled(name);
        assertJenkinsNotInQuietMode();
    }

    @Test
    @Issue("JENKINS-52822")
    public void enableInvalidPluginFails() {
        assertThat(enablePlugins("foobar"), failedWith(3));
        assertJenkinsNotInQuietMode();
    }

    @Test
    @Issue("JENKINS-52822")
    public void enableDependerEnablesDependee() throws IOException {
        installTestPlugin("dependee");
        installTestPlugin("depender");
        disablePlugin("depender");
        disablePlugin("dependee");
        assertThat(enablePlugins("depender"), succeeded());
        assertPluginEnabled("depender");
        assertPluginEnabled("dependee");
        assertJenkinsNotInQuietMode();
    }

    @Ignore("TODO calling restart seems to break Surefire")
    @Test
    @Issue("JENKINS-52950")
    public void enablePluginWithRestart() throws IOException {
        assumeNotWindows();
        String name = "credentials";
        assertThat(installTestPlugin(name), succeeded());
        disablePlugin(name);
        assertThat(enablePlugins("-restart", name), succeeded());
        assertJenkinsInQuietMode();
    }

    @Test
    @Issue("JENKINS-52950")
    public void enableNoPluginsWithRestartIsNoOp() {
        assumeNotWindows();
        String name = "icon-shim";
        assertThat(installTestPlugin(name), succeeded());
        assertThat(enablePlugins("-restart", name), succeeded());
        assertJenkinsNotInQuietMode();
    }
}
