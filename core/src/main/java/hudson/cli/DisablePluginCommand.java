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

import hudson.Extension;
import hudson.PluginManager;
import hudson.PluginWrapper;
import jenkins.model.Jenkins;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Set;

/**
 * Disable one or more installed plugins. If the plugin has an enabled dependant then it can't be disabled, the process
 * continues, but the exit code is 16, instead of 0. Disabling an already disabled plugin does nothing. It only restart
 * if, at least, one plugin has been disabled and the restart option was set.
 *
 * @since 2.136
 */
@Extension
public class DisablePluginCommand extends CLICommand {

    @Argument(metaVar = "plugins", required = true, usage = "Plugins to be disabled.")
    private List<String> pluginNames;

    @Option(name = "-restart", usage = "Restart Jenkins after disabling plugins.")
    private boolean restart;

    @Override
    public String getShortDescription() {
        return Messages.DisablePluginCommand_ShortDescription();
    }

    @Override
    protected void printUsageSummary(PrintStream stderr) {
        stderr.println(Messages.DisablePluginCommand_PrintUsageSummary());
    }

    @Override
    protected int run() throws Exception {
        Jenkins jenkins = Jenkins.get();
        jenkins.checkPermission(Jenkins.ADMINISTER);

        PluginManager manager = jenkins.getPluginManager();
        int result = 0;
        boolean somePluginDisabled = false; // to ensure we restart when it needs to
        for (String pluginName : pluginNames) {
            switch (disablePlugin(manager, pluginName)) {
                case DISABLED:
                    if(!somePluginDisabled) somePluginDisabled = true;
                    break;
                case NOT_DISABLED_DEPENDANTS:
                    if(result != 16) result = 16; // Custom error code to indicate one plugin, at least, can't be disabled
            }
        }

        if (restart && somePluginDisabled) {
            jenkins.safeRestart();
        }

        return result;
    }

    /**
     * Try to disable a  plugin.
     * @param manager The PluginManager.
     * @param shortName The name of the plugin to disable.
     * @return The result of the disabling of this plugin. See {@link DISABLING_STATUS}
     * @throws IOException An exception disabling the plugin. See {@link PluginWrapper#disable()}
     */
    private DISABLING_STATUS disablePlugin(PluginManager manager, String shortName) throws IOException {
        PluginWrapper plugin = manager.getPlugin(shortName);
        if (plugin == null) {
            throw new IllegalArgumentException(Messages.DisablePluginCommand_NoSuchPlugin(shortName)); // exit with 3
        }

        if (!plugin.isEnabled()) {
            stdout.format(Messages.DisablePluginCommand_Already_Disabled(shortName));
            stdout.println();
            return DISABLING_STATUS.ALREADY_DISABLED;
        }

        Set<String> dependants = plugin.getDependants();
        for (String dependant : dependants) {
            PluginWrapper dependantPlugin = manager.getPlugin(dependant);
            if (dependantPlugin != null && dependantPlugin.isEnabled()) {
                // TO-DO: stdout or stderr? the process continues but as the result code is not 0, it looks like stderr
                stderr.format(Messages.DisablePluginCommand_Plugin_Has_Dependant(shortName, dependant));
                stderr.println();
                return DISABLING_STATUS.NOT_DISABLED_DEPENDANTS;
            }
        }

        plugin.disable();
        stdout.format(Messages.DisablePluginCommand_Plugin_Disabled(plugin.getShortName()));
        stdout.println();

        return DISABLING_STATUS.DISABLED;
    }

    /**
     * An enum to hold the status of a disabling action. To do it more reader-friendly.
     */
    private enum DISABLING_STATUS {
        DISABLED,
        ALREADY_DISABLED,
        NOT_DISABLED_DEPENDANTS
    }
}
