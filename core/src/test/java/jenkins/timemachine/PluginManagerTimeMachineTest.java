package jenkins.timemachine;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class PluginManagerTimeMachineTest {

    private File testRootDir = new File("./target/" + PluginManagerTimeMachineTest.class.getSimpleName());
    private File testPluginRootDir = new File(testRootDir, "/plugins");
    private PluginManagerTimeMachine pluginManagerTimeMachine;

    @Before
    public void setup() throws IOException {
        FileUtils.deleteDirectory(testRootDir);
        testRootDir.mkdirs();
        pluginManagerTimeMachine = new PluginManagerTimeMachine(testPluginRootDir);
    }

    @Test
    public void test_load() throws IOException {
        PluginSnapshotManifest manifest1 = new PluginSnapshotManifest().setTakenAt(1L);
        PluginSnapshotManifest manifest2 = new PluginSnapshotManifest().setTakenAt(2L);
        PluginSnapshotManifest manifest3 = new PluginSnapshotManifest().setTakenAt(3L);

        // Save them...
        save(manifest1);
        save(manifest2);
        save(manifest3);

        // Load them into the time machine instance...
        pluginManagerTimeMachine.loadSnapshotManifests();

        // The "latest" snapshot should be the one that has the most
        // recent "takenAt" timestamp i.e. manifest3 ...
        PluginSnapshotManifest latest = pluginManagerTimeMachine.getLatestSnapshot();
        Assert.assertNotEquals(manifest1.getTakenAt(), latest.getTakenAt());
        Assert.assertNotEquals(manifest2.getTakenAt(), latest.getTakenAt());
        Assert.assertEquals(manifest3.getTakenAt(), latest.getTakenAt());
    }

    private void save(PluginSnapshotManifest manifest) throws IOException {
        File manifestFile = new File(pluginManagerTimeMachine.getPluginsTimeMachineDir(), String.format("%d/%s", manifest.getTakenAt(), PluginSnapshotManifest.MANIFEST_FILE_NAME));
        manifest.save(manifestFile);
    }
}