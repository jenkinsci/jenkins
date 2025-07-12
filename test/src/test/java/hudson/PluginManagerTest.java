/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

package hudson;

import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import hudson.PluginManager.UberClassLoader;
import hudson.model.DownloadService;
import hudson.model.Hudson;
import hudson.model.RootAction;
import hudson.model.UpdateCenter;
import hudson.model.UpdateCenter.UpdateCenterJob;
import hudson.model.UpdateSite;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.FormValidation;
import hudson.util.PersistedList;
import jakarta.servlet.ServletException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import jenkins.ClassLoaderReflectionToolkit;
import jenkins.RestartRequiredException;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.htmlunit.AlertHandler;
import org.htmlunit.Page;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.Url;
import org.jvnet.hudson.test.recipes.WithPlugin;
import org.jvnet.hudson.test.recipes.WithPluginManager;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * @author Kohsuke Kawaguchi
 */
public class PluginManagerTest {

    @Rule public JenkinsRule r = PluginManagerUtil.newJenkinsRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    @Rule public FlagRule<Boolean> signatureCheck = new FlagRule<>(() -> DownloadService.signatureCheck, x -> DownloadService.signatureCheck = x);

    // a simple plugin with ideally no dependencies so the tests run quickly
    private static final String TEST_PLUGIN_NAME = "commons-lang3-api";
    private static final String TEST_PLUGIN = TEST_PLUGIN_NAME + ".jpi";
    private static final String TEST_PLUGIN_HPI = TEST_PLUGIN_NAME + ".hpi";

    /**
     * Manual submission form.
     */
    @Test public void uploadJpi() throws Exception {
        HtmlPage page = r.createWebClient().goTo("pluginManager/advanced");
        HtmlForm f = page.getFormByName("uploadPlugin");
        File dir = tmp.newFolder();
        File plugin = new File(dir, TEST_PLUGIN);
        FileUtils.copyURLToFile(getClass().getClassLoader().getResource("plugins/" + TEST_PLUGIN), plugin);
        f.getInputByName("name").setValue(plugin.getAbsolutePath());
        r.submit(f);

        assertTrue(new File(r.jenkins.getRootDir(), "plugins/" + TEST_PLUGIN).exists());
    }

    /**
     * Manual submission form.
     */
    @Test public void uploadHpi() throws Exception {
        HtmlPage page = r.createWebClient().goTo("pluginManager/advanced");
        HtmlForm f = page.getFormByName("uploadPlugin");
        File dir = tmp.newFolder();
        File plugin = new File(dir, TEST_PLUGIN_HPI);
        FileUtils.copyURLToFile(getClass().getClassLoader().getResource("plugins/" + TEST_PLUGIN), plugin);
        f.getInputByName("name").setValue(plugin.getAbsolutePath());
        r.submit(f);

        // uploaded legacy plugins get renamed to *.jpi
        assertTrue(new File(r.jenkins.getRootDir(), "plugins/" + TEST_PLUGIN).exists());
    }

    @Test public void deployJpiFromUrl() throws Exception {
        HtmlPage page = r.createWebClient().goTo("pluginManager/advanced");
        HtmlForm f = page.getFormByName("uploadPlugin");
        f.getInputByName("pluginUrl").setValue(Jenkins.get().getRootUrl() + "pluginManagerGetPlugin/" + TEST_PLUGIN);
        r.submit(f);

        assertTrue(new File(r.jenkins.getRootDir(), "plugins/" + TEST_PLUGIN).exists());
    }

    @TestExtension("deployJpiFromUrl")
    public static final class ReturnPluginJpiAction implements RootAction {

        @Override
        public String getIconFileName() {
            return "gear2.png";
        }

        @Override
        public String getDisplayName() {
            return "URL to retrieve a plugin jpi";
        }

        @Override
        public String getUrlName() {
            return "pluginManagerGetPlugin";
        }

        public void doDynamic(StaplerRequest2 staplerRequest, StaplerResponse2 staplerResponse) throws ServletException, IOException {
            staplerResponse.setContentType("application/octet-stream");
            staplerResponse.setStatus(200);
            staplerResponse.serveFile(staplerRequest,  PluginManagerTest.class.getClassLoader().getResource("plugins/" + TEST_PLUGIN));
        }
    }

