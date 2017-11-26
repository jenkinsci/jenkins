package jenkins.timemachine;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class PluginSnapshotManifestTest {

    private File testPluginRootDir = new File("./target/" + PluginSnapshotManifestTest.class.getSimpleName() + "/plugins");

    @Before
    public void setup() {
        if (!testPluginRootDir.exists()) {
            testPluginRootDir.mkdirs();
        }
    }

    @Test
    public void test_equals_same() {
        PluginSnapshotManifest manifest1 = new PluginSnapshotManifest().setTakenAt(1L);
        PluginSnapshotManifest manifest2 = new PluginSnapshotManifest().setTakenAt(2L);

        manifest1.addSnapshot(new PluginSnapshot().setPluginId("a") .setVersion("1.0").setEnabled(true));
        manifest1.addSnapshot(new PluginSnapshot().setPluginId("b") .setVersion("1.0").setEnabled(true));

        manifest2.addSnapshot(new PluginSnapshot().setPluginId("b") .setVersion("1.0").setEnabled(true));
        manifest2.addSnapshot(new PluginSnapshot().setPluginId("a") .setVersion("1.0").setEnabled(true));

        Assert.assertEquals(manifest1, manifest2);
    }

    @Test
    public void test_not_equals_enabled() {
        PluginSnapshotManifest manifest1 = new PluginSnapshotManifest().setTakenAt(1L);
        PluginSnapshotManifest manifest2 = new PluginSnapshotManifest().setTakenAt(2L);

        manifest1.addSnapshot(new PluginSnapshot().setPluginId("a") .setVersion("1.0").setEnabled(true));
        manifest1.addSnapshot(new PluginSnapshot().setPluginId("b") .setVersion("1.0").setEnabled(true));

        // Set one of the plugins to be disabled
        manifest2.addSnapshot(new PluginSnapshot().setPluginId("b") .setVersion("1.0").setEnabled(false));
        manifest2.addSnapshot(new PluginSnapshot().setPluginId("a") .setVersion("1.0").setEnabled(true));

        Assert.assertNotEquals(manifest1, manifest2);
    }

    @Test
    public void test_not_equals_version() {
        PluginSnapshotManifest manifest1 = new PluginSnapshotManifest().setTakenAt(1L);
        PluginSnapshotManifest manifest2 = new PluginSnapshotManifest().setTakenAt(2L);

        manifest1.addSnapshot(new PluginSnapshot().setPluginId("a") .setVersion("1.0").setEnabled(true));
        manifest1.addSnapshot(new PluginSnapshot().setPluginId("b") .setVersion("1.0").setEnabled(true));

        // Set one of the plugins to a new version
        manifest2.addSnapshot(new PluginSnapshot().setPluginId("b") .setVersion("2.1").setEnabled(true));
        manifest2.addSnapshot(new PluginSnapshot().setPluginId("a") .setVersion("1.0").setEnabled(true));

        Assert.assertNotEquals(manifest1, manifest2);
    }

    @Test
    public void test_not_equals_num_plugins() {
        PluginSnapshotManifest manifest1 = new PluginSnapshotManifest().setTakenAt(1L);
        PluginSnapshotManifest manifest2 = new PluginSnapshotManifest().setTakenAt(2L);

        manifest1.addSnapshot(new PluginSnapshot().setPluginId("a") .setVersion("1.0").setEnabled(true));
        manifest1.addSnapshot(new PluginSnapshot().setPluginId("b") .setVersion("1.0").setEnabled(true));

        // Add another plugin e.g. after an install
        manifest2.addSnapshot(new PluginSnapshot().setPluginId("b") .setVersion("1.0").setEnabled(true));
        manifest2.addSnapshot(new PluginSnapshot().setPluginId("a") .setVersion("1.0").setEnabled(true));
        manifest2.addSnapshot(new PluginSnapshot().setPluginId("c") .setVersion("1.0").setEnabled(true));

        Assert.assertNotEquals(manifest1, manifest2);
    }

    @Test
    public void test_not_equals_diff_plugins() {
        PluginSnapshotManifest manifest1 = new PluginSnapshotManifest().setTakenAt(1L);
        PluginSnapshotManifest manifest2 = new PluginSnapshotManifest().setTakenAt(2L);

        manifest1.addSnapshot(new PluginSnapshot().setPluginId("a") .setVersion("1.0").setEnabled(true));
        manifest1.addSnapshot(new PluginSnapshot().setPluginId("b") .setVersion("1.0").setEnabled(true));

        // Add plugin "c" and remove plugin "b". So same number of plugins, but different plugins.
        manifest2.addSnapshot(new PluginSnapshot().setPluginId("a") .setVersion("1.0").setEnabled(true));
        manifest2.addSnapshot(new PluginSnapshot().setPluginId("c") .setVersion("1.0").setEnabled(true));

        Assert.assertNotEquals(manifest1, manifest2);
    }

    @Test
    public void test_save_load() throws IOException {
        PluginSnapshotManifest manifest1 = new PluginSnapshotManifest().setTakenAt(System.currentTimeMillis());
        PluginSnapshotManifest manifest2;

        manifest1.addSnapshot(new PluginSnapshot().setPluginId("a") .setVersion("1.0").setEnabled(true));
        manifest1.addSnapshot(new PluginSnapshot().setPluginId("b") .setVersion("1.0").setEnabled(true));

        // Save it ...
        File manifestFile = new File(testPluginRootDir, PluginSnapshotManifest.MANIFEST_FILE_NAME);
        manifest1.save(manifestFile);

        // Load it ...
        manifest2 = PluginSnapshotManifest.load(manifestFile);

        // Should be the same ...
        Assert.assertEquals(manifest1, manifest2);
        Assert.assertEquals(manifest1.getTakenAt(), manifest2.getTakenAt());
    }
}