package jenkins.timemachine;

import hudson.PluginManager;
import org.apache.commons.io.FileUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@Restricted(NoExternalUse.class)
public class PluginManagerTimeMachine {

    private static final Logger LOGGER = Logger.getLogger(PluginManagerTimeMachine.class.getName());

    private final File pluginsDir;
    private final File pluginsTimeMachineDir;
    private final File rollbackFile;
    private final List<PluginSnapshotManifest> snapshotManifests;

    public PluginManagerTimeMachine(@Nonnull File pluginsDir) {
        this.pluginsDir = pluginsDir;
        this.pluginsTimeMachineDir = new File(pluginsDir.getParentFile(), "time-machine/plugins");
        this.rollbackFile = new File(pluginsTimeMachineDir, ".rollback");

        if (!pluginsTimeMachineDir.exists()) {
            if (!pluginsTimeMachineDir.mkdirs()) {
                throw new IllegalStateException("Unexpected error creating plugins Time Machine directory: " + pluginsTimeMachineDir.getAbsolutePath());
            }
        }
        snapshotManifests = new ArrayList<>();
    }

    public void loadSnapshotManifests() {
        File[] timeMachineDirFiles = this.pluginsTimeMachineDir.listFiles();

        if (timeMachineDirFiles != null) {
            for (File timeMachineDirFile : timeMachineDirFiles) {
                if (timeMachineDirFile.isDirectory()) {
                    File manifestFile = new File(timeMachineDirFile, PluginSnapshotManifest.MANIFEST_FILE_NAME);
                    if (manifestFile.exists()) {
                        try {
                            addSnapshotManifest(PluginSnapshotManifest.load(manifestFile));
                        } catch (IOException e) {
                            LOGGER.log(Level.SEVERE, "Error loading PluginSnapshotManifest file " + manifestFile.getAbsolutePath(), e);
                        }
                    }
                }
            }
        }
    }

    synchronized void addSnapshotManifest(PluginSnapshotManifest snapshotManifest) {
        snapshotManifests.add(snapshotManifest);
        // sort them by snapshot time
        snapshotManifests.sort((o1, o2) -> {
            if (o1.getTakenAt() < o2.getTakenAt()) {
                return 1;
            } else if (o1.getTakenAt() > o2.getTakenAt()) {
                return -1;
            } else {
                return 0;
            }
        });
    }

    File getPluginsTimeMachineDir() {
        return pluginsTimeMachineDir;
    }

    public synchronized PluginSnapshotManifest getLatestSnapshot() {
        if (snapshotManifests.isEmpty()) {
            return null;
        }
        return snapshotManifests.get(0);
    }

    public void setRollback(String toVersion) {
        try {
            FileUtils.write(rollbackFile, toVersion);
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected error creating rollback marker file.");
        }
    }

    public void doRollback() throws IOException {
        if (!rollbackFile.exists()) {
            LOGGER.log(Level.INFO, "No plugins rollback registered. Continue using the current plugin set.");
            return;
        }

        String rollbackToVersion = FileUtils.readFileToString(rollbackFile);

        LOGGER.log(Level.INFO, "Rolling plugins back to " + rollbackToVersion);

        FileUtils.deleteDirectory(pluginsDir);
        FileUtils.copyDirectory(new File(pluginsTimeMachineDir, rollbackToVersion), pluginsDir);

        // Rollback worked. Okay to delete the marker file now...
        if (!rollbackFile.delete()) {
            LOGGER.log(Level.SEVERE, "Failed to delete the rollback marker file: " + rollbackFile.getAbsolutePath());
        }
    }

    public void takeSnapshot(@Nonnull PluginManager pluginManager) {
        PluginSnapshotManifest nowSnapshot = PluginSnapshotManifest.takeSnapshot(pluginManager);
    }
}
