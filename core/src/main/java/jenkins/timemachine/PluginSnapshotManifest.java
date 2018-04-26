package jenkins.timemachine;

import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.XmlFile;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@Restricted(NoExternalUse.class)
public class PluginSnapshotManifest {

    public static final String MANIFEST_FILE_NAME = "manifest.xml";

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

    public PluginSnapshot find(@Nonnull String pluginId) {
        for (PluginSnapshot plugin : plugins) {
            if (pluginId.equals(plugin.getPluginId())) {
                return plugin;
            }
        }
        return null;
    }

    public Set<String> getPluginIds() {
        Set<String> pluginIds = new LinkedHashSet<>();
        for (PluginSnapshot plugin : plugins) {
            pluginIds.add(plugin.getPluginId());
        }
        return pluginIds;
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

    public static @CheckForNull PluginSnapshotManifest load(@Nonnull File manifestFile) throws IOException {
        if (!manifestFile.exists()) {
            throw new IllegalArgumentException("PluginSnapshotManifest file " + manifestFile.getAbsolutePath() + " does not exist.");
        }

        XmlFile xmlFile = new XmlFile(manifestFile);

        try {
            return (PluginSnapshotManifest) xmlFile.read();
        } catch (ClassCastException e) {
            throw new IOException("PluginSnapshotManifest file " + manifestFile.getAbsolutePath() + " does not contain a serialized instance of type PluginSnapshotManifest.");
        }
    }

    public void save(@Nonnull File manifestFile) throws IOException {
        File snapshotDir = manifestFile.getParentFile();

        if (!snapshotDir.exists()) {
            if (!snapshotDir.mkdirs()) {
                throw new IOException("Error creating directory " + snapshotDir.getAbsolutePath());
            }
        }

        XmlFile xmlFile = new XmlFile(manifestFile);
        xmlFile.write(this);
    }
}
