/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
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

import java.util.List;
import hudson.Extension;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.model.UpdateSite;
import jenkins.model.Jenkins;
import org.kohsuke.args4j.Argument;

/**
 * Outputs a list of installed plugins.
 *
 * @author Michael Koch
 */
@Extension
public class ListPluginsCommand extends CLICommand {
    @Override
    public String getShortDescription() {
        return Messages.ListPluginsCommand_ShortDescription();
    }

    @Argument(metaVar = "NAME", usage = "Name of a specific plugin", required = false)
    public String name;

    protected int run() {
        Jenkins h = Jenkins.getInstance();
        PluginManager pluginManager = h.getPluginManager();

        if (this.name != null) {
            PluginWrapper plugin = pluginManager.getPlugin(this.name);

            if (plugin != null) {
                printPlugin(plugin, plugin.getShortName().length(), plugin.getDisplayName().length());
            }
            else {
                stderr.println(String.format("No plugin with the name '%s' found", this.name));
            }
        }
        else {
            int colWidthShortName = 1;
            int colWidthDisplayName = 1;
            List<PluginWrapper> plugins = pluginManager.getPlugins();

            if (plugins != null) {
                for (PluginWrapper plugin : plugins) {
                    colWidthShortName = Math.max(colWidthShortName, plugin.getShortName().length());
                    colWidthDisplayName = Math.max(colWidthDisplayName, plugin.getDisplayName().length());
                }

                for (PluginWrapper plugin : plugins) {
                    printPlugin(plugin, colWidthShortName, colWidthDisplayName);
                }
            }
        }

        return 0;
    }

    private void printPlugin(PluginWrapper plugin, int colWidthShortName, int colWidthDisplayName) {
        final String version;

        if (plugin.hasUpdate()) {
            UpdateSite.Plugin updateInfo = plugin.getUpdateInfo();
            version = String.format("%s (%s)", plugin.getVersion(), updateInfo.version);
        }
        else {
            version = plugin.getVersion();
        }

        String formatString = String.format("%%-%ds %%-%ds %%s", colWidthShortName, colWidthDisplayName);
        stdout.println(String.format(formatString, plugin.getShortName(), plugin.getDisplayName(), version));
    }
}
