package jenkins.timemachine;

import hudson.PluginManagerUtil;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class PluginManagerTimeMachineTest {

    @Rule
    public JenkinsRule jenkinsRule = PluginManagerUtil.newJenkinsRule();

    @Test
    public void test_doSnapshots() throws IOException {
        JSONObject response = jenkinsRule.getJSON("pluginManager/timeMachine/snapshots").getJSONObject();

        Assert.assertEquals("ok", response.get("status"));
        Assert.assertEquals(1, response.getJSONArray("data").size());
    }

    @Test
    public void test_doSnapshotChanges() throws IOException {
        PluginManagerTimeMachine timeMachine = jenkinsRule.getInstance().getPluginManager().getTimeMachine();
        List<PluginSnapshotManifest> snapshots = timeMachine.getSnapshotManifests();

        // clear the real one manifest (easier for the test) and add a
        // few test ones.
        snapshots.clear();

        PluginSnapshotManifest manifest1 = new PluginSnapshotManifest().setTakenAt(1L);
        PluginSnapshotManifest manifest2 = new PluginSnapshotManifest().setTakenAt(2L);

        manifest1.addSnapshot(new PluginSnapshot().setPluginId("a") .setVersion("1.0").setEnabled(true));
        manifest1.addSnapshot(new PluginSnapshot().setPluginId("b") .setVersion("1.0").setEnabled(true));
        manifest1.addSnapshot(new PluginSnapshot().setPluginId("c") .setVersion("1.0").setEnabled(true));
        manifest1.addSnapshot(new PluginSnapshot().setPluginId("d") .setVersion("1.0").setEnabled(true));
        manifest1.addSnapshot(new PluginSnapshot().setPluginId("e") .setVersion("1.0").setEnabled(true));

        // Disable b
        // Upgrade c
        // Downgrade d
        // Uninstall e
        // Install f
        manifest2.addSnapshot(new PluginSnapshot().setPluginId("a") .setVersion("1.0").setEnabled(true));
        manifest2.addSnapshot(new PluginSnapshot().setPluginId("b") .setVersion("1.0").setEnabled(false));
        manifest2.addSnapshot(new PluginSnapshot().setPluginId("c") .setVersion("1.1").setEnabled(true));
        manifest2.addSnapshot(new PluginSnapshot().setPluginId("d") .setVersion("0.9").setEnabled(true));
        manifest2.addSnapshot(new PluginSnapshot().setPluginId("f") .setVersion("1.0").setEnabled(true));

        timeMachine.addSnapshotManifest(manifest1);
        timeMachine.addSnapshotManifest(manifest2);

        JSONObject response = jenkinsRule.getJSON("pluginManager/timeMachine/snapshotChanges?from=1&to=2").getJSONObject();
        JSONArray pluginChangesJson = response.getJSONArray("data");
        List<PluginChanges> pluginChanges = new ArrayList<>();

        Assert.assertEquals(5, pluginChangesJson.size());

        JSONObject bChanges = pluginChangesJson.getJSONObject(0);
        Assert.assertEquals("b", bChanges.getString("pluginId"));
        Assert.assertEquals(1, bChanges.getJSONArray("changes").size());
        Assert.assertEquals("disabled", bChanges.getJSONArray("changes").getJSONObject(0).getString("type"));

        JSONObject cChanges = pluginChangesJson.getJSONObject(1);
        Assert.assertEquals("c", cChanges.getString("pluginId"));
        Assert.assertEquals(1, cChanges.getJSONArray("changes").size());
        Assert.assertEquals("upgraded", cChanges.getJSONArray("changes").getJSONObject(0).getString("type"));
        Assert.assertEquals("1.0", cChanges.getJSONArray("changes").getJSONObject(0).getString("from"));
        Assert.assertEquals("1.1", cChanges.getJSONArray("changes").getJSONObject(0).getString("to"));

        JSONObject dChanges = pluginChangesJson.getJSONObject(2);
        System.out.println(dChanges);
        Assert.assertEquals("d", dChanges.getString("pluginId"));
        Assert.assertEquals(1, dChanges.getJSONArray("changes").size());
        Assert.assertEquals("downgraded", dChanges.getJSONArray("changes").getJSONObject(0).getString("type"));
        Assert.assertEquals("1.0", dChanges.getJSONArray("changes").getJSONObject(0).getString("from"));
        Assert.assertEquals("0.9", dChanges.getJSONArray("changes").getJSONObject(0).getString("to"));

        JSONObject eChanges = pluginChangesJson.getJSONObject(3);
        Assert.assertEquals("e", eChanges.getString("pluginId"));
        Assert.assertEquals(1, eChanges.getJSONArray("changes").size());
        Assert.assertEquals("uninstalled", eChanges.getJSONArray("changes").getJSONObject(0).getString("type"));

        JSONObject fChanges = pluginChangesJson.getJSONObject(4);
        Assert.assertEquals("f", fChanges.getString("pluginId"));
        Assert.assertEquals(1, fChanges.getJSONArray("changes").size());
        Assert.assertEquals("installed", fChanges.getJSONArray("changes").getJSONObject(0).getString("type"));
    }
}
