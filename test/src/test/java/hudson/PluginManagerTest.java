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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.Url;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;
import org.jvnet.hudson.test.recipes.WithPlugin;
import org.jvnet.hudson.test.recipes.WithPluginManager;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * @author Kohsuke Kawaguchi
 */
class PluginManagerTest {

    @RegisterExtension
    private final JenkinsSessionExtension session = PluginManagerUtil.newJenkinsSessionExtension();
    @TempDir
    private File tmp;
    private boolean signatureCheck;

    @BeforeEach
    void setUp() {
        signatureCheck = DownloadService.signatureCheck;
    }

    @AfterEach
    void tearDown() {
        DownloadService.signatureCheck = signatureCheck;
    }

    /**
     * Manual submission form.
     */
    @Test
    void uploadJpi() throws Throwable {
        session.then(r -> {
            HtmlPage page = r.createWebClient().goTo("pluginManager/advanced");
            HtmlForm f = page.getFormByName("uploadPlugin");
            File dir = newFolder(tmp, "junit");
            File plugin = new File(dir, "htmlpublisher.jpi");
            FileUtils.copyURLToFile(getClass().getClassLoader().getResource("plugins/htmlpublisher.jpi"), plugin);
            f.getInputByName("name").setValue(plugin.getAbsolutePath());
            r.submit(f);

            assertTrue(new File(r.jenkins.getRootDir(), "plugins/htmlpublisher.jpi").exists());
        });
    }

    /**
     * Manual submission form.
     */
    @Test
    void uploadHpi() throws Throwable {
        session.then(r -> {
            HtmlPage page = r.createWebClient().goTo("pluginManager/advanced");
            HtmlForm f = page.getFormByName("uploadPlugin");
            File dir = newFolder(tmp, "junit");
            File plugin = new File(dir, "legacy.hpi");
            FileUtils.copyURLToFile(getClass().getClassLoader().getResource("plugins/legacy.hpi"), plugin);
            f.getInputByName("name").setValue(plugin.getAbsolutePath());
            r.submit(f);

            // uploaded legacy plugins get renamed to *.jpi
            assertTrue(new File(r.jenkins.getRootDir(), "plugins/legacy.jpi").exists());
        });
    }