    /**
     * Tests the effect of {@link WithPlugin}.
     */
    @WithPlugin(TEST_PLUGIN)
    @Test public void withRecipeJpi() {
        assertNotNull(r.jenkins.getPlugin("commons-lang3-api"));
    }

    /**
     * Tests the effect of {@link WithPlugin}.
     */
    @WithPlugin("legacy.hpi")
    @Test public void withRecipeHpi() {
        assertNotNull(r.jenkins.getPlugin("legacy"));
    }

    /**
     * Verifies that by the time {@link Plugin#start()} is called, uber classloader is fully functioning.
     * This is necessary as plugin start method can engage in XStream loading activities, and they should
     * resolve all the classes in the system (for example, a plugin X can define an extension point
     * other plugins implement, so when X loads its config it better sees all the implementations defined elsewhere)
     */
    @WithPlugin("htmlpublisher.jpi")
    @WithPluginManager(PluginManagerImpl_for_testUberClassLoaderIsAvailableDuringStart.class)
    @Test public void uberClassLoaderIsAvailableDuringStart() {
        assertTrue(((PluginManagerImpl_for_testUberClassLoaderIsAvailableDuringStart) r.jenkins.pluginManager).tested);
    }

    public static class PluginManagerImpl_for_testUberClassLoaderIsAvailableDuringStart extends LocalPluginManager {
        boolean tested;

        public PluginManagerImpl_for_testUberClassLoaderIsAvailableDuringStart(File rootDir) {
            super(rootDir);
        }

        @Override
        protected PluginStrategy createPluginStrategy() {
            return new ClassicPluginStrategy(this) {
                @Override
                public void startPlugin(PluginWrapper plugin) throws Exception {
                    tested = true;

                    // plugins should be already visible in the UberClassLoader
                    assertFalse(activePlugins.isEmpty());

                    assertNotNull(uberClassLoader.loadClass("htmlpublisher.HtmlPublisher"));

                    super.startPlugin(plugin);
                }
            };
        }
    }


    /**
     * Makes sure that thread context classloader isn't used by {@link UberClassLoader}, or else
     * infinite cycle ensues.
     */
    @Url("http://jenkins.361315.n4.nabble.com/channel-example-and-plugin-classes-gives-ClassNotFoundException-td3756092.html")
    @Test public void uberClassLoaderDoesntUseContextClassLoader() throws Exception {
        Thread t = Thread.currentThread();

        URLClassLoader ucl = new URLClassLoader(new URL[0], r.jenkins.pluginManager.uberClassLoader);

        ClassLoader old = t.getContextClassLoader();
        t.setContextClassLoader(ucl);
        try {
            assertThrows(ClassNotFoundException.class, () -> ucl.loadClass("No such class"));

            ucl.loadClass(Hudson.class.getName());
        } finally {
            t.setContextClassLoader(old);
        }
    }

    @Test public void installWithoutRestart() throws Exception {
        URL res = getClass().getClassLoader().getResource("plugins/htmlpublisher.jpi");
        File f = new File(r.jenkins.getRootDir(), "plugins/htmlpublisher.jpi");
        FileUtils.copyURLToFile(res, f);
        r.jenkins.pluginManager.dynamicLoad(f);

        Class c = r.jenkins.getPluginManager().uberClassLoader.loadClass("htmlpublisher.HtmlPublisher$DescriptorImpl");
        assertNotNull(r.jenkins.getDescriptorByType(c));
    }

