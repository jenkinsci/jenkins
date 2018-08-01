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

import hudson.PluginManager;
import hudson.PluginWrapper;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

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

    @Test
    public void enableSinglePlugin() throws IOException {
        String name = "token-macro";
        PluginManager m = j.getPluginManager();
        assertThat(m.getPlugin(name), is(nullValue()));
        assertThat(installTestPlugin("token-macro"), succeeded());
        PluginWrapper wrapper = m.getPlugin(name);
        assertThat(wrapper, is(notNullValue()));
        assertTrue(wrapper.isEnabled());
        wrapper.disable();
        assertFalse(wrapper.isEnabled());

        assertThat(enablePlugins(name), succeeded());
        assertTrue(wrapper.isEnabled());
    }

    @Test
    public void enableInvalidPluginFails() {
        assertThat(
                new CLICommandInvoker(j, "enable-plugin").invokeWithArgs("foobar"), failedWith(3)
        );
    }

    @Test
    public void enableDependerEnablesDependee() throws IOException {
        installTestPlugin("dependee");
        installTestPlugin("depender");
        PluginManager m = j.getPluginManager();
        PluginWrapper depender = m.getPlugin("depender");
        assertThat(depender, is(notNullValue()));
        PluginWrapper dependee = m.getPlugin("dependee");
        assertThat(dependee, is(notNullValue()));

        depender.disable();
        dependee.disable();

        assertThat(enablePlugins("depender"), succeeded());
        assertTrue(depender.isEnabled());
        assertTrue(dependee.isEnabled());
    }
}
