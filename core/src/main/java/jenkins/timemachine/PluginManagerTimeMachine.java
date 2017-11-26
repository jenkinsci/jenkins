package jenkins.timemachine;

import hudson.PluginManager;
import org.apache.commons.io.FileUtils;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class PluginManagerTimeMachine {

    private static final Logger LOGGER = Logger.getLogger(PluginManagerTimeMachine.class.getName());

    private final File pluginsDir;
    private final File pluginsTimeMachineDir;
    private final File rollbackFile;

    public PluginManagerTimeMachine(@Nonnull File pluginsDir) {
        this.pluginsDir = pluginsDir;
        this.pluginsTimeMachineDir = new File(pluginsDir, "../time-machine/plugins");
        this.rollbackFile = new File(pluginsTimeMachineDir, ".rollback");

        if (!pluginsTimeMachineDir.exists()) {
            if (!pluginsTimeMachineDir.mkdirs()) {
                throw new IllegalStateException("Unexpected error creating plugins Time Machine directory: " + pluginsTimeMachineDir.getAbsolutePath());
            }
        }
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