    @Test public void prevalidateConfig() throws Exception {
        assumeFalse("TODO: Implement this test on Windows", Functions.isWindows());
        PersistedList<UpdateSite> sites = r.jenkins.getUpdateCenter().getSites();
        sites.clear();
        URL url = PluginManagerTest.class.getResource("/plugins/htmlpublisher-update-center.json");
        UpdateSite site = new UpdateSite(UpdateCenter.ID_DEFAULT, url.toString());
        sites.add(site);
        assertEquals(FormValidation.ok(), site.updateDirectly(false).get());
        assertNotNull(site.getData());
        assertEquals(Collections.emptyList(), r.jenkins.getPluginManager().prevalidateConfig(new ByteArrayInputStream("<whatever><runant plugin=\"ant@1.1\"/></whatever>".getBytes(StandardCharsets.UTF_8))));
        assertNull(r.jenkins.getPluginManager().getPlugin("htmlpublisher"));
        List<Future<UpdateCenterJob>> jobs = r.jenkins.getPluginManager().prevalidateConfig(new ByteArrayInputStream("<whatever><htmlpublisher plugin=\"htmlpublisher@0.7\"/></whatever>".getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, jobs.size());
        UpdateCenterJob job = jobs.get(0).get(); // blocks for completion
        assertEquals("InstallationJob", job.getType());
        UpdateCenter.InstallationJob ijob = (UpdateCenter.InstallationJob) job;
        assertEquals("htmlpublisher", ijob.plugin.name);
        assertNotNull(r.jenkins.getPluginManager().getPlugin("htmlpublisher"));
        // TODO restart scheduled (SuccessButRequiresRestart) after upgrade or Support-Dynamic-Loading: false
        // TODO dependencies installed or upgraded too
        // TODO required plugin installed but inactive
    }

    // plugin "depender" optionally depends on plugin "dependee".
    // they are written like this:
    // org.jenkinsci.plugins.dependencytest.dependee:
    //   public class Dependee {
    //     public static String getValue() {
    //       return "dependee";
    //     }
    //   }
    //
    //   public abstract class DependeeExtensionPoint implements ExtensionPoint {
    //   }
    //
    // org.jenkinsci.plugins.dependencytest.depender:
    //   public class Depender {
    //     public static String getValue() {
    //       if (Jenkins.get().getPlugin("dependee") != null) {
    //         return Dependee.getValue();
    //       }
    //       return "depender";
    //     }
    //   }
    //
    //   @Extension(optional=true)
    //   public class DependerExtension extends DependeeExtensionPoint {
    //   }


    /**
     * call org.jenkinsci.plugins.dependencytest.depender.Depender.getValue().
     */
    private String callDependerValue() throws Exception {
        Class<?> c = r.jenkins.getPluginManager().uberClassLoader.loadClass("org.jenkinsci.plugins.dependencytest.depender.Depender");
        Method m = c.getMethod("getValue");
        return (String) m.invoke(null);
    }

    /**
     * Load "dependee" and then load "depender".
     * Asserts that "depender" can access to "dependee".
     */
    @Test public void installDependingPluginWithoutRestart() throws Exception {
        // Load dependee.
        {
            dynamicLoad("dependee.hpi");
        }

        // before load depender, of course failed to call Depender.getValue()
        assertThrows(ClassNotFoundException.class, this::callDependerValue);

        // No extensions exist.
        assertTrue(r.jenkins.getExtensionList("org.jenkinsci.plugins.dependencytest.dependee.DependeeExtensionPoint").isEmpty());

        // Load depender.
        {
            dynamicLoad("depender.hpi");
        }

        // depender successfully accesses to dependee.
        assertEquals("dependee", callDependerValue());

        // Extension in depender is loaded.
        assertFalse(r.jenkins.getExtensionList("org.jenkinsci.plugins.dependencytest.dependee.DependeeExtensionPoint").isEmpty());
    }

    /**
     * Load "depender" and then load "dependee".
     * Asserts that "depender" can access to "dependee".
     */
    @Issue("JENKINS-19976")
    @Test public void installDependedPluginWithoutRestart() throws Exception {
        // Load depender.
        {
            dynamicLoad("depender.hpi");
        }

        // before load dependee, depender does not access to dependee.
        assertEquals("depender", callDependerValue());

        // before load dependee, of course failed to list extensions for dependee.
        assertThrows(ClassNotFoundException.class, () -> r.jenkins.getExtensionList("org.jenkinsci.plugins.dependencytest.dependee.DependeeExtensionPoint"));
        // Extension extending a dependee class can't be loaded either
        assertThrows(NoClassDefFoundError.class, () -> r.jenkins.getExtensionList("org.jenkinsci.plugins.dependencytest.depender.DependerExtension"));

        // Load dependee.
        {
            dynamicLoad("dependee.hpi");
        }

        // (MUST) Not throws an exception
        // (SHOULD) depender successfully accesses to dependee.
        assertEquals("dependee", callDependerValue());

        // Extensions in depender are loaded.
        assertEquals(1, r.jenkins.getExtensionList("org.jenkinsci.plugins.dependencytest.depender.DependerExtension").size());
    }

    @Issue("JENKINS-21486")
    @Test public void installPluginWithObsoleteDependencyFails() throws Exception {
        // Load dependee 0.0.1.
        {
            dynamicLoad("dependee.hpi");
        }

        // Load mandatory-depender 0.0.2, depending on dependee 0.0.2
        assertThrows(IOException.class, () -> dynamicLoad("mandatory-depender-0.0.2.hpi"));
    }

    @Issue("JENKINS-21486")
    @Test public void installPluginWithDisabledOptionalDependencySucceeds() throws Exception {
        // Load dependee 0.0.2.
        {
            dynamicLoadAndDisable("dependee-0.0.2.hpi");
        }

        // Load depender 0.0.2, depending optionally on dependee 0.0.2
        {
            dynamicLoad("depender-0.0.2.hpi");
        }

        // dependee is not loaded so we cannot list any extension for it.
        assertThrows(ClassNotFoundException.class, () -> r.jenkins.getExtensionList("org.jenkinsci.plugins.dependencytest.dependee.DependeeExtensionPoint"));
    }

    @Issue("JENKINS-21486")
    @Test public void installPluginWithDisabledDependencyFails() throws Exception {
        // Load dependee 0.0.2.
        {
            dynamicLoadAndDisable("dependee-0.0.2.hpi");
        }

        // Load mandatory-depender 0.0.2, depending on dependee 0.0.2
        assertThrows(IOException.class, () -> dynamicLoad("mandatory-depender-0.0.2.hpi"));
    }

    @Issue("JENKINS-68194")
    @WithPlugin("dependee.hpi")
    @Test public void clearDisabledStatusAfterUninstall() throws Exception {
        PluginWrapper pw = r.jenkins.pluginManager.getPlugin("dependee");
        assertNotNull(pw);

        pw.doMakeDisabled();
        pw.doDoUninstall();

        File disabledHpi = new File(r.jenkins.getRootDir(), "plugins/dependee.hpi.disabled");
        assertFalse(disabledHpi.exists());  // `.disabled` file should be deleted after uninstall
    }

    @Issue("JENKINS-21486")
    @Test public void installPluginWithObsoleteOptionalDependencyFails() throws Exception {
        // Load dependee 0.0.1.
        {
            dynamicLoad("dependee.hpi");
        }

        // Load depender 0.0.2, depending optionally on dependee 0.0.2
        assertThrows(IOException.class, () -> dynamicLoad("depender-0.0.2.hpi"));
    }

    @Issue("JENKINS-12753")
    @WithPlugin("htmlpublisher.jpi")
    @Test public void dynamicLoadRestartRequiredException() throws Exception {
        File jpi = new File(r.jenkins.getRootDir(), "plugins/htmlpublisher.jpi");
        assertTrue(jpi.isFile());
        FileUtils.touch(jpi);
        File timestamp = new File(r.jenkins.getRootDir(), "plugins/htmlpublisher/.timestamp2");
        assertTrue(timestamp.isFile());
        long lastMod = timestamp.lastModified();
        assertThrows(RestartRequiredException.class, () -> r.jenkins.getPluginManager().dynamicLoad(jpi));
        assertEquals("should not have tried to delete & unpack", lastMod, timestamp.lastModified());
    }

    @WithPlugin("htmlpublisher.jpi")
    @Test public void pluginListJSONApi() throws IOException {
        JSONObject response = r.getJSON("pluginManager/plugins").getJSONObject();

        // Check that the basic API endpoint invocation works.
        assertEquals("ok", response.getString("status"));
        JSONArray data = response.getJSONArray("data");
        assertThat(data, not(empty()));

        // Check that there was some data in the response and that the first entry
        // at least had some of the expected fields.
        JSONObject pluginInfo = data.getJSONObject(0);
        assertNotNull(pluginInfo.getString("name"));
        assertNotNull(pluginInfo.getString("title"));
        assertNotNull(pluginInfo.getString("dependencies"));
    }

    @Issue("JENKINS-41684")
    @Test
    public void requireSystemDuringLoad() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy());
        try (ACLContext context = ACL.as2(User.getById("underprivileged", true).impersonate2())) {
            dynamicLoad("require-system-during-load.hpi");
        }
    }


    @Test
    @Issue("JENKINS-59775")
    public void requireSystemDuringStart() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy());
        String pluginShortName = "require-system-during-load";
        dynamicLoad(pluginShortName + ".hpi");
        try (ACLContext context = ACL.as2(User.getById("underprivileged", true).impersonate2())) {
            r.jenkins.pluginManager.start(List.of(r.jenkins.pluginManager.getPlugin(pluginShortName)));
        }
    }

    @Issue("JENKINS-61071")
    @Test
    public void requireSystemInInitializer() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy());
        String pluginShortName = "require-system-in-initializer";
        dynamicLoad(pluginShortName + ".jpi");
        try (ACLContext context = ACL.as2(User.getById("underprivileged", true).impersonate2())) {
            r.jenkins.pluginManager.start(List.of(r.jenkins.pluginManager.getPlugin(pluginShortName)));
        }
    }

    private void dynamicLoad(String plugin) throws IOException, InterruptedException, RestartRequiredException {
        PluginManagerUtil.dynamicLoad(plugin, r.jenkins);
    }

    private void dynamicLoadAndDisable(String plugin) throws IOException, InterruptedException, RestartRequiredException {
        PluginManagerUtil.dynamicLoad(plugin, r.jenkins, true);
    }

    @Test public void uploadDependencyResolution() throws Exception {
        assumeFalse("TODO: Implement this test for Windows", Functions.isWindows());
        PersistedList<UpdateSite> sites = r.jenkins.getUpdateCenter().getSites();
        sites.clear();
        URL url = PluginManagerTest.class.getResource("/plugins/upload-test-update-center.json");
        UpdateSite site = new UpdateSite(UpdateCenter.ID_DEFAULT, url.toString());
        sites.add(site);

        assertEquals(FormValidation.ok(), site.updateDirectly(false).get());
        assertNotNull(site.getData());

        // neither of the following plugins should be installed
        assertNull(r.jenkins.getPluginManager().getPlugin("mandatory-depender"));
        assertNull(r.jenkins.getPluginManager().getPlugin("dependee"));

        HtmlPage page = r.createWebClient().goTo("pluginManager/advanced");
        HtmlForm f = page.getFormByName("uploadPlugin");
        File dir = tmp.newFolder();
        File plugin = new File(dir, "mandatory-depender-0.0.2.hpi");
        FileUtils.copyURLToFile(getClass().getClassLoader().getResource("plugins/mandatory-depender-0.0.2.hpi"), plugin);
        f.getInputByName("name").setValue(plugin.getAbsolutePath());
        r.submit(f);

        assertThat(r.jenkins.getUpdateCenter().getJobs(), not(empty()));

        // wait for all the download jobs to complete
        boolean done = true;
        boolean passed = true;
        do {
            Thread.sleep(100);
            done = true;
            for (UpdateCenterJob job : r.jenkins.getUpdateCenter().getJobs()) {
                if (job instanceof UpdateCenter.DownloadJob j) {
                    assertFalse(j.status instanceof UpdateCenter.DownloadJob.Failure);
                    done &= !(j.status instanceof UpdateCenter.DownloadJob.Pending ||
                            j.status instanceof UpdateCenter.DownloadJob.Installing);
                }
            }
        } while (!done);

        // the files get renamed to .jpi
        assertTrue(new File(r.jenkins.getRootDir(), "plugins/mandatory-depender.jpi").exists());
        assertTrue(new File(r.jenkins.getRootDir(), "plugins/dependee.jpi").exists());

        // now the other plugins should have been found as dependencies and downloaded
        assertNotNull(r.jenkins.getPluginManager().getPlugin("mandatory-depender"));
        assertNotNull(r.jenkins.getPluginManager().getPlugin("dependee"));
    }

    @Issue("JENKINS-44898")
    @WithPlugin("plugin-first.hpi")
    @Test
    public void findResourceForPluginFirstClassLoader() {
        PluginWrapper w = r.jenkins.getPluginManager().getPlugin("plugin-first");
        assertNotNull(w);

        URL fromPlugin = w.classLoader.getResource("org/jenkinsci/plugins/pluginfirst/HelloWorldBuilder/config.jelly");
        assertNotNull(fromPlugin);

        // This is how UberClassLoader.findResource functions.
        URL fromToolkit = ClassLoaderReflectionToolkit._findResource(w.classLoader, "org/jenkinsci/plugins/pluginfirst/HelloWorldBuilder/config.jelly");

        assertEquals(fromPlugin, fromToolkit);
    }

    @Test @Issue("JENKINS-64840")
    @WithPlugin({"mandatory-depender-0.0.2.hpi", "dependee-0.0.2.hpi", "depender-0.0.2.hpi"})
    public void getPluginsSortedByTitle() throws Exception {
        List<String> installedPlugins = r.jenkins.getPluginManager().getPluginsSortedByTitle()
                .stream()
                .map(PluginWrapper::getDisplayName)
                .collect(Collectors.toUnmodifiableList());

        assertThat(installedPlugins, containsInRelativeOrder("dependee", "depender", "mandatory-depender"));
    }

    @Issue("JENKINS-62622")
    @Test
    @WithPlugin("legacy.hpi")
    public void doNotThrowWithUnknownPlugins() throws Exception {
        final UpdateCenter uc = Jenkins.get().getUpdateCenter();
        Assert.assertNull("This test requires the plugin with ID 'legacy' to not exist in update sites", uc.getPlugin("legacy"));

        // ensure data is loaded - probably unnecessary, but closer to reality
        Assert.assertSame(FormValidation.Kind.OK, uc.getSite("default").updateDirectlyNow().kind);
    }

    @Test @Issue("JENKINS-64840")
    public void searchMultipleUpdateSites() throws Exception {
        assumeFalse("TODO: Implement this test for Windows", Functions.isWindows());
        PersistedList<UpdateSite> sites = r.jenkins.getUpdateCenter().getSites();
        sites.clear();
        URL url = PluginManagerTest.class.getResource("/plugins/search-test-update-center1.json");
        UpdateSite site = new UpdateSite(UpdateCenter.ID_DEFAULT, url.toString());
        sites.add(site);
        assertEquals(FormValidation.ok(), site.updateDirectly(false).get());
        assertNotNull(site.getData());
        url = PluginManagerTest.class.getResource("/plugins/search-test-update-center2.json");
        site = new UpdateSite("secondary", url.toString());
        sites.add(site);
        final Future<FormValidation> future = site.updateDirectly(false);
        if (future != null) {
            assertEquals(FormValidation.ok(), future.get());
        }
        assertNotNull(site.getData());

        //Dummy plugin is found in the second site (should have worked before the fix)
        JenkinsRule.JSONWebResponse response = r.getJSON("pluginManager/pluginsSearch?query=dummy&limit=5");
        JSONObject json = response.getJSONObject();
        assertTrue(json.has("data"));
        JSONArray data = json.getJSONArray("data");
        assertEquals("Should be one search hit for dummy", 1, data.size());

        //token-macro plugin is found in the first site (didn't work before the fix)
        response = r.getJSON("pluginManager/pluginsSearch?query=token&limit=5");
        json = response.getJSONObject();
        assertTrue(json.has("data"));
        data = json.getJSONArray("data");
        assertEquals("Should be one search hit for token", 1, data.size());

        //hello-world plugin is found in the first site and hello-huston in the second (didn't work before the fix)
        response = r.getJSON("pluginManager/pluginsSearch?query=hello&limit=5");
        json = response.getJSONObject();
        assertTrue(json.has("data"));
        data = json.getJSONArray("data");
        assertEquals("Should be two search hits for hello", 2, data.size());
    }

    @Issue("JENKINS-70599")
    @Test
    public void installNecessaryPluginsTest() throws Exception {
        String jenkinsUrl = r.getURL().toString();

        // Define a cookie handler
        CookieHandler.setDefault(new CookieManager());
        HttpCookie sessionCookie = new HttpCookie("session", "test-session-cookie");
        sessionCookie.setPath("/");
        sessionCookie.setVersion(0);
        ((CookieManager) CookieHandler.getDefault())
                .getCookieStore()
                .add(new URI(jenkinsUrl), sessionCookie);

        // Initialize the cookie handler and get the crumb
        URI crumbIssuer = new URI(jenkinsUrl + "crumbIssuer/api/json");
        HttpRequest httpGet =
                HttpRequest.newBuilder()
                        .uri(crumbIssuer)
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(7))
                        .GET()
                        .build();
        HttpClient clientGet =
                HttpClient.newBuilder()
                        .cookieHandler(CookieHandler.getDefault())
                        .connectTimeout(Duration.ofSeconds(2))
                        .build();
        HttpResponse<String> responseGet = clientGet.send(httpGet, HttpResponse.BodyHandlers.ofString());
        assertEquals("Bad response for crumb issuer", 200, responseGet.statusCode());
        String body = responseGet.body();
        assertTrue("crumbRequestField not in response", body.contains("crumbRequestField"));
        org.json.JSONObject jsonObject = new org.json.JSONObject(body);
        String crumb = (String) jsonObject.get("crumb");
        String crumbRequestField = (String) jsonObject.get("crumbRequestField");

        // Call installNecessaryPlugins XML API for git client plugin 4.0.0 with crumb
        URI installNecessaryPlugins = new URI(jenkinsUrl + "pluginManager/installNecessaryPlugins");
        String xmlRequest = "<jenkins><install plugin=\"git-client@4.0.0\"></install></jenkins>";
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(installNecessaryPlugins)
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/xml")
                        .header(crumbRequestField, crumb)
                        .POST(HttpRequest.BodyPublishers.ofString(xmlRequest))
                        .build();
        HttpClient client =
                HttpClient.newBuilder()
                        .cookieHandler(CookieHandler.getDefault())
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .connectTimeout(Duration.ofSeconds(2))
                        .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // Redirect reported 404 before bug was fixed
        assertEquals("Bad response for installNecessaryPlugins", 200, response.statusCode());
    }

    @Test
    @Issue("SECURITY-2823")
    public void verifyUploadedPluginPermission() throws Exception {
        assumeFalse(Functions.isWindows());

        HtmlPage page = r.createWebClient().goTo("pluginManager/advanced");
        HtmlForm f = page.getFormByName("uploadPlugin");
        File dir = tmp.newFolder();
        File plugin = new File(dir, TEST_PLUGIN);
        FileUtils.copyURLToFile(Objects.requireNonNull(getClass().getClassLoader().getResource("plugins/" + TEST_PLUGIN)), plugin);
        f.getInputByName("name").setValue(plugin.getAbsolutePath());
        r.submit(f);

        File filesRef = Files.createTempFile("tmp", ".tmp").toFile();
        File filesTmpDir = filesRef.getParentFile();
        filesRef.deleteOnExit();

        final Set<PosixFilePermission>[] filesPermission = new Set[]{new HashSet<>()};
        await().pollInterval(250, TimeUnit.MILLISECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> {
                    Optional<File> lastUploadedPluginDir = Arrays.stream(Objects.requireNonNull(
                                    filesTmpDir.listFiles((file, fileName) ->
                                            fileName.startsWith("uploadDir")))).
                            max(Comparator.comparingLong(File::lastModified));
                    if (lastUploadedPluginDir.isPresent()) {
                        filesPermission[0] = Files.getPosixFilePermissions(lastUploadedPluginDir.get().toPath(), LinkOption.NOFOLLOW_LINKS);
                        Optional<File> pluginFile = Arrays.stream(Objects.requireNonNull(
                                        lastUploadedPluginDir.get().listFiles((file, fileName) ->
                                                fileName.startsWith("uploaded")))).
                                max(Comparator.comparingLong(File::lastModified));
                        assertTrue(pluginFile.isPresent());
                        return true;
                    } else {
                        return false;
                    }
                });
        assertEquals(EnumSet.of(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE), filesPermission[0]);
    }

    @Test
    @Issue("SECURITY-3037")
    public void noInjectionOnAvailablePluginsPage() throws Exception {
        DownloadService.signatureCheck = false;
        Jenkins.get().getUpdateCenter().getSites().clear();
        UpdateSite us = new UpdateSite("Security3037", Jenkins.get().getRootUrl() + "security3037UpdateCenter/security3037-update-center.json");
        Jenkins.get().getUpdateCenter().getSites().add(us);

        try (JenkinsRule.WebClient wc = r.createWebClient()) {
            HtmlPage p = wc.goTo("pluginManager");

            AlertHandlerImpl alertHandler = new AlertHandlerImpl();
            wc.setAlertHandler(alertHandler);

            PluginManagerUtil.getCheckForUpdatesButton(p).click();
            HtmlPage available = wc.goTo("pluginManager/available");
            assertTrue(available.querySelector(".jenkins-alert-danger")
                    .getTextContent().contains("This plugin is built for Jenkins 9999999"));
            wc.waitForBackgroundJavaScript(100);

            HtmlAnchor anchor = available.querySelector(".jenkins-table__link");
            anchor.click(true, false, false);
            wc.waitForBackgroundJavaScript(100);
            assertTrue(alertHandler.messages.isEmpty());
        }
    }

    @Test
    @Issue("SECURITY-3072")
    public void verifyUploadedPluginFromURLPermission() throws Exception {
        assumeFalse(Functions.isWindows());

        HtmlPage page = r.createWebClient().goTo("pluginManager/advanced");
        HtmlForm f = page.getFormByName("uploadPlugin");
        f.getInputByName("pluginUrl").setValue(Jenkins.get().getRootUrl() + "pluginManagerGetPlugin/" + TEST_PLUGIN);
        r.submit(f);

        File filesRef = Files.createTempFile("tmp", ".tmp").toFile();
        File filesTmpDir = filesRef.getParentFile();
        filesRef.deleteOnExit();

        final Set<PosixFilePermission>[] filesPermission = new Set[]{new HashSet<>()};
        await().pollInterval(250, TimeUnit.MILLISECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> {
                    Optional<File> lastUploadedPluginDir = Arrays.stream(Objects.requireNonNull(
                                    filesTmpDir.listFiles((file, fileName) ->
                                            fileName.startsWith("uploadDir")))).
                            max(Comparator.comparingLong(File::lastModified));
                    if (lastUploadedPluginDir.isPresent()) {
                        filesPermission[0] = Files.getPosixFilePermissions(lastUploadedPluginDir.get().toPath(), LinkOption.NOFOLLOW_LINKS);
                        Optional<File> pluginFile = Arrays.stream(Objects.requireNonNull(
                                        lastUploadedPluginDir.get().listFiles((file, fileName) ->
                                                fileName.startsWith("uploaded")))).
                                max(Comparator.comparingLong(File::lastModified));
                        assertTrue(pluginFile.isPresent());
                        return true;
                    } else {
                        return false;
                    }
                });
        assertEquals(EnumSet.of(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE), filesPermission[0]);
    }

    static class AlertHandlerImpl implements AlertHandler {
        List<String> messages = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void handleAlert(final Page page, final String message) {
            messages.add(message);
        }
    }

    @TestExtension("noInjectionOnAvailablePluginsPage")
    public static final class Security3037UpdateCenter implements RootAction {

        @Override
        public String getIconFileName() {
            return "gear2.png";
        }

        @Override
        public String getDisplayName() {
            return "security-3037-update-center";
        }

        @Override
        public String getUrlName() {
            return "security3037UpdateCenter";
        }

        public void doDynamic(StaplerRequest2 staplerRequest, StaplerResponse2 staplerResponse) throws ServletException, IOException {
            staplerResponse.setContentType("application/json");
            staplerResponse.setStatus(200);
            staplerResponse.serveFile(staplerRequest, PluginManagerTest.class.getResource("/plugins/security3037-update-center.json"));
        }
    }

    @TestExtension("verifyUploadedPluginFromURLPermission")
    public static final class Security3072JpiAction implements RootAction {

        @Override
        public String getIconFileName() {
            return "gear2.png";
        }

        @Override
        public String getDisplayName() {
            return "URL to retrieve a plugin jpi";
        }

        @Override
        public String getUrlName() {
            return "pluginManagerGetPlugin";
        }

        public void doDynamic(StaplerRequest2 staplerRequest, StaplerResponse2 staplerResponse) throws ServletException, IOException {
            staplerResponse.setContentType("application/octet-stream");
            staplerResponse.setStatus(200);
            staplerResponse.serveFile(staplerRequest,  PluginManagerTest.class.getClassLoader().getResource("plugins/" + TEST_PLUGIN));
        }
    }

}
