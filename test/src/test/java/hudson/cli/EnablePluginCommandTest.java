package hudson.cli;

import hudson.Plugin;
import hudson.PluginWrapper;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class EnablePluginCommandTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void enableSinglePlugin() throws IOException {
        String name = "token-macro";
        assertThat(j.jenkins.getPlugin(name), is(nullValue()));
        assertThat(
                new CLICommandInvoker(j, "install-plugin")
                        .withStdin(InstallPluginCommandTest.class.getResourceAsStream("/plugins/token-macro.hpi"))
                        .invokeWithArgs("-name", name, "-deploy", "="),
                succeeded());
        Plugin plugin = j.jenkins.getPlugin(name);
        assertThat(plugin, is(notNullValue()));
        PluginWrapper wrapper = plugin.getWrapper();
        assertTrue(wrapper.isEnabled());
        wrapper.disable();
        assertFalse(wrapper.isEnabled());

        assertThat(
                new CLICommandInvoker(j, "enable-plugin").invokeWithArgs(name),
                succeeded()
        );
        assertTrue(wrapper.isEnabled());
    }

    @Test
    public void enableInvalidPluginFails() {
        assertThat(
                new CLICommandInvoker(j, "enable-plugin").invokeWithArgs("foobar"), failedWith(3)
        );
    }
}
