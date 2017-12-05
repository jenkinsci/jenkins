package jenkins.timemachine;

import hudson.PluginManager;
import hudson.util.HttpResponses;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.json.JsonHttpResponse;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
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

    List<PluginSnapshotManifest> getSnapshotManifests() {
        return snapshotManifests;
    }

    public HttpResponse doSnapshots() {
        JSONArray response = new JSONArray();
        for (PluginSnapshotManifest snapshotManifest : snapshotManifests) {
            response.add(snapshotManifest.getTakenAt());
        }
        return HttpResponses.okJSON(response);
    }

    public HttpResponse doSnapshotChanges(StaplerRequest request) {
        long from;
        long to;

        try {
            from = Long.parseLong(request.getParameter("from"));
            to = Long.parseLong(request.getParameter("to"));
        } catch (Exception e) {
            return HttpResponses.errorJSON("'from' and 'to' request params should be long timestamps.");
        }

        PluginSnapshotManifest fromManifest = getManifest(from);
        PluginSnapshotManifest toManifest = getManifest(to);
        if (fromManifest == null) {
            return HttpResponses.errorJSON("Unknown 'from' timestamp: " + from);
        } else if (toManifest == null) {
            return HttpResponses.errorJSON("Unknown 'to' timestamp: " + to);
        }
        List<PluginChanges> changes = PluginChanges.changes(fromManifest, toManifest);

        JSONArray response = new JSONArray();
        for (PluginChanges pluginChanges : changes) {
            response.add(pluginChanges.toJSONObject());
        }

        return HttpResponses.okJSON(response);
    }

    private PluginSnapshotManifest getManifest(long timestamp) {
        for (PluginSnapshotManifest snapshotManifest : snapshotManifests) {
            if (snapshotManifest.getTakenAt() == timestamp) {
                return snapshotManifest;
            }
        }
        return null;
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

        String rollbackToVersion = FileUtils.readFileToString(rollbackFile).trim();
        File snapshotDir = new File(pluginsTimeMachineDir, rollbackToVersion);

        if (snapshotDir.exists()) {
            File manifestFile = new File(snapshotDir, PluginSnapshotManifest.MANIFEST_FILE_NAME);
            PluginSnapshotManifest snapshotManifest = PluginSnapshotManifest.load(manifestFile);
            PluginSnapshotManifest latestSnapshotBackup = getLatestSnapshot();

            if (!snapshotManifest.equals(latestSnapshotBackup)) {
                LOGGER.log(Level.INFO, "Rolling plugins back to " + rollbackToVersion +
                        " (taken " + new Date(snapshotManifest.getTakenAt()) + ").");

                FileUtils.deleteDirectory(pluginsDir);
                FileUtils.copyDirectory(snapshotDir, pluginsDir);
            } else {
                LOGGER.log(Level.INFO, "Not rolling plugins back to " + rollbackToVersion +
                        " (taken " + new Date(snapshotManifest.getTakenAt()) +
                        "). No difference i.e. same set of plugins.");
            }
        } else {
            LOGGER.log(Level.WARNING, "Rollback marker file " + rollbackFile.getAbsolutePath()
                    + " references an unknown snapshot '" + rollbackToVersion + "'. Ignoring.");
        }

        // Rollback worked. Okay to delete the marker file now...
        if (!rollbackFile.delete()) {
            LOGGER.log(Level.SEVERE, "Failed to delete the rollback marker file: " + rollbackFile.getAbsolutePath());
        }
    }

    private synchronized void doNowBackup(@Nonnull PluginSnapshotManifest nowSnapshot) throws IOException {
        LOGGER.log(Level.INFO, "Creating plugin snapshot backup: " + nowSnapshot.getTakenAt());

        // Backup the plugin directory...
        File snapshotDir = new File(pluginsTimeMachineDir, Long.toString(nowSnapshot.getTakenAt()));
        FileUtils.copyDirectory(pluginsDir, snapshotDir);

        // Save the manifest to the backup dir ...
        File manifestFile = new File(snapshotDir, PluginSnapshotManifest.MANIFEST_FILE_NAME);
        nowSnapshot.save(manifestFile);

        // And add nowSnapshot, making it the new latest...
        addSnapshotManifest(nowSnapshot);
    }

    public synchronized void takeSnapshot(@Nonnull PluginManager pluginManager) throws IOException {
        PluginSnapshotManifest nowSnapshot = PluginSnapshotManifest.takeSnapshot(pluginManager);
        PluginSnapshotManifest latestSnapshotBackup = getLatestSnapshot();

        if (!nowSnapshot.equals(latestSnapshotBackup)) {
            // Something has changed in the plugins. Backup the current
            // set of plugins.
            doNowBackup(nowSnapshot);
        } else {
            LOGGER.log(Level.INFO, "Current plugin set is the same as the last plugin snapshot taken "
                    + new Date(latestSnapshotBackup.getTakenAt()) + ". No new plugin snapshot backup needed.");
        }
    }
}
