package jenkins.timemachine;

import hudson.util.VersionNumber;
import jenkins.timemachine.pluginchange.Disabled;
import jenkins.timemachine.pluginchange.Downgraded;
import jenkins.timemachine.pluginchange.Enabled;
import jenkins.timemachine.pluginchange.PluginChange;
import jenkins.timemachine.pluginchange.Installed;
import jenkins.timemachine.pluginchange.Uninstalled;
import jenkins.timemachine.pluginchange.Upgraded;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@Restricted(NoExternalUse.class)
public class PluginChanges {

    private final PluginSnapshot fromPlugin;
    private final PluginSnapshot toPlugin;
    private final List<PluginChange> pluginChanges = new ArrayList<>();

    PluginChanges(@Nonnull String pluginId, @Nonnull PluginSnapshotManifest from, @Nonnull PluginSnapshotManifest to) {
        this.fromPlugin = from.find(pluginId);
        this.toPlugin = to.find(pluginId);
        if (this.fromPlugin == null && this.toPlugin == null) {
            throw new IllegalArgumentException("Plugin " + pluginId + " not found in either manifest.");
        }
        initChanges();
    }

    private void initChanges() {
        if (fromPlugin == null && toPlugin != null) {
            pluginChanges.add(new Installed(toPlugin));
        } else if (fromPlugin != null && toPlugin == null) {
            pluginChanges.add(new Uninstalled(fromPlugin));
        } else {
            // Plugin is in both manifests

            // Version change ...
            VersionNumber fromVersion = fromPlugin.getVersion();
            VersionNumber toVersion = toPlugin.getVersion();
            if (toVersion.isNewerThan(fromVersion)) {
                pluginChanges.add(new Upgraded(fromPlugin, toPlugin));
            } else if (toVersion.isOlderThan(fromVersion)) {
                pluginChanges.add(new Downgraded(fromPlugin, toPlugin));
            }

            // Enabled/disabled ...
            if (fromPlugin.isEnabled() && !toPlugin.isEnabled()) {
                pluginChanges.add(new Disabled(toPlugin));
            } else if (!fromPlugin.isEnabled() && toPlugin.isEnabled()) {
                pluginChanges.add(new Enabled(toPlugin));
            }
        }
    }

    public List<PluginChange> getPluginChanges() {
        return pluginChanges;
    }

    public boolean hasChanges() {
        return !pluginChanges.isEmpty();
    }

    public static List<PluginChanges> changes(@Nonnull PluginSnapshotManifest from, @Nonnull PluginSnapshotManifest to) {
        List<PluginChanges> changes = new ArrayList<>();
        Set<String> allPluginIds = new LinkedHashSet<>();

        allPluginIds.addAll(from.getPluginIds());
        allPluginIds.addAll(to.getPluginIds());

        for (String pluginId : allPluginIds) {
            PluginChanges pluginChanges = new PluginChanges(pluginId, from, to);
            if (pluginChanges.hasChanges()) {
                changes.add(pluginChanges);
            }
        }

        return changes;
    }
}
