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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.Computer;
import hudson.model.Failure;
import hudson.model.RestartListener;
import hudson.model.RootAction;
import hudson.model.UnprotectedRootAction;
import hudson.model.User;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.util.HttpResponses;
import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.slaves.ComputerListener;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import hudson.util.FormValidation;
import hudson.util.VersionNumber;

import jenkins.AgentProtocol;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.HttpResponse;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import javax.annotation.CheckForNull;

/**
 * Tests of the {@link Jenkins} class instance logic.
 * @see Jenkins
 * @see JenkinsRule
 */
public class JenkinsTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Issue("SECURITY-406")
    @Test
    public void testUserCreationFromUrlForAdmins() throws Exception {
        WebClient wc = j.createWebClient();

        assertNull("User not supposed to exist", User.getById("nonexistent", false));
        wc.assertFails("user/nonexistent", 404);
        assertNull("User not supposed to exist", User.getById("nonexistent", false));

        try {
            User.ALLOW_USER_CREATION_VIA_URL = true;

            // expected to work
            wc.goTo("user/nonexistent2");

            assertNotNull("User supposed to exist", User.getById("nonexistent2", false));

        } finally {
            User.ALLOW_USER_CREATION_VIA_URL = false;
        }
    }

    @Test
    public void testIsDisplayNameUniqueTrue() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        FreeStyleProject curProject = j.createFreeStyleProject(curJobName);
        curProject.setDisplayName("currentProjectDisplayName");
        
        FreeStyleProject p = j.createFreeStyleProject(jobName);
        p.setDisplayName("displayName");
        
        Jenkins jenkins = Jenkins.getInstance();
        assertTrue(jenkins.isDisplayNameUnique("displayName1", curJobName));
        assertTrue(jenkins.isDisplayNameUnique(jobName, curJobName));
    }

    @Test
    public void testIsDisplayNameUniqueFalse() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        final String displayName = "displayName";
        
        FreeStyleProject curProject = j.createFreeStyleProject(curJobName);
        curProject.setDisplayName("currentProjectDisplayName");
        
        FreeStyleProject p = j.createFreeStyleProject(jobName);
        p.setDisplayName(displayName);
        
        Jenkins jenkins = Jenkins.getInstance();
        assertFalse(jenkins.isDisplayNameUnique(displayName, curJobName));
    }
    
    @Test
    public void testIsDisplayNameUniqueSameAsCurrentJob() throws Exception {
        final String curJobName = "curJobName";
        final String displayName = "currentProjectDisplayName";
        
        FreeStyleProject curProject = j.createFreeStyleProject(curJobName);
        curProject.setDisplayName(displayName);
        
        Jenkins jenkins = Jenkins.getInstance();
        // should be true as we don't test against the current job
        assertTrue(jenkins.isDisplayNameUnique(displayName, curJobName));
    }
    
    @Test
    public void testIsNameUniqueTrue() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        j.createFreeStyleProject(curJobName);
        j.createFreeStyleProject(jobName);
        
        Jenkins jenkins = Jenkins.getInstance();
        assertTrue(jenkins.isNameUnique("jobName1", curJobName));
    }

    @Test
    public void testIsNameUniqueFalse() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        j.createFreeStyleProject(curJobName);
        j.createFreeStyleProject(jobName);
        
        Jenkins jenkins = Jenkins.getInstance();
        assertFalse(jenkins.isNameUnique(jobName, curJobName));
    }

    @Test
    public void testIsNameUniqueSameAsCurrentJob() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        j.createFreeStyleProject(curJobName);
        j.createFreeStyleProject(jobName);
        
        Jenkins jenkins = Jenkins.getInstance();
        // true because we don't test against the current job
        assertTrue(jenkins.isNameUnique(curJobName, curJobName));
    }
    
    @Test
    public void testDoCheckDisplayNameUnique() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        FreeStyleProject curProject = j.createFreeStyleProject(curJobName);
        curProject.setDisplayName("currentProjectDisplayName");
        
        FreeStyleProject p = j.createFreeStyleProject(jobName);
        p.setDisplayName("displayName");
        
        Jenkins jenkins = Jenkins.getInstance();
        FormValidation v = jenkins.doCheckDisplayName("1displayName", curJobName);
        assertEquals(FormValidation.ok(), v);
    }

    @Test
    public void testDoCheckDisplayNameSameAsDisplayName() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        final String displayName = "displayName";
        FreeStyleProject curProject = j.createFreeStyleProject(curJobName);
        curProject.setDisplayName("currentProjectDisplayName");
        
        FreeStyleProject p = j.createFreeStyleProject(jobName);
        p.setDisplayName(displayName);
        
        Jenkins jenkins = Jenkins.getInstance();
        FormValidation v = jenkins.doCheckDisplayName(displayName, curJobName);
        assertEquals(FormValidation.Kind.WARNING, v.kind);
    }

    @Test
    public void testDoCheckDisplayNameSameAsJobName() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        final String displayName = "displayName";
        FreeStyleProject curProject = j.createFreeStyleProject(curJobName);
        curProject.setDisplayName("currentProjectDisplayName");
        
        FreeStyleProject p = j.createFreeStyleProject(jobName);
        p.setDisplayName(displayName);
        
        Jenkins jenkins = Jenkins.getInstance();
        FormValidation v = jenkins.doCheckDisplayName(jobName, curJobName);
        assertEquals(FormValidation.Kind.WARNING, v.kind);
    }

    @Test
    public void testDoCheckViewName_GoodName() throws Exception {
        String[] viewNames = new String[] {
            "", "Jenkins"    
        };
        
        Jenkins jenkins = Jenkins.getInstance();
        for (String viewName : viewNames) {
            FormValidation v = jenkins.doCheckViewName(viewName);
            assertEquals(FormValidation.Kind.OK, v.kind);
        }
    }

    @Test
    public void testDoCheckViewName_NotGoodName() throws Exception {
        String[] viewNames = new String[] {
            "Jenkins?", "Jenkins*", "Jenkin/s", "Jenkin\\s", "jenkins%", 
            "Jenkins!", "Jenkins[]", "Jenkin<>s", "^Jenkins", ".."    
        };
        
        Jenkins jenkins = Jenkins.getInstance();
        
        for (String viewName : viewNames) {
            FormValidation v = jenkins.doCheckViewName(viewName);
            assertEquals(FormValidation.Kind.ERROR, v.kind);
        }
    }
    
    @Test @Issue("JENKINS-12251")
    public void testItemFullNameExpansion() throws Exception {
        HtmlForm f = j.createWebClient().goTo("configure").getFormByName("config");
        f.getInputByName("_.rawBuildsDir").setValueAttribute("${JENKINS_HOME}/test12251_builds/${ITEM_FULL_NAME}");
        f.getInputByName("_.rawWorkspaceDir").setValueAttribute("${JENKINS_HOME}/test12251_ws/${ITEM_FULL_NAME}");
        j.submit(f);

        // build a dummy project
        MavenModuleSet m = j.jenkins.createProject(MavenModuleSet.class, "p");
        m.setScm(new ExtractResourceSCM(getClass().getResource("/simple-projects.zip")));
        MavenModuleSetBuild b = m.scheduleBuild2(0).get();

        // make sure these changes are effective
        assertTrue(b.getWorkspace().getRemote().contains("test12251_ws"));
        assertTrue(b.getRootDir().toString().contains("test12251_builds"));
    }

    /**
     * Makes sure access to "/foobar" for UnprotectedRootAction gets through.
     */
    @Test @Issue("JENKINS-14113")
    public void testUnprotectedRootAction() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new FullControlOnceLoggedInAuthorizationStrategy());
        WebClient wc = j.createWebClient();
        wc.goTo("foobar");
        wc.goTo("foobar/");
        wc.goTo("foobar/zot");

        // and make sure this fails
        wc.assertFails("foobar-zot/", HttpURLConnection.HTTP_INTERNAL_ERROR);

        assertEquals(3,j.jenkins.getExtensionList(RootAction.class).get(RootActionImpl.class).count);
    }

    @Test
    public void testDoScript() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
            grant(Jenkins.ADMINISTER).everywhere().to("alice").
            grant(Jenkins.READ).everywhere().to("bob").
            grantWithoutImplication(Jenkins.ADMINISTER, Jenkins.READ).everywhere().to("charlie"));
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

        wc.withBasicApiToken(User.getById("charlie", true));
        wc.assertFails("script", HttpURLConnection.HTTP_FORBIDDEN);
    }

    @Test
    public void testDoEval() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
            grant(Jenkins.ADMINISTER).everywhere().to("alice").
            grant(Jenkins.READ).everywhere().to("bob").
            grantWithoutImplication(Jenkins.ADMINISTER, Jenkins.READ).everywhere().to("charlie"));

        WebClient wc = j.createWebClient();

        wc.withBasicApiToken(User.getById("alice", true));
        wc.assertFails("eval", HttpURLConnection.HTTP_BAD_METHOD);
        assertEquals("3", eval(wc));

        wc.withBasicApiToken(User.getById("bob", true));
        try {
            eval(wc);
            fail("bob has only READ");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(HttpURLConnection.HTTP_FORBIDDEN, e.getStatusCode());
        }

        wc.withBasicApiToken(User.getById("charlie", true));
        try {
            eval(wc);
            fail("charlie has ADMINISTER but not RUN_SCRIPTS");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(HttpURLConnection.HTTP_FORBIDDEN, e.getStatusCode());
        }
    }
    private String eval(WebClient wc) throws Exception {
        WebRequest req = new WebRequest(new URL(wc.getContextPath() + "eval"), HttpMethod.POST);
        req.setEncodingType(null);
        req.setRequestBody("<j:jelly xmlns:j='jelly:core'>${1+2}</j:jelly>");
        return wc.getPage(req).getWebResponse().getContentAsString();
    }

    @TestExtension("testUnprotectedRootAction")
    public static class RootActionImpl implements UnprotectedRootAction {
        private int count;

        public String getIconFileName() {
            return null;
        }

        public String getDisplayName() {
            return null;
        }

        public String getUrlName() {
            return "foobar";
        }

        public HttpResponse doDynamic() {
            assertTrue(Jenkins.getInstance().getAuthentication().getName().equals("anonymous"));
            count++;
            return HttpResponses.html("OK");
        }
    }

    @TestExtension("testUnprotectedRootAction")
    public static class ProtectedRootActionImpl implements RootAction {
        public String getIconFileName() {
            return null;
        }

        public String getDisplayName() {
            return null;
        }

        public String getUrlName() {
            return "foobar-zot";
        }

        public HttpResponse doDynamic() {
            throw new AssertionError();
        }
    }

    @Test @Issue("JENKINS-20866")
    public void testErrorPageShouldBeAnonymousAccessible() throws Exception {
        HudsonPrivateSecurityRealm s = new HudsonPrivateSecurityRealm(false, false, null);
        User alice = s.createAccount("alice", "alice");
        j.jenkins.setSecurityRealm(s);

        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(auth);

        // no anonymous read access
        assertTrue(!Jenkins.getInstance().hasPermission(Jenkins.ANONYMOUS,Jenkins.READ));

        WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        HtmlPage p = wc.goTo("error/reportError");

        assertEquals(p.asText(), 400, p.getWebResponse().getStatusCode());  // not 403 forbidden
        assertTrue(p.getWebResponse().getContentAsString().contains("My car is black"));
    }

    @TestExtension("testErrorPageShouldBeAnonymousAccessible")
    public static class ReportError implements UnprotectedRootAction {

        public String getIconFileName() {
            return null;
        }

        public String getDisplayName() {
            return null;
        }

        public String getUrlName() {
            return "error";
        }

        public HttpResponse doReportError() {
            return new Failure("My car is black");
        }
    }

    @Test @Issue("JENKINS-23551")
    public void testComputerListenerNotifiedOnRestart() {
        // Simulate restart calling listeners
        for (RestartListener listener : RestartListener.all())
            listener.onRestart();

        ArgumentCaptor<OfflineCause> captor = ArgumentCaptor.forClass(OfflineCause.class);
        Mockito.verify(listenerMock).onOffline(Mockito.eq(j.jenkins.toComputer()), captor.capture());
        assertTrue(captor.getValue().toString().contains("restart"));
    }

    @TestExtension(value = "testComputerListenerNotifiedOnRestart")
    public static final ComputerListener listenerMock = Mockito.mock(ComputerListener.class);

    @Test
    public void runScriptOnOfflineComputer() throws Exception {
        DumbSlave slave = j.createSlave(true);
        j.disconnectSlave(slave);

        URL url = new URL(j.getURL(), "computer/" + slave.getNodeName() + "/scriptText?script=println(42)");

        WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        WebRequest req = new WebRequest(url, HttpMethod.POST);
        Page page = wc.getPage(wc.addCrumb(req));
        WebResponse rsp = page.getWebResponse();

        assertThat(rsp.getContentAsString(), containsString("Node is offline"));
        assertThat(rsp.getStatusCode(), equalTo(404));
    }

    @Test
    @Issue("JENKINS-38487")
    public void startupShouldNotFailOnFailingOnlineListener() {
        // We do nothing, FailingOnOnlineListener & JenkinsRule should cause the 
        // boot failure if the issue is not fixed.
    }

    @TestExtension(value = "startupShouldNotFailOnFailingOnlineListener")
    public static final class FailingOnOnlineListener extends ComputerListener {
        
        @Override
        public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
            throw new IOException("Something happened (the listener always throws this exception)");
        }
    }
    
    @Test
    @Issue("JENKINS-39465")
    public void agentProtocols_singleEnable_roundtrip() throws Exception {
        final Set<String> defaultProtocols = Collections.unmodifiableSet(j.jenkins.getAgentProtocols());
        
        final Set<String> newProtocols = new HashSet<>(defaultProtocols);
        newProtocols.add(MockOptInProtocol1.NAME);
        j.jenkins.setAgentProtocols(newProtocols);
        j.jenkins.save();
        final Set<String> agentProtocolsBeforeReload = j.jenkins.getAgentProtocols();
        assertProtocolEnabled(MockOptInProtocol1.NAME, "before the roundtrip");
        
        j.jenkins.reload();
        
        final Set<String> reloadedProtocols = j.jenkins.getAgentProtocols();
        assertFalse("The protocol list must have been really reloaded", agentProtocolsBeforeReload == reloadedProtocols);
        assertThat("We should have additional enabled protocol", 
                reloadedProtocols.size(), equalTo(defaultProtocols.size() + 1));
        assertProtocolEnabled(MockOptInProtocol1.NAME, "after the roundtrip");
    }
    
    @Test
    @Issue("JENKINS-39465")
    public void agentProtocols_multipleDisable_roundtrip() throws Exception {
        final Set<String> defaultProtocols = Collections.unmodifiableSet(j.jenkins.getAgentProtocols());
        assertProtocolEnabled(MockOptOutProtocol1.NAME, "after startup");

        final Set<String> newProtocols = new HashSet<>(defaultProtocols);
        newProtocols.remove(MockOptOutProtocol1.NAME);
        j.jenkins.setAgentProtocols(newProtocols);
        j.jenkins.save();
        assertProtocolDisabled(MockOptOutProtocol1.NAME, "before the roundtrip");
        final Set<String> agentProtocolsBeforeReload = j.jenkins.getAgentProtocols();
        j.jenkins.reload();
        
        assertFalse("The protocol list must have been really refreshed", agentProtocolsBeforeReload == j.jenkins.getAgentProtocols());
        assertThat("We should have disabled one protocol", 
                j.jenkins.getAgentProtocols().size(), equalTo(defaultProtocols.size() - 1));

        assertProtocolDisabled(MockOptOutProtocol1.NAME, "after the roundtrip");
    }
    
    @Test
    @Issue("JENKINS-39465")
    public void agentProtocols_multipleEnable_roundtrip() throws Exception {
        final Set<String> defaultProtocols = Collections.unmodifiableSet(j.jenkins.getAgentProtocols());
        final Set<String> newProtocols = new HashSet<>(defaultProtocols);
        newProtocols.add(MockOptInProtocol1.NAME);
        newProtocols.add(MockOptInProtocol2.NAME);
        j.jenkins.setAgentProtocols(newProtocols);
        j.jenkins.save();

        final Set<String> agentProtocolsBeforeReload = j.jenkins.getAgentProtocols();
        assertProtocolEnabled(MockOptInProtocol1.NAME, "before the roundtrip");
        assertProtocolEnabled(MockOptInProtocol2.NAME, "before the roundtrip");

        j.jenkins.reload();
        
        final Set<String> reloadedProtocols = j.jenkins.getAgentProtocols();
        assertFalse("The protocol list must have been really reloaded", agentProtocolsBeforeReload == reloadedProtocols);
        assertThat("There should be two additional enabled protocols",
                reloadedProtocols.size(), equalTo(defaultProtocols.size() + 2));
        assertProtocolEnabled(MockOptInProtocol1.NAME, "after the roundtrip");
        assertProtocolEnabled(MockOptInProtocol2.NAME, "after the roundtrip");
    }
    
    @Test
    @Issue("JENKINS-39465")
    public void agentProtocols_singleDisable_roundtrip() throws Exception {
        final Set<String> defaultProtocols = Collections.unmodifiableSet(j.jenkins.getAgentProtocols());
        final String protocolToDisable1 = MockOptOutProtocol1.NAME;
        final String protocolToDisable2 = MockOptOutProtocol2.NAME;
        
        final Set<String> newProtocols = new HashSet<>(defaultProtocols);
        newProtocols.remove(protocolToDisable1);
        newProtocols.remove(protocolToDisable2);
        j.jenkins.setAgentProtocols(newProtocols);
        j.jenkins.save();
        assertProtocolDisabled(protocolToDisable1, "before the roundtrip");
        assertProtocolDisabled(protocolToDisable2, "before the roundtrip");
        final Set<String> agentProtocolsBeforeReload = j.jenkins.getAgentProtocols();
        j.jenkins.reload();
        
        assertFalse("The protocol list must have been really reloaded", agentProtocolsBeforeReload == j.jenkins.getAgentProtocols());
        assertThat("We should have disabled two protocols", 
                j.jenkins.getAgentProtocols().size(), equalTo(defaultProtocols.size() - 2));
        assertProtocolDisabled(protocolToDisable1, "after the roundtrip");
        assertProtocolDisabled(protocolToDisable2, "after the roundtrip");
    }

    private void assertProtocolDisabled(String protocolName, @CheckForNull String stage) throws AssertionError {
        assertThat(protocolName + " must be disabled. Stage=" + (stage != null ? stage : "undefined"),
                j.jenkins.getAgentProtocols(), not(hasItem(protocolName)));
    }

    private void assertProtocolEnabled(String protocolName, @CheckForNull String stage) throws AssertionError {
        assertThat(protocolName + " must be enabled. Stage=" + (stage != null ? stage : "undefined"),
                j.jenkins.getAgentProtocols(), hasItem(protocolName));
    }

    @TestExtension
    public static class MockOptInProtocol1 extends MockOptInProtocol {

        static final String NAME = "MOCK-OPTIN-1";

        @Override
        public String getName() {
            return NAME;
        }
    }

    @TestExtension
    public static class MockOptInProtocol2 extends MockOptInProtocol {

        static final String NAME = "MOCK-OPTIN-2";

        @Override
        public String getName() {
            return NAME;
        }
    }

    private abstract static class MockOptInProtocol extends AgentProtocol {
        @Override
        public boolean isOptIn() {
            return true;
        }

        @Override
        public void handle(Socket socket) throws IOException, InterruptedException {
            throw new IOException("This is a mock agent protocol. It cannot be used for connection");
        }
    }

    @TestExtension
    public static class MockOptOutProtocol1 extends MockOptOutProtocol {

        static final String NAME = "MOCK-OPTOUT-1";

        @Override
        public String getName() {
            return NAME;
        }
    }

    @TestExtension
    public static class MockOptOutProtocol2 extends MockOptOutProtocol {

        static final String NAME = "MOCK-OPTOUT-2";

        @Override
        public String getName() {
            return NAME;
        }
    }

    private abstract static class MockOptOutProtocol extends AgentProtocol {
        @Override
        public boolean isOptIn() {
            return false;
        }

        @Override
        public void handle(Socket socket) throws IOException, InterruptedException {
            throw new IOException("This is a mock agent protocol. It cannot be used for connection");
        }
    }

    @Issue("JENKINS-42577")
    @Test
    public void versionIsSavedInSave() throws Exception {
        Jenkins.VERSION = "1.0";
        j.jenkins.save();
        VersionNumber storedVersion = Jenkins.getStoredVersion();
        assertNotNull(storedVersion);
        assertEquals(storedVersion.toString(), "1.0");

        Jenkins.VERSION = null;
        j.jenkins.save();
        VersionNumber nullVersion = Jenkins.getStoredVersion();
        assertNull(nullVersion);
    }
}
