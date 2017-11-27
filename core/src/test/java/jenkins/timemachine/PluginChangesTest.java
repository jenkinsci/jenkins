package jenkins.timemachine;

import jenkins.timemachine.pluginchange.Disabled;
import jenkins.timemachine.pluginchange.Downgraded;
import jenkins.timemachine.pluginchange.Enabled;
import jenkins.timemachine.pluginchange.Installed;
import jenkins.timemachine.pluginchange.PluginChange;
import jenkins.timemachine.pluginchange.Uninstalled;
import jenkins.timemachine.pluginchange.Upgraded;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class PluginChangesTest {

    @Test
    public void test_no_changes() {
        PluginSnapshotManifest manifest1 = new PluginSnapshotManifest().setTakenAt(1L);
        PluginSnapshotManifest manifest2 = new PluginSnapshotManifest().setTakenAt(2L);

        manifest1.addSnapshot(new PluginSnapshot().setPluginId("a") .setVersion("1.0").setEnabled(true));
        manifest1.addSnapshot(new PluginSnapshot().setPluginId("b") .setVersion("1.0").setEnabled(true));

        manifest2.addSnapshot(new PluginSnapshot().setPluginId("b") .setVersion("1.0").setEnabled(true));
        manifest2.addSnapshot(new PluginSnapshot().setPluginId("a") .setVersion("1.0").setEnabled(true));

        List<PluginChanges> changes = PluginChanges.changes(manifest1, manifest2);
        Assert.assertTrue(changes.isEmpty());
    }

    @Test
    public void test_installed_uninstalled() {
        PluginSnapshotManifest manifest1 = new PluginSnapshotManifest().setTakenAt(1L);
        PluginSnapshotManifest manifest2 = new PluginSnapshotManifest().setTakenAt(2L);

        manifest1.addSnapshot(new PluginSnapshot().setPluginId("a") .setVersion("1.0").setEnabled(true));

        manifest2.addSnapshot(new PluginSnapshot().setPluginId("a") .setVersion("1.0").setEnabled(true));
        manifest2.addSnapshot(new PluginSnapshot().setPluginId("b") .setVersion("1.0").setEnabled(true));

        // Installed ...
        List<PluginChanges> changes_1_to_2 = PluginChanges.changes(manifest1, manifest2);
        Assert.assertEquals(1, changes_1_to_2.size());
        Assert.assertEquals(1, changes_1_to_2.get(0).getPluginChanges().size());

        Installed installed = (Installed) changes_1_to_2.get(0).getPluginChanges().get(0);
        Assert.assertEquals("b", installed.getPlugin().getPluginId());
        Assert.assertEquals("1.0", installed.getPlugin().getVersion().toString());

        // Uninstalled (just compare in the other direction) ...
        List<PluginChanges> changes_2_to_1 = PluginChanges.changes(manifest2, manifest1);
        Assert.assertEquals(1, changes_2_to_1.size());
        Assert.assertEquals(1, changes_2_to_1.get(0).getPluginChanges().size());

        Uninstalled uninstalled = (Uninstalled) changes_2_to_1.get(0).getPluginChanges().get(0);
        Assert.assertEquals("b", uninstalled.getPlugin().getPluginId());
        Assert.assertEquals("1.0", uninstalled.getPlugin().getVersion().toString());
    }

    @Test
    public void test_upgraded_downgraded() {
        PluginSnapshotManifest manifest1 = new PluginSnapshotManifest().setTakenAt(1L);
        PluginSnapshotManifest manifest2 = new PluginSnapshotManifest().setTakenAt(2L);

        manifest1.addSnapshot(new PluginSnapshot().setPluginId("a") .setVersion("1.0").setEnabled(true));

        manifest2.addSnapshot(new PluginSnapshot().setPluginId("a") .setVersion("1.1").setEnabled(true));

        // Upgrade ...
        List<PluginChanges> changes_1_to_2 = PluginChanges.changes(manifest1, manifest2);
        Assert.assertEquals(1, changes_1_to_2.size());
        Assert.assertEquals(1, changes_1_to_2.get(0).getPluginChanges().size());

        Upgraded upgraded = (Upgraded) changes_1_to_2.get(0).getPluginChanges().get(0);
        Assert.assertEquals("a", upgraded.getPlugin().getPluginId());
        Assert.assertEquals("1.0", upgraded.getFrom().toString());
        Assert.assertEquals("1.1", upgraded.getTo().toString());

        // Downgraded (just compare in the other direction) ...
        List<PluginChanges> changes_2_to_1 = PluginChanges.changes(manifest2, manifest1);
        Assert.assertEquals(1, changes_2_to_1.size());
        Assert.assertEquals(1, changes_2_to_1.get(0).getPluginChanges().size());

        Downgraded downgraded = (Downgraded) changes_2_to_1.get(0).getPluginChanges().get(0);
        Assert.assertEquals("a", downgraded.getPlugin().getPluginId());
        Assert.assertEquals("1.1", downgraded.getFrom().toString());
        Assert.assertEquals("1.0", downgraded.getTo().toString());
    }

    @Test
    public void test_disabled_enabled() {
        PluginSnapshotManifest manifest1 = new PluginSnapshotManifest().setTakenAt(1L);
        PluginSnapshotManifest manifest2 = new PluginSnapshotManifest().setTakenAt(2L);

        manifest1.addSnapshot(new PluginSnapshot().setPluginId("a") .setVersion("1.0").setEnabled(true));

        manifest2.addSnapshot(new PluginSnapshot().setPluginId("a") .setVersion("1.0").setEnabled(false));

        // Disabled ...
        List<PluginChanges> changes_1_to_2 = PluginChanges.changes(manifest1, manifest2);
        Assert.assertEquals(1, changes_1_to_2.size());
        Assert.assertEquals(1, changes_1_to_2.get(0).getPluginChanges().size());

        Disabled disabled = (Disabled) changes_1_to_2.get(0).getPluginChanges().get(0);
        Assert.assertEquals("a", disabled.getPlugin().getPluginId());

        // Enabled (just compare in the other direction) ...
        List<PluginChanges> changes_2_to_1 = PluginChanges.changes(manifest2, manifest1);
        Assert.assertEquals(1, changes_2_to_1.size());
        Assert.assertEquals(1, changes_2_to_1.get(0).getPluginChanges().size());

        Enabled enabled = (Enabled) changes_2_to_1.get(0).getPluginChanges().get(0);
        Assert.assertEquals("a", enabled.getPlugin().getPluginId());
    }

    @Test
    public void test_upgraded_and_disabled() {
        PluginSnapshotManifest manifest1 = new PluginSnapshotManifest().setTakenAt(1L);
        PluginSnapshotManifest manifest2 = new PluginSnapshotManifest().setTakenAt(2L);

        manifest1.addSnapshot(new PluginSnapshot().setPluginId("a") .setVersion("1.0").setEnabled(true));

        manifest2.addSnapshot(new PluginSnapshot().setPluginId("a") .setVersion("1.1").setEnabled(false));

        // Upgrade & disabled ...
        List<PluginChanges> changes_1_to_2 = PluginChanges.changes(manifest1, manifest2);
        Assert.assertEquals(1, changes_1_to_2.size());
        Assert.assertEquals(2, changes_1_to_2.get(0).getPluginChanges().size());

        Upgraded upgraded = (Upgraded) changes_1_to_2.get(0).getPluginChanges().get(0);
        Assert.assertEquals("a", upgraded.getPlugin().getPluginId());
        Assert.assertEquals("1.0", upgraded.getFrom().toString());
        Assert.assertEquals("1.1", upgraded.getTo().toString());

        Disabled disabled = (Disabled) changes_1_to_2.get(0).getPluginChanges().get(1);
        Assert.assertEquals("a", disabled.getPlugin().getPluginId());
    }
}