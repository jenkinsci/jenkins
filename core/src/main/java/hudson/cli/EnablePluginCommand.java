package hudson.cli;

import hudson.Extension;
import hudson.Plugin;
import jenkins.model.Jenkins;
import org.kohsuke.args4j.Argument;

/**
 * Enables an installed plugin by name.
 *
 * @since TODO
 */
@Extension
public class EnablePluginCommand extends CLICommand {

    @Argument(required = true, usage = "Enables the plugin with the given name if it's installed.")
    private String pluginName;

    @Override
    public String getShortDescription() {
        return Messages.EnablePluginCommand_ShortDescription();
    }

    @Override
    protected int run() throws Exception {
        Jenkins jenkins = Jenkins.get();
        jenkins.checkPermission(Jenkins.ADMINISTER);
        Plugin plugin = jenkins.getPlugin(pluginName);
        if (plugin == null) {
            throw new IllegalArgumentException(Messages.EnablePluginCommand_NoSuchPlugin(pluginName));
        }
        plugin.getWrapper().enable();
        return 0;
    }
}
