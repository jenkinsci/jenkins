/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.xml.sax.SAXException;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@WithJenkins
class UpdateCenterPluginInstallTest {

    private JenkinsRule jenkinsRule;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        try {
            jenkinsRule = rule;
            jenkinsRule.jenkins.getUpdateCenter().getSite(UpdateCenter.ID_DEFAULT).updateDirectlyNow(false);
        } catch (Exception x) {
            assumeTrue(false, x.toString());
        }
        InetSocketAddress address = new InetSocketAddress("updates.jenkins-ci.org", 80);
        assumeFalse(address.isUnresolved(), "Unable to resolve updates.jenkins-ci.org. Skip test.");
    }

    @Test
    void test_installUnknownPlugin() throws IOException, SAXException {
        JenkinsRule.JSONWebResponse response = jenkinsRule.postJSON("pluginManager/installPlugins", buildInstallPayload("unknown_plugin_xyz"));
        JSONObject json = response.getJSONObject();

        assertEquals("error", json.get("status"));
        assertEquals("No such plugin: unknown_plugin_xyz", json.get("message"));
        assertEquals("error", json.get("status"));
        assertEquals("No such plugin: unknown_plugin_xyz", json.get("message"));
    }

    @Test
    void test_installKnownPlugins() throws IOException, SAXException {
        JenkinsRule.JSONWebResponse installResponse = jenkinsRule.postJSON("pluginManager/installPlugins", buildInstallPayload("changelog-history", "git"));
        JSONObject json = installResponse.getJSONObject();

        assertEquals("ok", json.get("status"));
        JSONObject data = json.getJSONObject("data");
        assertTrue(data.has("correlationId"));

        String correlationId = data.getString("correlationId");
        JSONObject installStatus = jenkinsRule.getJSON("updateCenter/installStatus?correlationId=" + correlationId).getJSONObject();
        assertEquals("ok", json.get("status"));
        JSONObject status = installStatus.getJSONObject("data");
        JSONArray states = status.getJSONArray("jobs");
        assertEquals(2, states.size(), states.toString());

        JSONObject pluginInstallState = states.getJSONObject(0);
        assertEquals("changelog-history", pluginInstallState.get("name"));
        pluginInstallState = states.getJSONObject(1);
        assertEquals("git", pluginInstallState.get("name"));
    }

    private JSONObject buildInstallPayload(String... plugins) {
        JSONObject payload = new JSONObject();
        payload.put("dynamicLoad", true);
        payload.put("plugins", JSONArray.fromObject(Arrays.asList(plugins)));
        return payload;
    }
}
