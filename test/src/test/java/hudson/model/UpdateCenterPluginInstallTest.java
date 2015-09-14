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

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RandomlyFails;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Arrays;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class UpdateCenterPluginInstallTest {
    
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();
    
    public void setup() throws IOException {
        jenkinsRule.jenkins.getUpdateCenter().getSite(UpdateCenter.ID_DEFAULT).updateDirectlyNow(false);
    }
        
    @Test
    @RandomlyFails("Will fail if it can't connect to the UC")
    public void test_installUnknownPlugin() throws IOException, SAXException {
        setup();
        JenkinsRule.JSONWebResponse response = jenkinsRule.postJSON("/pluginManager/installPlugins", buildInstallPayload("unknown_plugin_xyz"));
        JSONObject json = response.getJSONObject();

        Assert.assertEquals("error", json.get("status"));
        Assert.assertEquals("No such plugin: unknown_plugin_xyz", json.get("message"));
        Assert.assertEquals("error", json.get("status"));
        Assert.assertEquals("No such plugin: unknown_plugin_xyz", json.get("message"));
    }

    @Test
    @RandomlyFails("Will fail if it can't connect to the UC")
    public void test_installKnownPlugins() throws IOException, SAXException {
        setup();
        JenkinsRule.JSONWebResponse installResponse = jenkinsRule.postJSON("/pluginManager/installPlugins", buildInstallPayload("changelog-history", "git"));
        JSONObject json = installResponse.getJSONObject();

        Assert.assertEquals("ok", json.get("status"));
        JSONObject data = json.getJSONObject("data");
        Assert.assertTrue(data.has("correlationId"));
        
        String correlationId = data.getString("correlationId");
        JSONObject installStatus = jenkinsRule.getJSON("updateCenter/installStatus?correlationId=" + correlationId).getJSONObject();
        Assert.assertEquals("ok", json.get("status"));
        JSONArray states = installStatus.getJSONArray("data");
        Assert.assertEquals(2, states.size());
        
        JSONObject pluginInstallState = states.getJSONObject(0);
        Assert.assertEquals("changelog-history", pluginInstallState.get("name"));
        pluginInstallState = states.getJSONObject(1);
        Assert.assertEquals("git", pluginInstallState.get("name"));
    }

    private JSONObject buildInstallPayload(String... plugins) {
        JSONObject payload = new JSONObject();
        payload.put("dynamicLoad", true);
        payload.put("plugins", JSONArray.fromObject(Arrays.asList(plugins)));
        return payload;
    }
}
