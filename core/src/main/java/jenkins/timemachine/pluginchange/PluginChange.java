package jenkins.timemachine.pluginchange;

import jenkins.timemachine.PluginSnapshot;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@Restricted(NoExternalUse.class)
public abstract class PluginChange {
    private final PluginSnapshot plugin;

    public PluginChange(PluginSnapshot plugin) {
        this.plugin = plugin;
    }

    public PluginSnapshot getPlugin() {
        return plugin;
    }
}
