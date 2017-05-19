/*
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
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

import hudson.model.UpdateSite.Data;
import hudson.util.FormValidation;
import hudson.util.PersistedList;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.*;

import jenkins.security.UpdateSiteWarningsConfiguration;
import jenkins.security.UpdateSiteWarningsMonitor;
import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

public class UpdateSiteTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    private final String RELATIVE_BASE = "/_relative/";
    private Server server;
    private URL baseUrl;

    private String getResource(String resourceName) throws IOException {
        try {
            URL url = UpdateSiteTest.class.getResource(resourceName);
            return (url != null)?FileUtils.readFileToString(new File(url.toURI())):null;
        } catch(URISyntaxException e) {
            return null;
        }
    }

    /**
     * Startup a web server to access resources via HTTP.
     * @throws Exception 
     */
    @Before
    public void setUpWebServer() throws Exception {
        server = new Server();
        ServerConnector connector = new ServerConnector(server);
        server.addConnector(connector);
        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if (target.startsWith(RELATIVE_BASE)) {
                    target = target.substring(RELATIVE_BASE.length());
                }
                String responseBody = getResource(target);
                if (responseBody != null) {
                    baseRequest.setHandled(true);
                    response.setContentType("text/plain; charset=utf-8");
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.getOutputStream().write(responseBody.getBytes());
                }
            }
        });
        server.start();
        baseUrl = new URL("http", "localhost", connector.getLocalPort(), RELATIVE_BASE);
    }

    @After
    public void shutdownWebserver() throws Exception {
        server.stop();
    }
    
    @Test public void relativeURLs() throws Exception {
        PersistedList<UpdateSite> sites = j.jenkins.getUpdateCenter().getSites();
        sites.clear();
        URL url = new URL(baseUrl, "/plugins/tasks-update-center.json");
        UpdateSite site = new UpdateSite(UpdateCenter.ID_DEFAULT, url.toString());
        sites.add(site);
        assertEquals(FormValidation.ok(), site.updateDirectly(false).get());
        Data data = site.getData();
        assertNotNull(data);
        assertEquals(new URL(url, "jenkins.war").toString(), data.core.url);
        assertEquals(new HashSet<String>(Arrays.asList("tasks", "dummy")), data.plugins.keySet());
        assertEquals(new URL(url, "tasks.jpi").toString(), data.plugins.get("tasks").url);
        assertEquals("http://nowhere.net/dummy.hpi", data.plugins.get("dummy").url);

        UpdateSite.Plugin tasksPlugin = data.plugins.get("tasks");
        assertEquals("Wrong name of plugin found", "Task Scanner Plug-in", tasksPlugin.getDisplayName());
    }

    @Test public void updateDirectlyWithJson() throws Exception {
        UpdateSite us = new UpdateSite("default", new URL(baseUrl, "update-center.json").toExternalForm());
        assertNull(us.getPlugin("AdaptivePlugin"));
        assertEquals(FormValidation.ok(), us.updateDirectly(/* TODO the certificate is now expired, and downloading a fresh copy did not seem to help */false).get());
        assertNotNull(us.getPlugin("AdaptivePlugin"));
    }

    @Test public void lackOfDataDoesNotFailWarningsCode() throws Exception {
        assertNull("plugin data is not present", j.jenkins.getUpdateCenter().getSite("default").getData());

        // nothing breaking?
        j.jenkins.getExtensionList(UpdateSiteWarningsMonitor.class).get(0).getActivePluginWarningsByPlugin();
        j.jenkins.getExtensionList(UpdateSiteWarningsMonitor.class).get(0).getActiveCoreWarnings();
        j.jenkins.getExtensionList(UpdateSiteWarningsConfiguration.class).get(0).getAllWarnings();
    }

    @Test public void incompleteWarningsJson() throws Exception {
        PersistedList<UpdateSite> sites = j.jenkins.getUpdateCenter().getSites();
        sites.clear();
        URL url = new URL(baseUrl, "/plugins/warnings-update-center-malformed.json");
        UpdateSite site = new UpdateSite(UpdateCenter.ID_DEFAULT, url.toString());
        sites.add(site);
        assertEquals(FormValidation.ok(), site.updateDirectly(false).get());
        assertEquals("number of warnings", 7, site.getData().getWarnings().size());
        assertNotEquals("plugin data is present", Collections.emptyMap(), site.getData().plugins);
    }

    @LocalData
    @Test public void testCyclicDependencyDetection() throws Exception {
        PersistedList<UpdateSite> sites = j.jenkins.getUpdateCenter().getSites();
        UpdateSite.Plugin plugin = null;
        for (UpdateSite site : sites) {
            plugin = site.getPlugin("subversion");
            if (plugin != null) {
                break;
            }
        }
        try {
            plugin.getNeededDependenciesRequiredCore();
            plugin.isNeededDependenciesForNewerJenkins();
            plugin.isNeededDependenciesCompatibleWithInstalledVersion();
        } catch (Error e) {
            if (e instanceof StackOverflowError) {
                fail("Did not detect cyclic dependency");
            }
            fail("No exception should be thrown");
        }
    }

    @LocalData
    @Test public void testCheckTransientDependency() {
        UpdateSite.Plugin plugin = null;
        for (UpdateSite site : j.jenkins.getUpdateCenter().getSiteList()) {
            plugin = site.getPlugin("ssh-credentials");
            if (plugin != null) {
                break;
            }
        }
        try {
            plugin.checkForTransientDependency();
        } catch (Failure exception) {
            fail("Failure exception was thrown despite there is no cyclic dependency.");
        }
        plugin = null;
        for (UpdateSite site : j.jenkins.getUpdateCenter().getSiteList()) {
            plugin = site.getPlugin("workflow-scm-step");
            if (plugin != null) {
                break;
            }
        }
        try {
            plugin.checkForTransientDependency();
        } catch (Failure exception) {
            return;
        }
        fail("Cyclic dependency was not detected.");
    }

    @LocalData
    @Test public void testGetAllDependencies() {
        UpdateSite.Plugin plugin = null;
        for (UpdateSite site : j.jenkins.getUpdateCenter().getSiteList()) {
            plugin = site.getPlugin("subversion");
            if (plugin != null) {
                break;
            }
        }
        Set<UpdateSite.Plugin> plugins = plugin.getAllDependencies();
        TreeSet<String> pluginNames = new TreeSet<String>();
        for (UpdateSite.Plugin p : plugins) {
            pluginNames.add(p.name);
        }
        TreeSet<String> expectedPlugins = new TreeSet<String>();
        expectedPlugins.add("workflow-scm-step");
        expectedPlugins.add("mapdb-api");
        expectedPlugins.add("ssh-credentials");
        expectedPlugins.add("credentials");
        expectedPlugins.add("scm-api");
        expectedPlugins.add("workflow-step-api");
        expectedPlugins.add("git-client");
        expectedPlugins.add("icon-shim");
        assertEquals("Collection of all dependencies does not contains expected plugins", pluginNames,expectedPlugins);

    }

}
