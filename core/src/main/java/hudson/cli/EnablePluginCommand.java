package hudson.cli;

import hudson.Extension;
import hudson.PluginManager;
import hudson.PluginWrapper;
import jenkins.model.Jenkins;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.List;

/**
 * Enables one or more installed plugins. The listed plugins
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
        for (String pluginName : pluginNames) {
            enablePlugin(manager, pluginName);
        }
        if (restart) {
            jenkins.safeRestart();
        }
        return 0;
    }

    private static void enablePlugin(PluginManager manager, String shortName) throws IOException {
        PluginWrapper plugin = manager.getPlugin(shortName);
        if (plugin == null) throw new IllegalArgumentException(Messages.EnablePluginCommand_NoSuchPlugin(shortName));
        if (plugin.isEnabled()) return;
        for (PluginWrapper.Dependency dependency : plugin.getDependencies()) {
            enablePlugin(manager, dependency.shortName);
        }
        plugin.enable();
    }
}
