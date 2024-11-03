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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import hudson.PluginWrapper;
import hudson.model.UpdateSite.Data;
import hudson.util.FormValidation;
import hudson.util.PersistedList;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import jenkins.model.Jenkins;
import jenkins.security.UpdateSiteWarningsConfiguration;
import jenkins.security.UpdateSiteWarningsMonitor;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class UpdateSiteTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    private final String RELATIVE_BASE = "/_relative/";
    private Server server;
    private URL baseUrl;

    private static String getResource(String resourceName) throws IOException {
        try {
            URL url = UpdateSiteTest.class.getResource(resourceName);
            if (url == null) {
                url = extract(resourceName);
            }
            return url != null ? Files.readString(Paths.get(url.toURI()), StandardCharsets.UTF_8) : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    public static URL extract(String resourceName) throws IOException {
        URL url = UpdateSiteTest.class.getResource(resourceName + ".zip");
        if (url == null) {
            return null;
        }
        try (InputStream is = url.openStream(); ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry zipEntry = zis.getNextEntry();
            assertEquals(resourceName, zipEntry.getName());
            Path result = Files.createTempFile(FilenameUtils.getBaseName(resourceName), FilenameUtils.getExtension(resourceName));
            result.toFile().deleteOnExit();
            Files.copy(zis, result, StandardCopyOption.REPLACE_EXISTING);
            assertNull(zis.getNextEntry());
            return result.toUri().toURL();
        }
    }

    /**
     * Startup a web server to access resources via HTTP.
     */
    @Before
    public void setUpWebServer() throws Exception {
        server = new Server();
        ServerConnector connector = new ServerConnector(server);
        server.addConnector(connector);
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) throws IOException {
                String target = request.getHttpURI().getPath();
                if (target.startsWith(RELATIVE_BASE)) {
                    target = target.substring(RELATIVE_BASE.length());
                }
                String responseBody = getResource(target);
                if (responseBody != null) {
                    response.getHeaders().add(HttpHeader.CONTENT_TYPE, "text/plain; charset=utf-8");
                    response.setStatus(HttpStatus.OK_200);
                    Content.Sink.write(response, true, responseBody, callback);
                    return true;
                }
                return false;
            }
        });
        server.start();
        baseUrl = new URI("http", null, "localhost", connector.getLocalPort(), RELATIVE_BASE, null, null).toURL();
    }

    @After
    public void shutdownWebserver() throws Exception {
        server.stop();
    }

    @Test public void relativeURLs() throws Exception {
        URL url = new URL(baseUrl, "/plugins/htmlpublisher-update-center.json");
        UpdateSite site = new UpdateSite(UpdateCenter.ID_DEFAULT, url.toString());
        overrideUpdateSite(site);
        assertEquals(FormValidation.ok(), site.updateDirectly(false).get());
        Data data = site.getData();
        assertNotNull(data);
        assertEquals(new URL(url, "jenkins.war").toString(), data.core.url);
        assertEquals(new HashSet<>(Arrays.asList("htmlpublisher", "dummy")), data.plugins.keySet());
        assertEquals(new URL(url, "htmlpublisher.jpi").toString(), data.plugins.get("htmlpublisher").url);
        assertEquals("http://nowhere.net/dummy.hpi", data.plugins.get("dummy").url);

        UpdateSite.Plugin htmlPublisherPlugin = data.plugins.get("htmlpublisher");
        assertEquals("Wrong name of plugin found", "HTML Publisher", htmlPublisherPlugin.getDisplayName());
    }

    @Test public void wikiUrlFromSingleSite() throws Exception {
        UpdateSite site = getUpdateSite("/plugins/htmlpublisher-update-center.json");
        overrideUpdateSite(site);
        PluginWrapper wrapper = buildPluginWrapper("dummy", "https://wiki.jenkins.io/display/JENKINS/dummy");
        assertEquals("https://plugins.jenkins.io/dummy", wrapper.getUrl());
    }

    @Test public void wikiUrlFromMoreSites() throws Exception {
        UpdateSite site = getUpdateSite("/plugins/htmlpublisher-update-center.json");
        UpdateSite alternativeSite = getUpdateSite("/plugins/alternative-update-center.json", "alternative");
        overrideUpdateSite(site, alternativeSite);
        // sites use different Wiki URL for dummy -> use URL from manifest
        PluginWrapper wrapper = buildPluginWrapper("dummy", "https://wiki.jenkins.io/display/JENKINS/dummy");
        assertEquals("https://wiki.jenkins.io/display/JENKINS/dummy", wrapper.getUrl());
        // sites use the same Wiki URL for HTML Publisher -> use it
        wrapper = buildPluginWrapper("htmlpublisher", "https://plugins.jenkins.io/htmlpublisher");
        assertEquals("https://plugins.jenkins.io/htmlpublisher", wrapper.getUrl());
        // only one site has it
        wrapper = buildPluginWrapper("foo", "https://wiki.jenkins.io/display/JENKINS/foo");
        assertEquals("https://plugins.jenkins.io/foo", wrapper.getUrl());
    }

    @Test public void updateDirectlyWithJson() throws Exception {
        UpdateSite us = new UpdateSite("default", new URL(baseUrl, "update-center.json").toExternalForm());
        assertNull(us.getPlugin("AdaptivePlugin"));
        assertEquals(FormValidation.ok(), us.updateDirectly(/* TODO the certificate is now expired, and downloading a fresh copy did not seem to help */false).get());
        assertNotNull(us.getPlugin("AdaptivePlugin"));
    }

    @Test public void lackOfDataDoesNotFailWarningsCode() {
        assertNull("plugin data is not present", j.jenkins.getUpdateCenter().getSite("default").getData());

        // nothing breaking?
        j.jenkins.getExtensionList(UpdateSiteWarningsMonitor.class).get(0).getActivePluginWarningsByPlugin();
        j.jenkins.getExtensionList(UpdateSiteWarningsMonitor.class).get(0).getActiveCoreWarnings();
        j.jenkins.getExtensionList(UpdateSiteWarningsConfiguration.class).get(0).getAllWarnings();
    }

    @Test public void incompleteWarningsJson() throws Exception {
        UpdateSite site = getUpdateSite("/plugins/warnings-update-center-malformed.json");
        overrideUpdateSite(site);
        assertEquals("number of warnings", 7, site.getData().getWarnings().size());
        assertNotEquals("plugin data is present", Collections.emptyMap(), site.getData().plugins);
    }

    @Issue("JENKINS-73760")
    @Test
    public void isLegacyDefault() {
        assertFalse("isLegacyDefault should be false with null id", new UpdateSite(null, "url").isLegacyDefault());
        assertFalse(
                "isLegacyDefault should be false when id is not default and url is http://updates.jenkins-ci.org/",
                new UpdateSite("dummy", "http://updates.jenkins-ci.org/").isLegacyDefault());
        assertTrue(
                "isLegacyDefault should be true when id is default and url is http://updates.jenkins-ci.org/",
                new UpdateSite(UpdateCenter.PREDEFINED_UPDATE_SITE_ID, "http://updates.jenkins-ci.org/").isLegacyDefault());
        assertFalse("isLegacyDefault should be false with null url", new UpdateSite(null, null).isLegacyDefault());
    }

    @Test public void getAvailables() throws Exception {
        UpdateSite site = getUpdateSite("/plugins/available-update-center.json");
        List<UpdateSite.Plugin> available = site.getAvailables();
        assertEquals("ALowTitle", available.get(0).getDisplayName());
        assertEquals("TheHighTitle", available.get(1).getDisplayName());
    }

    @Test public void deprecations() throws Exception {
        UpdateSite site = getUpdateSite("/plugins/deprecations-update-center.json");

        // present in plugins section of update-center.json, not deprecated
        UpdateSite.Plugin credentials = site.getPlugin("credentials");
        assertNotNull(credentials);
        assertFalse(credentials.isDeprecated());
        assertNull(credentials.getDeprecation());
        assertNull(site.getData().getDeprecations().get("credentials"));

        // present in plugins section of update-center.json, deprecated via label and top-level list
        UpdateSite.Plugin iconShim = site.getPlugin("icon-shim");
        assertNotNull(iconShim);
        assertTrue(iconShim.isDeprecated());
        assertEquals("https://www.jenkins.io/deprecations/icon-shim/", iconShim.getDeprecation().url);
        assertEquals("https://www.jenkins.io/deprecations/icon-shim/", site.getData().getDeprecations().get("icon-shim").url);

        // present in plugins section of update-center.json, deprecated via label only
        UpdateSite.Plugin tokenMacro = site.getPlugin("token-macro");
        assertNotNull(tokenMacro);
        assertTrue(tokenMacro.isDeprecated());
        assertEquals("https://wiki.jenkins-ci.org/display/JENKINS/Token+Macro+Plugin", tokenMacro.getDeprecation().url);
        assertEquals("https://wiki.jenkins-ci.org/display/JENKINS/Token+Macro+Plugin", site.getData().getDeprecations().get("token-macro").url);

        // not in plugins section of update-center.json, deprecated via top-level list
        UpdateSite.Plugin variant = site.getPlugin("variant");
        assertNull(variant);
        assertEquals("https://www.jenkins.io/deprecations/variant/", site.getData().getDeprecations().get("variant").url);
    }

    private UpdateSite getUpdateSite(String path) throws Exception {
        return getUpdateSite(path, UpdateCenter.ID_DEFAULT);
    }

    private UpdateSite getUpdateSite(String path, String id) throws Exception {
        URL url = new URL(baseUrl, path);
        UpdateSite site = new UpdateSite(id, url.toString());
        assertEquals(FormValidation.ok(), site.updateDirectly(false).get());
        return site;
    }

    private void overrideUpdateSite(UpdateSite... overrideSites) {
        PersistedList<UpdateSite> sites = j.jenkins.getUpdateCenter().getSites();
        sites.clear();
        sites.addAll(Arrays.asList(overrideSites));
    }

    private PluginWrapper buildPluginWrapper(String name, String wikiUrl) {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(new Attributes.Name("Short-Name"), name);
        attributes.put(new Attributes.Name("Plugin-Version"), "1.0.0");
        attributes.put(new Attributes.Name("Url"), wikiUrl);
        return new PluginWrapper(
                Jenkins.get().getPluginManager(),
                new File("/tmp/" + name + ".jpi"),
                manifest,
                null,
                null,
                new File("/tmp/" + name + ".jpi.disabled"),
                null,
                new ArrayList<>()
        );
    }
}
