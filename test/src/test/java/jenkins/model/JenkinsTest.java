/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Yahoo!, Inc.
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

package jenkins.model;

import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import hudson.ExtensionList;
import hudson.Functions;
import hudson.XmlFile;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AllView;
import hudson.model.Computer;
import hudson.model.Failure;
import hudson.model.FreeStyleProject;
import hudson.model.InvisibleAction;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.RestartListener;
import hudson.model.RootAction;
import hudson.model.Saveable;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.model.UnprotectedRootAction;
import hudson.model.User;
import hudson.model.listeners.SaveableListener;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.slaves.ComputerListener;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import hudson.util.FormValidation;
import hudson.util.HttpResponses;
import hudson.util.VersionNumber;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.TextPage;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.reactor.ReactorException;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.WithPlugin;
import org.kohsuke.stapler.HttpResponse;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * Tests of the {@link Jenkins} class instance logic.
 * @see Jenkins
 * @see JenkinsRule
 */
@Tag("SmokeTest")
@WithJenkins
public class JenkinsTest {

    @TempDir
    private File tmp;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("SECURITY-3498")
    void testPaneToggleCollapse() {
        try (WebClient wc = j.createWebClient()) {
            final FailingHttpStatusCodeException ex = assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo("toggleCollapse?paneId=foo"));
            // @POST responds 404 when the verb is wrong; @RequirePOST would respond 405.
            assertThat(ex.getStatusCode(), is(HttpServletResponse.SC_NOT_FOUND));
        }
    }

    @Test
    @Issue("SECURITY-3073")
    void verifyUploadedFingerprintFilePermission() throws Exception {
        assumeFalse(Functions.isWindows());

        HtmlPage page = j.createWebClient().goTo("fingerprintCheck");
        HtmlForm form = page.getForms().get(0);
        File dir = newFolder(tmp, "junit");
        File plugin = new File(dir, "htmlpublisher.jpi");
        // We're using a plugin to have a file above DiskFileItemFactory.DEFAULT_SIZE_THRESHOLD
        FileUtils.copyURLToFile(Objects.requireNonNull(getClass().getClassLoader().getResource("plugins/htmlpublisher.jpi")), plugin);
        form.getInputByName("name").setValueAttribute(plugin.getAbsolutePath());
        j.submit(form);

        File filesRef = Files.createTempFile("tmp", ".tmp").toFile();
        File filesTmpDir = filesRef.getParentFile();
        filesRef.deleteOnExit();

        final Set<PosixFilePermission>[] filesPermission = new Set[]{new HashSet<>()};
        await().pollInterval(250, TimeUnit.MILLISECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> {
                    Optional<File> lastUploadedPlugin = Arrays.stream(Objects.requireNonNull(
                                    filesTmpDir.listFiles((file, fileName) ->
                                            fileName.startsWith("jenkins-multipart-uploads")))).
                            max(Comparator.comparingLong(File::lastModified));
                    if (lastUploadedPlugin.isPresent()) {
                        filesPermission[0] = Files.getPosixFilePermissions(lastUploadedPlugin.get().toPath(), LinkOption.NOFOLLOW_LINKS);
                        return true;
                    } else {
                        return false;
                    }
                });
        assertEquals(EnumSet.of(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE), filesPermission[0]);
    }

    @Issue("SECURITY-406")
    @Test
    void testUserCreationFromUrlForAdmins() throws Exception {
        WebClient wc = j.createWebClient();

        assertNull(User.getById("nonexistent", false), "User not supposed to exist");
        wc.assertFails("user/nonexistent", 404);
        assertNull(User.getById("nonexistent", false), "User not supposed to exist");

        try {
            User.ALLOW_USER_CREATION_VIA_URL = true;

            // expected to work
            wc.goTo("user/nonexistent2");

            assertNotNull(User.getById("nonexistent2", false), "User supposed to exist");

        } finally {
            User.ALLOW_USER_CREATION_VIA_URL = false;
        }
    }

    @Test
    void testIsDisplayNameUniqueTrue() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        FreeStyleProject curProject = j.createFreeStyleProject(curJobName);
        curProject.setDisplayName("currentProjectDisplayName");

        FreeStyleProject p = j.createFreeStyleProject(jobName);
        p.setDisplayName("displayName");

        Jenkins jenkins = Jenkins.get();
        assertTrue(jenkins.isDisplayNameUnique(jenkins, "displayName1", curJobName));
        assertTrue(jenkins.isDisplayNameUnique(jenkins, jobName, curJobName));
    }

    @Test
    void testIsDisplayNameUniqueFalse() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        final String displayName = "displayName";

        FreeStyleProject curProject = j.createFreeStyleProject(curJobName);
        curProject.setDisplayName("currentProjectDisplayName");

        FreeStyleProject p = j.createFreeStyleProject(jobName);
        p.setDisplayName(displayName);

        Jenkins jenkins = Jenkins.get();
        assertFalse(jenkins.isDisplayNameUnique(jenkins, displayName, curJobName));
    }

    @Test
    void testIsDisplayNameUniqueSameAsCurrentJob() throws Exception {
        final String curJobName = "curJobName";
        final String displayName = "currentProjectDisplayName";

        FreeStyleProject curProject = j.createFreeStyleProject(curJobName);
        curProject.setDisplayName(displayName);

        Jenkins jenkins = Jenkins.get();
        // should be true as we don't test against the current job
        assertTrue(jenkins.isDisplayNameUnique(jenkins, displayName, curJobName));
    }

    @Test
    void testIsNameUniqueTrue() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        j.createFreeStyleProject(curJobName);
        j.createFreeStyleProject(jobName);

        Jenkins jenkins = Jenkins.get();
        assertTrue(jenkins.isNameUnique(jenkins, "jobName1", curJobName));
    }

    @Test
    void testIsNameUniqueFalse() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        j.createFreeStyleProject(curJobName);
        j.createFreeStyleProject(jobName);

        Jenkins jenkins = Jenkins.get();
        assertFalse(jenkins.isNameUnique(jenkins, jobName, curJobName));
    }

    @Test
    void testIsNameUniqueSameAsCurrentJob() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        j.createFreeStyleProject(curJobName);
        j.createFreeStyleProject(jobName);

        Jenkins jenkins = Jenkins.get();
        // true because we don't test against the current job
        assertTrue(jenkins.isNameUnique(jenkins, curJobName, curJobName));
    }

    @Test
    void testDoCheckDisplayNameUnique() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        FreeStyleProject curProject = j.createFreeStyleProject(curJobName);
        curProject.setDisplayName("currentProjectDisplayName");

        FreeStyleProject p = j.createFreeStyleProject(jobName);
        p.setDisplayName("displayName");

        Jenkins jenkins = Jenkins.get();
        FormValidation v = jenkins.checkDisplayName("1displayName", curProject);
        assertEquals(FormValidation.ok(), v);
    }

    @Test
    void testDoCheckDisplayNameSameAsDisplayName() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        final String displayName = "displayName";
        FreeStyleProject curProject = j.createFreeStyleProject(curJobName);
        curProject.setDisplayName("currentProjectDisplayName");

        FreeStyleProject p = j.createFreeStyleProject(jobName);
        p.setDisplayName(displayName);

        Jenkins jenkins = Jenkins.get();
        FormValidation v = jenkins.checkDisplayName(displayName, curProject);
        assertEquals(FormValidation.Kind.WARNING, v.kind);
    }

    @Test
    void testDoCheckDisplayNameSameAsJobName() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        final String displayName = "displayName";
        FreeStyleProject curProject = j.createFreeStyleProject(curJobName);
        curProject.setDisplayName("currentProjectDisplayName");

        FreeStyleProject p = j.createFreeStyleProject(jobName);
        p.setDisplayName(displayName);

        Jenkins jenkins = Jenkins.get();
        FormValidation v = jenkins.checkDisplayName(jobName, curProject);
        assertEquals(FormValidation.Kind.WARNING, v.kind);
    }

    @Test
    void testDoCheckViewName_GoodName() {
        String[] viewNames = new String[] {
            "",
            "Jenkins",
        };

        Jenkins jenkins = Jenkins.get();
        for (String viewName : viewNames) {
            FormValidation v = jenkins.doCheckViewName(viewName);
            assertEquals(FormValidation.Kind.OK, v.kind);
        }
    }

    @Test
    void testDoCheckViewName_NotGoodName() {
        String[] viewNames = new String[] {
            "Jenkins?",
            "Jenkins*",
            "Jenkin/s",
            "Jenkin\\s",
            "jenkins%",
            "Jenkins!",
            "Jenkins[]",
            "Jenkin<>s",
            "^Jenkins",
            "..",
        };

        Jenkins jenkins = Jenkins.get();

        for (String viewName : viewNames) {
            FormValidation v = jenkins.doCheckViewName(viewName);
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }
    }


    /**
     * Makes sure access to "/foobar" for UnprotectedRootAction gets through.
     */
    @Test
    @Issue("JENKINS-14113")
    void testUnprotectedRootAction() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new FullControlOnceLoggedInAuthorizationStrategy());
        WebClient wc = j.createWebClient();
        wc.goTo("foobar");
        wc.goTo("foobar/");
        wc.goTo("foobar/zot");

        // and make sure this fails
        wc.assertFails("foobar-zot/", HttpURLConnection.HTTP_INTERNAL_ERROR);

        assertEquals(3, j.jenkins.getExtensionList(RootAction.class).get(RootActionImpl.class).count);
    }

    @Test
    void testDoScript() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
            grant(Jenkins.ADMINISTER).everywhere().to("alice").
            grant(Jenkins.READ).everywhere().to("bob").
            grantWithoutImplication(Jenkins.RUN_SCRIPTS, Jenkins.READ).everywhere().to("charlie"));
        WebClient wc = j.createWebClient();

        wc.withBasicApiToken(User.getById("alice", true));
        wc.goTo("script");
        wc.assertFails("script?script=System.setProperty('hack','me')", HttpURLConnection.HTTP_BAD_METHOD);
        assertNull(System.getProperty("hack"));
        WebRequest req = new WebRequest(new URL(wc.getContextPath() + "script?script=System.setProperty('hack','me')"), HttpMethod.POST);
        wc.getPage(req);
        assertEquals("me", System.getProperty("hack"));
        wc.assertFails("scriptText?script=System.setProperty('hack','me')", HttpURLConnection.HTTP_BAD_METHOD);
        req = new WebRequest(new URL(wc.getContextPath() + "scriptText?script=System.setProperty('huck','you')"), HttpMethod.POST);
        wc.getPage(req);
        assertEquals("you", System.getProperty("huck"));

        wc.withBasicApiToken(User.getById("bob", true));
        wc.assertFails("script", HttpURLConnection.HTTP_FORBIDDEN);

        //TODO: remove once RUN_SCRIPTS is finally retired
        wc.withBasicApiToken(User.getById("charlie", true));
        wc.assertFails("script", HttpURLConnection.HTTP_FORBIDDEN);
    }

    @Test
    @Issue("JENKINS-58548")
    void testDoScriptTextDoesNotOutputExtraWhitespace() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        WebClient wc = j.createWebClient().login("admin");
        TextPage page = wc.getPage(new WebRequest(wc.createCrumbedUrl("scriptText?script=print 'hello'"), HttpMethod.POST));
        assertEquals("hello", page.getContent());
    }

    @Test
    void testDoEval() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
            grant(Jenkins.ADMINISTER).everywhere().to("alice").
            grant(Jenkins.READ).everywhere().to("bob").
            grantWithoutImplication(Jenkins.ADMINISTER, Jenkins.READ).everywhere().to("charlie"));

        WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false)
                .withBasicApiToken(User.getById("alice", true));

        wc.assertFails("eval", HttpURLConnection.HTTP_BAD_METHOD);
        assertEquals("3", eval(wc).getWebResponse().getContentAsString());

        wc.withBasicApiToken(User.getById("bob", true));
        Page page = eval(wc);
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN,
                page.getWebResponse().getStatusCode(),
                "bob has only READ");

        wc.withBasicApiToken(User.getById("charlie", true));
        page = eval(wc);
        assertEquals(HttpURLConnection.HTTP_OK,
                page.getWebResponse().getStatusCode(),
                "charlie has ADMINISTER and READ");
    }

    private Page eval(WebClient wc) throws Exception {
        WebRequest req = new WebRequest(new URI(wc.getContextPath() + "eval").toURL(), HttpMethod.POST);
        req.setEncodingType(null);
        req.setRequestBody("<j:jelly xmlns:j='jelly:core'>${1+2}</j:jelly>");
        return wc.getPage(req);
    }

    @TestExtension("testUnprotectedRootAction")
    public static class RootActionImpl implements UnprotectedRootAction {
        private int count;

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return "foobar";
        }

        public HttpResponse doDynamic() {
            assertEquals("anonymous", Jenkins.getAuthentication2().getName());
            count++;
            return HttpResponses.html("OK");
        }
    }

    @TestExtension("testUnprotectedRootAction")
    public static class ProtectedRootActionImpl extends InvisibleAction implements RootAction {
        @Override
        public String getUrlName() {
            return "foobar-zot";
        }

        public HttpResponse doDynamic() {
            throw new AssertionError();
        }
    }

    @Test
    @Issue("JENKINS-20866")
    void testErrorPageShouldBeAnonymousAccessible() throws Exception {
        HudsonPrivateSecurityRealm s = new HudsonPrivateSecurityRealm(false, false, null);
        User alice = s.createAccount("alice", "alice");
        j.jenkins.setSecurityRealm(s);

        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(auth);

        // no anonymous read access
        assertFalse(Jenkins.get().hasPermission2(Jenkins.ANONYMOUS2, Jenkins.READ));

        WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);
        HtmlPage p = wc.goTo("error/reportError");

        assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, p.getWebResponse().getStatusCode(), p.asNormalizedText());  // not 403 forbidden
        assertTrue(p.getWebResponse().getContentAsString().contains("My car is black"));
    }

    @TestExtension("testErrorPageShouldBeAnonymousAccessible")
    public static class ReportError implements UnprotectedRootAction {

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return "error";
        }

        public HttpResponse doReportError() {
            return new Failure("My car is black");
        }
    }

    @Test
    @Issue("JENKINS-23551")
    void testComputerListenerNotifiedOnRestart() {
        // Simulate restart calling listeners
        for (RestartListener listener : RestartListener.all())
            listener.onRestart();

        ArgumentCaptor<OfflineCause> captor = ArgumentCaptor.forClass(OfflineCause.class);
        Mockito.verify(listenerMock).onOffline(ArgumentMatchers.eq(j.jenkins.toComputer()), captor.capture());
        assertTrue(captor.getValue().toString().contains("restart"));
    }

    @TestExtension(value = "testComputerListenerNotifiedOnRestart")
    public static final ComputerListener listenerMock = Mockito.mock(ComputerListener.class);

    @Test
    void runScriptOnOfflineComputer() throws Exception {
        DumbSlave slave = j.createSlave(true);
        j.disconnectSlave(slave);

        URL url = new URL(j.getURL(), "computer/" + slave.getNodeName() + "/scriptText?script=println(42)");

        WebClient wc = j.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);

        WebRequest req = new WebRequest(url, HttpMethod.POST);
        Page page = wc.getPage(wc.addCrumb(req));
        WebResponse rsp = page.getWebResponse();

        assertThat(rsp.getContentAsString(), containsString("Node is offline"));
        assertThat(rsp.getStatusCode(), equalTo(404));
    }

    @Test
    @Issue("JENKINS-38487")
    void startupShouldNotFailOnIOExceptionOnlineListener() {
        // We do nothing, IOExceptionOnOnlineListener & JenkinsRule should cause the
        // boot failure if the issue is not fixed.

        assertEquals(1, IOExceptionOnOnlineListener.onOnlineCount);
    }

    @TestExtension(value = "startupShouldNotFailOnIOExceptionOnlineListener")
    public static final class IOExceptionOnOnlineListener extends ComputerListener {

        static int onOnlineCount = 0;

        @Override
        public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
            onOnlineCount++;
            throw new IOException("Something happened (the listener always throws this exception)");
        }
    }

    @Test
    @Issue("JENKINS-57111")
    void startupShouldNotFailOnRuntimeExceptionOnlineListener() {
        // We do nothing, RuntimeExceptionOnOnlineListener & JenkinsRule should cause the
        // boot failure if the issue is not fixed.
        assertEquals(1, RuntimeExceptionOnOnlineListener.onOnlineCount);
    }

    @TestExtension(value = "startupShouldNotFailOnRuntimeExceptionOnlineListener")
    public static final class RuntimeExceptionOnOnlineListener extends ComputerListener {

        static int onOnlineCount = 0;

        @Override
        public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
            onOnlineCount++;
            throw new RuntimeException("Something happened (the listener always throws this exception)");
        }
    }

    @Test
    void getComputers() throws Exception {
        List<Slave> agents = new ArrayList<>();
        for (String n : List.of("zestful", "bilking", "grouchiest")) {
            agents.add(j.createSlave(n, null, null));
        }
        for (Slave agent : agents) {
            j.waitOnline(agent);
        }
        assertThat(Stream.of(j.jenkins.getComputers()).map(Computer::getName).toArray(String[]::new),
            arrayContaining("", "bilking", "grouchiest", "zestful"));
    }

    @Issue("JENKINS-42577")
    @Test
    void versionIsSavedInSave() throws Exception {
        Jenkins.VERSION = "1.0";
        j.jenkins.save();
        VersionNumber storedVersion = Jenkins.getStoredVersion();
        assertNotNull(storedVersion);
        assertEquals("1.0", storedVersion.toString());

        Jenkins.VERSION = null;
        j.jenkins.save();
        VersionNumber nullVersion = Jenkins.getStoredVersion();
        assertNull(nullVersion);
    }

    // Sources: https://github.com/Vlatombe/jenkins-47406
    @Issue("JENKINS-47406")
    @Test
    @WithPlugin("jenkins-47406.hpi")
    void jobCreatedByInitializerIsRetained() {
        assertNotNull(j.jenkins.getItem("JENKINS-47406"), "JENKINS-47406 should exist");
    }

    @Issue("SECURITY-2047")
    @Test
    void testLogin123() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy());
        WebClient wc = j.createWebClient();

        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo("login123"), "Page should be protected.");
        assertThat(e.getStatusCode(), is(403));
    }

    @Issue("SECURITY-2047")
    @Test
    void testLogin123WithRead() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
                grant(Jenkins.READ).everywhere().to("bob"));
        WebClient wc = j.createWebClient();

        wc.login("bob");
        HtmlPage login123 = wc.goTo("login123");
        assertThat(login123.getWebResponse().getStatusCode(), is(200));
        assertThat(login123.getWebResponse().getContentAsString(), containsString("This should be protected"));
    }

    @Test
    void testLogin() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
                grant(Jenkins.READ).everywhere().to("bob"));
        WebClient wc = j.createWebClient();

        HtmlPage login = wc.goTo("login");
        assertThat(login.getWebResponse().getStatusCode(), is(200));
        assertThat(login.getWebResponse().getContentAsString(), containsString("login"));
    }

    @Issue("JENKINS-68055")
    @Test
    void testTrimLabelsRetainsLabelExpressions() throws Exception {
        Node n = j.createOnlineSlave();
        n.setLabelString("test expression");

        FreeStyleProject f = j.createFreeStyleProject();
        Label l = Label.parseExpression("test&&expression");
        f.setAssignedLabel(l);
        f.scheduleBuild2(0).get();

        j.jenkins.trimLabels();
        assertThat(j.jenkins.getLabels().contains(l), is(true));
    }

    @Test
    void reloadShouldNotSaveConfig() throws Exception {
        SaveableListenerImpl saveListener = ExtensionList.lookupSingleton(SaveableListenerImpl.class);
        saveListener.reset();
        j.jenkins.reload();
        assertFalse(saveListener.wasCalled(), "Jenkins object should not have been saved.");
    }

    @TestExtension("reloadShouldNotSaveConfig")
    public static class SaveableListenerImpl extends SaveableListener {
        private boolean called;

        void reset() {
            called = false;
        }

        boolean wasCalled() {
            return called;
        }

        @Override
        public void onChange(Saveable o, XmlFile file) {
            if (o instanceof Jenkins) {
                called = true;
            }
        }
    }

    @TestExtension({"testLogin123", "testLogin123WithRead"})
    public static class ProtectedRootAction implements RootAction {
        @Override
        public String getIconFileName() {
            return "document.png";
        }

        @Override
        public String getDisplayName() {
            return "I am PROTECTED";
        }

        @Override
        public String getUrlName() {
            return "login123";
        }
    }

    @Test
    void checkInitialView() {
        assertTrue(CheckInitialViewExtension.hasPrimaryView);
    }

    @TestExtension(value = "checkInitialView")
    public static class CheckInitialViewExtension implements RootAction {
        private static boolean hasPrimaryView;

        @Initializer(after = InitMilestone.SYSTEM_CONFIG_LOADED, before = InitMilestone.JOB_CONFIG_ADAPTED)
        public static void checkViews() {
            hasPrimaryView = Jenkins.get().getPrimaryView() != null;
        }


        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return null;
        }
    }

    @Test
    void reloadViews() throws Exception {
        assertThat(j.jenkins.getPrimaryView(), isA(AllView.class));
        assertThat(j.jenkins.getViews(), contains(isA(AllView.class)));
        Files.writeString(j.jenkins.getConfigFile().getFile().toPath(), "<broken");
        assertThrows(ReactorException.class, j.jenkins::reload);
        j.createWebClient().goTo("manage/");
        assertThat(j.jenkins.getPrimaryView(), isA(AllView.class));
        assertThat(j.jenkins.getViews(), contains(isA(AllView.class)));
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
