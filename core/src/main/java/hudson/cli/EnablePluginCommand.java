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

import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Enables one or more installed plugins. The listed plugins must already be installed along with its dependencies.
 * Any listed plugin with disabled dependencies will have its dependencies enabled transitively. Note that enabling an
 * already enabled plugin does nothing.
 *
 * @since TODO
 */
@Extension
public class EnablePluginCommand extends CLICommand {

    @Argument(required = true, usage = "Enables the plugins with the given short names and their dependencies.")
    private List<String> pluginNames;

    @Option(name = "-restart", usage = "Restart Jenkins after enabling plugins.")
    private boolean restart;

    @Override
    public String getShortDescription() {
        return Messages.EnablePluginCommand_ShortDescription();
    }

    @Override
    protected int run() throws Exception {
        Jenkins jenkins = Jenkins.get();
        jenkins.checkPermission(Jenkins.ADMINISTER);
        PluginManager manager = jenkins.getPluginManager();
        List<PluginWrapper> pluginsToEnable = pluginNames.stream()
                .flatMap(pluginName -> getPluginsToEnable(manager, pluginName))
                .collect(toList());
        for (PluginWrapper plugin : pluginsToEnable) {
            stdout.println(String.format("Enabling plugin `%s' (%s)", plugin.getShortName(), plugin.getVersion()));
            plugin.enable();
        }
        if (restart) {
            jenkins.safeRestart();
        }
        return 0;
    }

    private static Stream<PluginWrapper> getPluginsToEnable(PluginManager manager, String shortName) {
        PluginWrapper plugin = manager.getPlugin(shortName);
        if (plugin == null) throw new IllegalArgumentException(Messages.EnablePluginCommand_NoSuchPlugin(shortName));
        if (plugin.isEnabled()) return Stream.empty();
        Stream<PluginWrapper> dependencies = plugin.getDependencies().stream()
                .flatMap(dependency -> getPluginsToEnable(manager, dependency.shortName));
        return Stream.concat(Stream.of(plugin), dependencies);
    }

}