    @Test
    void deployJpiFromUrl() throws Throwable {
        session.then(r -> {
            HtmlPage page = r.createWebClient().goTo("pluginManager/advanced");
            HtmlForm f = page.getFormByName("uploadPlugin");
            f.getInputByName("pluginUrl").setValue(Jenkins.get().getRootUrl() + "pluginManagerGetPlugin/htmlpublisher.jpi");
            r.submit(f);

            assertTrue(new File(r.jenkins.getRootDir(), "plugins/htmlpublisher.jpi").exists());
        });
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
            staplerResponse.serveFile(staplerRequest,  PluginManagerTest.class.getClassLoader().getResource("plugins/htmlpublisher.jpi"));
        }
    }

    /**
     * Tests the effect of {@link WithPlugin}.
     */
    @WithPlugin("htmlpublisher.jpi")
    @Test
    void withRecipeJpi() throws Throwable {
        session.then(r -> assertNotNull(r.jenkins.getPlugin("htmlpublisher")));
    }

    /**
     * Tests the effect of {@link WithPlugin}.
     */
    @WithPlugin("legacy.hpi")
    @Test
    void withRecipeHpi() throws Throwable {
        session.then(r -> assertNotNull(r.jenkins.getPlugin("legacy")));
    }

    /**
     * Verifies that by the time {@link Plugin#start()} is called, uber classloader is fully functioning.
     * This is necessary as plugin start method can engage in XStream loading activities, and they should
     * resolve all the classes in the system (for example, a plugin X can define an extension point
     * other plugins implement, so when X loads its config it better sees all the implementations defined elsewhere)
     */
    @WithPlugin("htmlpublisher.jpi")
    @WithPluginManager(PluginManagerImpl_for_testUberClassLoaderIsAvailableDuringStart.class)
    @Test
    void uberClassLoaderIsAvailableDuringStart() throws Throwable {
        session.then(r -> assertTrue(((PluginManagerImpl_for_testUberClassLoaderIsAvailableDuringStart) r.jenkins.pluginManager).tested));
    }

    public static class PluginManagerImpl_for_testUberClassLoaderIsAvailableDuringStart extends LocalPluginManager {
        boolean tested;

        @SuppressWarnings("checkstyle:redundantmodifier")
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
    @Test
    void uberClassLoaderDoesntUseContextClassLoader() throws Throwable {
        session.then(r -> {
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
        });
    }

    @Test
    void installWithoutRestart() throws Throwable {
        session.then(r -> {
            URL res = getClass().getClassLoader().getResource("plugins/htmlpublisher.jpi");
            File f = new File(r.jenkins.getRootDir(), "plugins/htmlpublisher.jpi");
            FileUtils.copyURLToFile(res, f);
            r.jenkins.pluginManager.dynamicLoad(f);

            Class c = r.jenkins.getPluginManager().uberClassLoader.loadClass("htmlpublisher.HtmlPublisher$DescriptorImpl");
            assertNotNull(r.jenkins.getDescriptorByType(c));
        });
    }

    @Test
    void prevalidateConfig() throws Throwable {
        session.then(r -> {
            assumeFalse(Functions.isWindows(), "TODO: Implement this test on Windows");
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
            UpdateCenterJob job = jobs.getFirst().get(); // blocks for completion
            assertEquals("InstallationJob", job.getType());
            UpdateCenter.InstallationJob ijob = (UpdateCenter.InstallationJob) job;
            assertEquals("htmlpublisher", ijob.plugin.name);
            assertNotNull(r.jenkins.getPluginManager().getPlugin("htmlpublisher"));
            // TODO restart scheduled (SuccessButRequiresRestart) after upgrade or Support-Dynamic-Loading: false
            // TODO dependencies installed or upgraded too
            // TODO required plugin installed but inactive
        });
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
    private String callDependerValue(JenkinsRule r) throws Exception {
        Class<?> c = r.jenkins.getPluginManager().uberClassLoader.loadClass("org.jenkinsci.plugins.dependencytest.depender.Depender");
        Method m = c.getMethod("getValue");
        return (String) m.invoke(null);
    }

    /**
     * Load "dependee" and then load "depender".
     * Asserts that "depender" can access to "dependee".
     */
    @Test
    void installDependingPluginWithoutRestart() throws Throwable {
        session.then(r -> {
            // Load dependee.
            {
                dynamicLoad(r, "dependee.hpi");
            }

            // before load depender, of course failed to call Depender.getValue()
            assertThrows(ClassNotFoundException.class, () -> callDependerValue(r));

            // No extensions exist.
            assertTrue(r.jenkins.getExtensionList("org.jenkinsci.plugins.dependencytest.dependee.DependeeExtensionPoint").isEmpty());

            // Load depender.
            {
                dynamicLoad(r, "depender.hpi");
            }

            // depender successfully accesses to dependee.
            assertEquals("dependee", callDependerValue(r));

            // Extension in depender is loaded.
            assertFalse(r.jenkins.getExtensionList("org.jenkinsci.plugins.dependencytest.dependee.DependeeExtensionPoint").isEmpty());
        });
    }

    /**
     * Load "depender" and then load "dependee".
     * Asserts that "depender" can access to "dependee".
     */
    @Issue("JENKINS-19976")
    @Test
    void installDependedPluginWithoutRestart() throws Throwable {
        session.then(r -> {
            // Load depender.
            {
                dynamicLoad(r, "depender.hpi");
            }

            // before load dependee, depender does not access to dependee.
            assertEquals("depender", callDependerValue(r));

            // before load dependee, of course failed to list extensions for dependee.
            assertThrows(ClassNotFoundException.class, () -> r.jenkins.getExtensionList("org.jenkinsci.plugins.dependencytest.dependee.DependeeExtensionPoint"));
            // Extension extending a dependee class can't be loaded either
            assertThrows(NoClassDefFoundError.class, () -> r.jenkins.getExtensionList("org.jenkinsci.plugins.dependencytest.depender.DependerExtension"));

            // Load dependee.
            {
                dynamicLoad(r, "dependee.hpi");
            }

            // (MUST) Not throws an exception
            // (SHOULD) depender successfully accesses to dependee.
            assertEquals("dependee", callDependerValue(r));

            // Extensions in depender are loaded.
            assertEquals(1, r.jenkins.getExtensionList("org.jenkinsci.plugins.dependencytest.depender.DependerExtension").size());
        });
    }

    @Issue("JENKINS-21486")
    @Test
    void installPluginWithObsoleteDependencyFails() throws Throwable {
        session.then(r -> {
            // Load dependee 0.0.1.
            {
                dynamicLoad(r, "dependee.hpi");
            }

            // Load mandatory-depender 0.0.2, depending on dependee 0.0.2
            assertThrows(IOException.class, () -> dynamicLoad(r, "mandatory-depender-0.0.2.hpi"));
        });
    }

    @Issue("JENKINS-21486")
    @Test
    void installPluginWithDisabledOptionalDependencySucceeds() throws Throwable {
        session.then(r -> {
            // Load dependee 0.0.2.
            {
                dynamicLoadAndDisable(r, "dependee-0.0.2.hpi");
            }

            // Load depender 0.0.2, depending optionally on dependee 0.0.2
            {
                dynamicLoad(r, "depender-0.0.2.hpi");
            }

            // dependee is not loaded so we cannot list any extension for it.
            assertThrows(ClassNotFoundException.class, () -> r.jenkins.getExtensionList("org.jenkinsci.plugins.dependencytest.dependee.DependeeExtensionPoint"));
        });
    }

    @Issue("JENKINS-21486")
    @Test
    void installPluginWithDisabledDependencyFails() throws Throwable {
        session.then(r -> {
            // Load dependee 0.0.2.
            {
                dynamicLoadAndDisable(r, "dependee-0.0.2.hpi");
            }

            // Load mandatory-depender 0.0.2, depending on dependee 0.0.2
            assertThrows(IOException.class, () -> dynamicLoad(r, "mandatory-depender-0.0.2.hpi"));
        });
    }

    @Issue("JENKINS-68194")
    @WithPlugin("dependee.hpi")
    @Test
    void clearDisabledStatusAfterUninstall() throws Throwable {
        session.then(r -> {
            PluginWrapper pw = r.jenkins.pluginManager.getPlugin("dependee");
            assertNotNull(pw);

            pw.doMakeDisabled();
            pw.doDoUninstall();

            File disabledHpi = new File(r.jenkins.getRootDir(), "plugins/dependee.hpi.disabled");
            assertFalse(disabledHpi.exists());  // `.disabled` file should be deleted after uninstall
        });
    }

    @Issue("JENKINS-21486")
    @Test
    void installPluginWithObsoleteOptionalDependencyFails() throws Throwable {
        session.then(r -> {
            // Load dependee 0.0.1.
            {
                dynamicLoad(r, "dependee.hpi");
            }

            // Load depender 0.0.2, depending optionally on dependee 0.0.2
            assertThrows(IOException.class, () -> dynamicLoad(r, "depender-0.0.2.hpi"));
        });
    }

    @Issue("JENKINS-12753")
    @WithPlugin("htmlpublisher.jpi")
    @Test
    void dynamicLoadRestartRequiredException() throws Throwable {
        session.then(r -> {
            File jpi = new File(r.jenkins.getRootDir(), "plugins/htmlpublisher.jpi");
            assertTrue(jpi.isFile());
            FileUtils.touch(jpi);
            File timestamp = new File(r.jenkins.getRootDir(), "plugins/htmlpublisher/.timestamp2");
            assertTrue(timestamp.isFile());
            long lastMod = timestamp.lastModified();
            assertThrows(RestartRequiredException.class, () -> r.jenkins.getPluginManager().dynamicLoad(jpi));
            assertEquals(lastMod, timestamp.lastModified(), "should not have tried to delete & unpack");
        });
    }

    @WithPlugin("htmlpublisher.jpi")
    @Test
    void pluginListJSONApi() throws Throwable {
        session.then(r -> {
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
        });
    }

    @Issue("JENKINS-41684")
    @Test
    void requireSystemDuringLoad() throws Throwable {
        session.then(r -> {
            r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
            r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy());
            try (ACLContext context = ACL.as2(User.getById("underprivileged", true).impersonate2())) {
                dynamicLoad(r, "require-system-during-load.hpi");
            }
        });
    }

    @Test
    @Issue("JENKINS-59775")
    void requireSystemDuringStart() throws Throwable {
        session.then(r -> {
            r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
            r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy());
            String pluginShortName = "require-system-during-load";
            dynamicLoad(r, pluginShortName + ".hpi");
            try (ACLContext context = ACL.as2(User.getById("underprivileged", true).impersonate2())) {
                r.jenkins.pluginManager.start(List.of(r.jenkins.pluginManager.getPlugin(pluginShortName)));
            }
        });
    }

    @Issue("JENKINS-61071")
    @Test
    void requireSystemInInitializer() throws Throwable {
        session.then(r -> {
            r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
            r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy());
            String pluginShortName = "require-system-in-initializer";
            dynamicLoad(r, pluginShortName + ".jpi");
            try (ACLContext context = ACL.as2(User.getById("underprivileged", true).impersonate2())) {
                r.jenkins.pluginManager.start(List.of(r.jenkins.pluginManager.getPlugin(pluginShortName)));
            }
        });
    }

    private void dynamicLoad(JenkinsRule r, String plugin) throws IOException, InterruptedException, RestartRequiredException {
        PluginManagerUtil.dynamicLoad(plugin, r.jenkins);
    }

    private void dynamicLoadAndDisable(JenkinsRule r, String plugin) throws IOException, InterruptedException, RestartRequiredException {
        PluginManagerUtil.dynamicLoad(plugin, r.jenkins, true);
    }

    @Test
    void uploadDependencyResolution() throws Throwable {
        session.then(r -> {
            assumeFalse(Functions.isWindows(), "TODO: Implement this test for Windows");
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
            File dir = newFolder(tmp, "junit");
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
        });
    }

    @Issue("JENKINS-44898")
    @WithPlugin("plugin-first.hpi")
    @Test
    void findResourceForPluginFirstClassLoader() throws Throwable {
        session.then(r -> {
            PluginWrapper w = r.jenkins.getPluginManager().getPlugin("plugin-first");
            assertNotNull(w);

            URL fromPlugin = w.classLoader.getResource("org/jenkinsci/plugins/pluginfirst/HelloWorldBuilder/config.jelly");
            assertNotNull(fromPlugin);

            // This is how UberClassLoader.findResource functions.
            URL fromToolkit = ClassLoaderReflectionToolkit._findResource(w.classLoader, "org/jenkinsci/plugins/pluginfirst/HelloWorldBuilder/config.jelly");

            assertEquals(fromPlugin, fromToolkit);
        });
    }

    @Test
    @Issue("JENKINS-64840")
    @WithPlugin({"mandatory-depender-0.0.2.hpi", "dependee-0.0.2.hpi", "depender-0.0.2.hpi"})
    void getPluginsSortedByTitle() throws Throwable {
        session.then(r -> {
            List<String> installedPlugins = r.jenkins.getPluginManager().getPluginsSortedByTitle()
                    .stream()
                    .map(PluginWrapper::getDisplayName)
                    .toList();

            assertThat(installedPlugins, containsInRelativeOrder("dependee", "depender", "mandatory-depender"));
        });
    }

    @Issue("JENKINS-62622")
    @Test
    @WithPlugin("legacy.hpi")
    void doNotThrowWithUnknownPlugins() throws Throwable {
        session.then(r -> {
            final UpdateCenter uc = Jenkins.get().getUpdateCenter();
            assertNull(uc.getPlugin("legacy"), "This test requires the plugin with ID 'legacy' to not exist in update sites");

            // ensure data is loaded - probably unnecessary, but closer to reality
            assertSame(FormValidation.Kind.OK, uc.getSite("default").updateDirectlyNow().kind);
        });
    }

    @Test
    @Issue("JENKINS-64840")
    void searchMultipleUpdateSites() throws Throwable {
        session.then(r -> {
            assumeFalse(Functions.isWindows(), "TODO: Implement this test for Windows");
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
            assertEquals(1, data.size(), "Should be one search hit for dummy");

            //token-macro plugin is found in the first site (didn't work before the fix)
            response = r.getJSON("pluginManager/pluginsSearch?query=token&limit=5");
            json = response.getJSONObject();
            assertTrue(json.has("data"));
            data = json.getJSONArray("data");
            assertEquals(1, data.size(), "Should be one search hit for token");

            //hello-world plugin is found in the first site and hello-huston in the second (didn't work before the fix)
            response = r.getJSON("pluginManager/pluginsSearch?query=hello&limit=5");
            json = response.getJSONObject();
            assertTrue(json.has("data"));
            data = json.getJSONArray("data");
            assertEquals(2, data.size(), "Should be two search hits for hello");
        });
    }

    @Issue("JENKINS-70599")
    @Test
    void installNecessaryPluginsTest() throws Throwable {
        session.then(r -> {
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
            assertEquals(200, responseGet.statusCode(), "Bad response for crumb issuer");
            String body = responseGet.body();
            assertTrue(body.contains("crumbRequestField"), "crumbRequestField not in response");
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
            assertEquals(200, response.statusCode(), "Bad response for installNecessaryPlugins");
        });
    }

    @Test
    @Issue("SECURITY-2823")
    void verifyUploadedPluginPermission() throws Throwable {
        assumeFalse(Functions.isWindows());

        session.then(r -> {
            HtmlPage page = r.createWebClient().goTo("pluginManager/advanced");
            HtmlForm f = page.getFormByName("uploadPlugin");
            File dir = newFolder(tmp, "junit");
            File plugin = new File(dir, "htmlpublisher.jpi");
            FileUtils.copyURLToFile(Objects.requireNonNull(getClass().getClassLoader().getResource("plugins/htmlpublisher.jpi")), plugin);
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
        });
    }

    @Test
    @Issue("SECURITY-3037")
    void noInjectionOnAvailablePluginsPage() throws Throwable {
        session.then(r -> {
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
        });
    }

    @Test
    @Issue("SECURITY-3072")
    void verifyUploadedPluginFromURLPermission() throws Throwable {
        assumeFalse(Functions.isWindows());

        session.then(r -> {
            HtmlPage page = r.createWebClient().goTo("pluginManager/advanced");
            HtmlForm f = page.getFormByName("uploadPlugin");
            f.getInputByName("pluginUrl").setValue(Jenkins.get().getRootUrl() + "pluginManagerGetPlugin/htmlpublisher.jpi");
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
        });
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
            staplerResponse.serveFile(staplerRequest,  PluginManagerTest.class.getClassLoader().getResource("plugins/htmlpublisher.jpi"));
        }
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }

}
