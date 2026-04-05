
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
import static hudson.cli.DisablePluginCommand.RETURN_CODE_NOT_DISABLED_DEPENDANTS;
import static hudson.cli.DisablePluginCommand.RETURN_CODE_NO_SUCH_PLUGIN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.Functions;
import hudson.PluginWrapper;
import java.io.IOException;
import java.util.function.BiPredicate;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.WithPlugin;

@WithJenkins
class DisablePluginCommandTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Can disable a plugin with an optional dependent plugin.
     * With strategy none.
     */
    @Test
    @Issue("JENKINS-27177")
    @WithPlugin({"depender-0.0.2.hpi", "dependee-0.0.2.hpi"})
    void canDisablePluginWithOptionalDependerStrategyNone() {
        assertThat(disablePluginsCLiCommand("-strategy", "NONE", "dependee"), succeeded());
        assertPluginDisabled("dependee");
    }

    @Test
    @Issue("JENKINS-27177")
    @WithPlugin({"depender-0.0.2.hpi", "dependee-0.0.2.hpi", "mandatory-depender-0.0.2.hpi"})
    void canDisablePluginWithDependentsDisabledStrategyNone() throws IOException {
        disablePlugin("mandatory-depender");
        CLICommandInvoker.Result result = disablePluginsCLiCommand("-strategy", "NONE", "dependee");

        assertThat(result, succeeded());
        assertEquals(1, StringUtils.countMatches(result.stdout(), "Disabling"), "Disabling only dependee");
        assertPluginDisabled("dependee");
    }

    /**
     * Can't disable a plugin with a mandatory dependent plugin.
     * With default strategy (none).
     */
    @Test
    @Issue("JENKINS-27177")
    @WithPlugin({"mandatory-depender-0.0.2.hpi", "dependee-0.0.2.hpi"})
    void cannotDisablePluginWithMandatoryDependerStrategyNone() {
        assertThat(disablePluginsCLiCommand("dependee"), failedWith(RETURN_CODE_NOT_DISABLED_DEPENDANTS));
        assertPluginEnabled("dependee");
    }

    /**
     * Can't disable a plugin with a mandatory dependent plugin before its dependent plugin.
     * With default strategy (none).
     */
    @Test
    @Issue("JENKINS-27177")
    @WithPlugin({"mandatory-depender-0.0.2.hpi", "dependee-0.0.2.hpi"})
    void cannotDisableDependentPluginWrongOrderStrategyNone() {
        assertThat(disablePluginsCLiCommand("dependee", "mandatory-depender"), failedWith(RETURN_CODE_NOT_DISABLED_DEPENDANTS));
        assertPluginDisabled("mandatory-depender");
        assertPluginEnabled("dependee");
    }

    /**
     * Can disable a plugin with a mandatory dependent plugin before its dependent plugin with <i>all</i> strategy
     */
    @Test
    @Issue("JENKINS-27177")
    @WithPlugin({"mandatory-depender-0.0.2.hpi", "dependee-0.0.2.hpi"})
    void canDisableDependentPluginWrongOrderStrategyAll() {
        assertThat(disablePluginsCLiCommand("dependee", "mandatory-depender", "-strategy", "all"), succeeded());
        assertPluginDisabled("mandatory-depender");
        assertPluginDisabled("dependee");
    }

    /**
     * Can disable a plugin with a mandatory dependent plugin after being disabled the mandatory dependent plugin. With
     * default strategy (none).
     */
    @Test
    @Issue("JENKINS-27177")
    @WithPlugin({"mandatory-depender-0.0.2.hpi", "dependee-0.0.2.hpi"})
    void canDisableDependentPluginsRightOrderStrategyNone() {
        assertThat(disablePluginsCLiCommand("mandatory-depender", "dependee"), succeeded());
        assertPluginDisabled("dependee");
        assertPluginDisabled("mandatory-depender");
    }

    /**
     * Can disable a plugin without dependents plugins and Jenkins restart after it if -restart argument is passed.
     */
    @Disabled("TODO calling restart seems to break Surefire")
    @Test
    @Issue("JENKINS-27177")
    @WithPlugin("dependee-0.0.2.hpi")
    void restartAfterDisable() {
        assumeNotWindows();
        assertThat(disablePluginsCLiCommand("-restart", "dependee"), succeeded());
        assertPluginDisabled("dependee");
        assertJenkinsInQuietMode();
    }

    /**
     * Can disable a plugin without dependents plugins and Jenkins doesn't restart after it if -restart is not passed.
     */
    @Test
    @Issue("JENKINS-27177")
    @WithPlugin("dependee-0.0.2.hpi")
    void notRestartAfterDisablePluginWithoutArgumentRestart() throws Exception {
        assertThat(disablePluginsCLiCommand("dependee"), succeeded());
        assertPluginDisabled("dependee");
        assertJenkinsNotInQuietMode();
        j.waitUntilNoActivity();
    }

    /**
     * A non-existing plugin returns with a {@link DisablePluginCommand#RETURN_CODE_NO_SUCH_PLUGIN} status code.
     */
    @Test
    @Issue("JENKINS-27177")
    @WithPlugin("dependee-0.0.2.hpi")
    void returnCodeDisableInvalidPlugin() {
        assertThat(disablePluginsCLiCommand("wrongname"), failedWith(RETURN_CODE_NO_SUCH_PLUGIN));
    }

    /**
     * A plugin already disabled returns 0 and jenkins doesn't restart even though you passed the -restart argument.
     * @throws IOException See {@link PluginWrapper#disable()}.
     */
    @Test
    @Issue("JENKINS-27177")
    @WithPlugin("dependee-0.0.2.hpi")
    void disableAlreadyDisabledPluginNotRestart() throws Exception {
        // Disable before the command call
        disablePlugin("dependee");

        assertPluginDisabled("dependee");
        assertThat(disablePluginsCLiCommand("-restart", "dependee"), succeeded());
        assertPluginDisabled("dependee");
        assertJenkinsNotInQuietMode();
        j.waitUntilNoActivity();
    }

    /**
     * If some plugins are disabled, Jenkins will restart even though the status code isn't 0 (is 16).
     */
    @Disabled("TODO calling restart seems to break Surefire")
    @Test
    @Issue("JENKINS-27177")
    @WithPlugin({"depender-0.0.2.hpi", "mandatory-depender-0.0.2.hpi", "plugin-first.hpi", "dependee-0.0.2.hpi"})
    void restartAfterDisablePluginsAndErrors() {
        assumeNotWindows();
        assertThat(disablePluginsCLiCommand("-restart", "dependee", "depender", "plugin-first", "mandatory-depender"), failedWith(RETURN_CODE_NOT_DISABLED_DEPENDANTS));
        assertPluginEnabled("dependee");
        assertPluginDisabled("depender");
        assertPluginDisabled("plugin-first");
        assertPluginDisabled("mandatory-depender");
        assertJenkinsInQuietMode(); // some plugins were disabled, so it should be restarting
    }

    /**
     * All the dependent plugins, mandatory or optional, are disabled using <i>-strategy all</i>.
     */
    @Test
    @Issue("JENKINS-27177")
    @WithPlugin({"depender-0.0.2.hpi", "mandatory-depender-0.0.2.hpi", "plugin-first.hpi", "dependee-0.0.2.hpi"})
    void disablePluginsStrategyAll() {
        assertPluginEnabled("dependee");
        assertPluginEnabled("depender");
        assertPluginEnabled("mandatory-depender");
        assertThat(disablePluginsCLiCommand("-strategy", "all", "dependee", "plugin-first"), succeeded());
        assertPluginDisabled("dependee");
        assertPluginDisabled("depender");
        assertPluginDisabled("plugin-first");
        assertPluginDisabled("mandatory-depender");
    }

    /**
     * Only the mandatory dependent plugins are disabled using <i>-strategy mandatory</i>.
     */
    @Test
    @Issue("JENKINS-27177")
    @WithPlugin({"depender-0.0.2.hpi", "mandatory-depender-0.0.2.hpi", "plugin-first.hpi", "dependee-0.0.2.hpi"})
    void disablePluginsStrategyMandatory() {
        assertThat(disablePluginsCLiCommand("-strategy", "mandatory", "dependee", "plugin-first"), succeeded());
        assertPluginDisabled("dependee");
        assertPluginEnabled("depender");
        assertPluginDisabled("plugin-first");
        assertPluginDisabled("mandatory-depender");
    }

    /**
     * A plugin already disabled because it's a dependent plugin of one previously disabled appear two times in the log
     * with different messages.
     */
    @Test
    @Issue("JENKINS-27177")
    @WithPlugin({"depender-0.0.2.hpi", "dependee-0.0.2.hpi"})
    void disablePluginsMessageAlreadyDisabled() {
        CLICommandInvoker.Result result = disablePluginsCLiCommand("-strategy", "all", "dependee", "depender");
        assertThat(result, succeeded());

        assertPluginDisabled("dependee");
        assertPluginDisabled("depender");

        assertTrue(checkResultWith(result, Strings.CS::contains, "depender", PluginWrapper.PluginDisableStatus.DISABLED), "An occurrence of the depender plugin in the log says it was successfully disabled");
        assertTrue(checkResultWith(result, Strings.CS::contains, "depender", PluginWrapper.PluginDisableStatus.ALREADY_DISABLED), "An occurrence of the depender plugin in the log says it was already disabled");
    }

    /**
     * The return code is the first error distinct of 0 found during the process. In this case dependent plugins not
     * disabled.
     */
    @Test
    @Issue("JENKINS-27177")
    @WithPlugin({"dependee-0.0.2.hpi", "mandatory-depender-0.0.2.hpi"})
    void returnCodeFirstErrorIsDependents() {
        CLICommandInvoker.Result result = disablePluginsCLiCommand("dependee", "badplugin");
        assertThat(result, failedWith(RETURN_CODE_NOT_DISABLED_DEPENDANTS));

        assertPluginEnabled("dependee");
    }

    /**
     * The return code is the first error distinct of 0 found during the process. In this case no such plugin.
     */
    @Test
    @Issue("JENKINS-27177")
    @WithPlugin({"dependee-0.0.2.hpi", "mandatory-depender-0.0.2.hpi"})
    void returnCodeFirstErrorIsNoSuchPlugin() {
        CLICommandInvoker.Result result = disablePluginsCLiCommand("badplugin", "dependee");
        assertThat(result, failedWith(RETURN_CODE_NO_SUCH_PLUGIN));

        assertPluginEnabled("dependee");
    }

    /**
     * In quiet mode, no message is printed if all plugins are disabled or were already disabled.
     */
    @Test
    @Issue("JENKINS-27177")
    @WithPlugin({"depender-0.0.2.hpi", "dependee-0.0.2.hpi", "mandatory-depender-0.0.2.hpi"})
    void quietModeEmptyOutputSucceed() {
        CLICommandInvoker.Result result = disablePluginsCLiCommand("-strategy", "all", "-quiet", "dependee");
        assertThat(result, succeeded());

        assertPluginDisabled("dependee");
        assertPluginDisabled("depender");
        assertPluginDisabled("mandatory-depender");

        assertThat("No log in quiet mode if all plugins disabled", result.stdout(), is(emptyOrNullString()));
    }

    /**
     * In quiet mode, only the errors (no such plugin) are printed.
     */
    @Test
    @Issue("JENKINS-27177")
    @WithPlugin({"depender-0.0.2.hpi", "dependee-0.0.2.hpi", "mandatory-depender-0.0.2.hpi"})
    void quietModeWithErrorNoSuch() {
        CLICommandInvoker.Result result = disablePluginsCLiCommand("-quiet", "-strategy", "all", "dependee", "badplugin");
        assertThat(result, failedWith(RETURN_CODE_NO_SUCH_PLUGIN));

        assertPluginDisabled("dependee");
        assertPluginDisabled("depender");
        assertPluginDisabled("mandatory-depender");

        assertTrue(checkResultWith(result, Strings.CS::startsWith, "badplugin", PluginWrapper.PluginDisableStatus.NO_SUCH_PLUGIN), "Only error NO_SUCH_PLUGIN in quiet mode");
    }

    /**
     * In quiet mode, only the errors (dependents plugins) are printed.
     */
    @Test
    @Issue("JENKINS-27177")
    @WithPlugin({"depender-0.0.2.hpi", "dependee-0.0.2.hpi", "mandatory-depender-0.0.2.hpi"})
    void quietModeWithErrorDependents() {
        CLICommandInvoker.Result result = disablePluginsCLiCommand("-quiet", "-strategy", "none", "dependee");
        assertThat(result, failedWith(RETURN_CODE_NOT_DISABLED_DEPENDANTS));

        assertPluginEnabled("dependee");
        assertPluginEnabled("depender");
        assertPluginEnabled("mandatory-depender");

        assertTrue(checkResultWith(result, Strings.CS::startsWith, "dependee", PluginWrapper.PluginDisableStatus.NOT_DISABLED_DEPENDANTS), "Only error NOT_DISABLED_DEPENDANTS in quiet mode");
    }

    /**
     * Helper method to check the output of a result with a specific method allowing two arguments (
     * StringUtils::startsWith or StringUtils::contents). This method avoid to have it hardcoded the messages. We avoid
     * having to compose the descriptive text of the message by using a <i>stop</i> flag to ignore the last characters.
     * This method supposes that the descriptive text is at the last of the string.
     * @param result the result of the command.
     * @param method a method with two string arguments to check against
     * @param plugin the plugin printed outSetting the plugin and status as parameters, the method gets
     * the string printed using the
     * @param status the status printed out
     * @return true if the output has been checked against the method using the plugin and status args
     */
    private boolean checkResultWith(CLICommandInvoker.Result result, BiPredicate<String, String> method, String plugin, PluginWrapper.PluginDisableStatus status) {
            String noMatterFollowingChars = "/!$stop";
            String outExpected = Messages.DisablePluginCommand_StatusMessage(plugin, status, noMatterFollowingChars);
            outExpected = StringUtils.substringBefore(outExpected, noMatterFollowingChars);
            return method.test(result.stdout(), outExpected);
    }

    /**
     * Disable a plugin using the {@link PluginWrapper#disable()} method.
     * @param name the name of the plugin.
     * @throws IOException if the disablement cannot be made.
     */
    private void disablePlugin(String name) throws IOException {
        PluginWrapper plugin = j.getPluginManager().getPlugin(name);
        assertThat(plugin, is(notNullValue()));
        plugin.disable();
    }

    /**
     * Disable a list of plugins using the CLI command.
     * @param args Arguments to pass to the command.
     * @return Result of the command. 0 if succeed, 16 if some plugin couldn't be disabled due to dependent plugins.
     */
    private CLICommandInvoker.Result disablePluginsCLiCommand(String... args) {
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

    private void assumeNotWindows() {
        Assumptions.assumeFalse(Functions.isWindows());
    }
}
