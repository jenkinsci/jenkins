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
import java.util.Arrays;
import java.util.HashSet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.junit.Assert.*;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.handler.AbstractHandler;

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
        SocketConnector connector = new SocketConnector();
        server.addConnector(connector);
        server.setHandler(new AbstractHandler() {
            public void handle(String target, HttpServletRequest request,
                    HttpServletResponse response, int dispatch) throws IOException,
                    ServletException {
                if (target.startsWith(RELATIVE_BASE)) {
                    target = target.substring(RELATIVE_BASE.length());
                }
                String responseBody = getResource(target);
                if (responseBody != null) {
                    HttpConnection.getCurrentConnection().getRequest().setHandled(true);
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
    
}
