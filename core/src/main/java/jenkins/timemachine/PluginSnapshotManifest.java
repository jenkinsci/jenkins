package jenkins.timemachine;

import hudson.PluginManager;
import hudson.PluginWrapper;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class PluginSnapshotManifest {

    private long takenAt;
    private List<PluginSnapshot> plugins = new CopyOnWriteArrayList<>();

    public long getTakenAt() {
        return takenAt;
    }

    public PluginSnapshotManifest setTakenAt(long takenAt) {
        this.takenAt = takenAt;
        return this;
    }

    public List<PluginSnapshot> getPlugins() {
        return plugins;
    }

    public PluginSnapshotManifest setPlugins(List<PluginSnapshot> plugins) {
        this.plugins = plugins;
        return this;
    }

    synchronized void addSnapshot(PluginSnapshot snapshot) {
        plugins.add(snapshot);
        // Sort them by pluginId so the equals can work off the
        // List#equals() impl.
        plugins.sort(Comparator.comparing(PluginSnapshot::getPluginId));
    }

    void addSnapshot(PluginWrapper pluginWrapper) {
        addSnapshot(new PluginSnapshot()
                .setPluginId(pluginWrapper.getShortName())
                .setVersion(pluginWrapper.getVersion())
                .setEnabled(pluginWrapper.isEnabled())
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PluginSnapshotManifest that = (PluginSnapshotManifest) o;

        return plugins.equals(that.plugins);
    }

    @Override
    public int hashCode() {
        return plugins.hashCode();
    }

    static @Nonnull
    PluginSnapshotManifest takeSnapshot(@Nonnull PluginManager pluginManager) {
        PluginSnapshotManifest manifest = new PluginSnapshotManifest();
        List<PluginWrapper> plugins = pluginManager.getPlugins();

        for (PluginWrapper pluginWrapper : plugins) {
            manifest.addSnapshot(pluginWrapper);
        }
        manifest.takenAt = System.currentTimeMillis();

        return manifest;
    }
}
