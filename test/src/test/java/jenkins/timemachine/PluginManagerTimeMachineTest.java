package jenkins.timemachine;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import hudson.PluginManagerUtil;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import javax.servlet.ServletRequest;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
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

    @Test
    public void test_Rollback() throws IOException {
        // Should not be configured to start with.
        assertRollbackConfig(null);

        // Configure it ...
        doSetRollback("1234");

        // Check the configure ...
        assertRollbackConfig("1234");

        // Reset and check again...
        doResetRollback();
        assertRollbackConfig(null);

        // Configure and check it again ...
        doSetRollback("1234");
        assertRollbackConfig("1234");

        // Run the rollback. Yes ... not actually going to roll anything back
        // in this test, but does test that the endpoint was executed.
        doRollback();
        // Once executed, the backend config should be reset.
        assertRollbackConfig(null);
    }

    private void assertRollbackConfig(String expected) throws IOException {
        JSONObject response = jenkinsRule.getJSON("pluginManager/timeMachine/rollbackConfig").getJSONObject();

        //System.out.println(response);
        Assert.assertEquals(expected, response.getJSONObject("data").optString("snapshotTakenAt", null));
    }

    private int doResetRollback() throws IOException {
        JenkinsRule.WebClient wc = jenkinsRule.createWebClient();

        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        WebRequest req = new WebRequest(
                new URL(jenkinsRule.jenkins.getRootUrl() + "pluginManager/timeMachine/resetRollback"),
                HttpMethod.DELETE
        );

        Page page = wc.getPage(req);
        WebResponse response = page.getWebResponse();

        return response.getStatusCode();
    }

    private int doRollback() throws IOException {
        JenkinsRule.WebClient wc = jenkinsRule.createWebClient();

        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        WebRequest req = new WebRequest(
                new URL(jenkinsRule.jenkins.getRootUrl() + "pluginManager/timeMachine/rollback"),
                HttpMethod.GET
        );

        Page page = wc.getPage(req);
        WebResponse response = page.getWebResponse();

        return response.getStatusCode();
    }

    private void doSetRollback(String toSnapshotTakenAt) throws IOException {
        doPost("pluginManager/timeMachine/setRollback", new NameValuePair("toSnapshotTakenAt", toSnapshotTakenAt));
    }

    private int doPost(String url, NameValuePair... params) throws IOException {
        return doPost(url, null, params);
    }

    private int doPost(String url, String data, NameValuePair... params) throws IOException {
        JenkinsRule.WebClient wc = jenkinsRule.createWebClient();

        WebRequest req = new WebRequest(
                new URL(jenkinsRule.jenkins.getRootUrl() + url),
                HttpMethod.POST
        );

        List<NameValuePair> requestParams  = new ArrayList<>();

        requestParams.add(getCrumbHeaderNVP());
        if (params != null && params.length > 0) {
            requestParams.addAll(Arrays.asList(params));
        }
        req.setRequestParameters(requestParams);

        if (data != null) {
            req.setRequestBody(data);
        }

        Page page = wc.getPage(req);
        WebResponse response = page.getWebResponse();

        return response.getStatusCode();
    }

    private NameValuePair getCrumbHeaderNVP() {
        return new NameValuePair(jenkinsRule.jenkins.getCrumbIssuer().getDescriptor().getCrumbRequestField(), jenkinsRule.jenkins.getCrumbIssuer().getCrumb((ServletRequest)null));
    }
}
