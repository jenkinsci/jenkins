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
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
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
import hudson.model.Failure;
import hudson.model.RestartListener;
import hudson.model.RootAction;
import hudson.model.UnprotectedRootAction;
import hudson.model.User;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.util.HttpResponses;
import hudson.model.FreeStyleProject;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.LegacySecurityRealm;
import hudson.security.Permission;
import hudson.slaves.ComputerListener;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import hudson.util.FormValidation;

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

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * @author kingfai
 *
 */
public class JenkinsTest {

    @Rule public JenkinsRule j = new JenkinsRule();

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
        MavenModuleSet m = j.createMavenProject();
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
        j.jenkins.setSecurityRealm(new LegacySecurityRealm());
        GlobalMatrixAuthorizationStrategy gmas = new GlobalMatrixAuthorizationStrategy() {
            @Override public boolean hasPermission(String sid, Permission p) {
                return p == Jenkins.RUN_SCRIPTS ? hasExplicitPermission(sid, p) : super.hasPermission(sid, p);
            }
        };
        gmas.add(Jenkins.ADMINISTER, "alice");
        gmas.add(Jenkins.RUN_SCRIPTS, "alice");
        gmas.add(Jenkins.READ, "bob");
        gmas.add(Jenkins.ADMINISTER, "charlie");
        j.jenkins.setAuthorizationStrategy(gmas);
        WebClient wc = j.createWebClient();
        wc.login("alice");
        wc.goTo("script");
        wc.assertFails("script?script=System.setProperty('hack','me')", HttpURLConnection.HTTP_BAD_METHOD);
        assertNull(System.getProperty("hack"));
        WebRequest req = new WebRequest(new URL(wc.getContextPath() + "script?script=System.setProperty('hack','me')"), HttpMethod.POST);
        req.setEncodingType(null);
        wc.getPage(wc.addCrumb(req));
        assertEquals("me", System.getProperty("hack"));
        wc.assertFails("scriptText?script=System.setProperty('hack','me')", HttpURLConnection.HTTP_BAD_METHOD);
        req = new WebRequest(new URL(wc.getContextPath() + "scriptText?script=System.setProperty('huck','you')"), HttpMethod.POST);
        req.setEncodingType(null);
        wc.getPage(wc.addCrumb(req));
        assertEquals("you", System.getProperty("huck"));
        wc.login("bob");
        wc.assertFails("script", HttpURLConnection.HTTP_FORBIDDEN);
        wc.login("charlie");
        wc.assertFails("script", HttpURLConnection.HTTP_FORBIDDEN);
    }

    @Test
    public void testDoEval() throws Exception {
        j.jenkins.setSecurityRealm(new LegacySecurityRealm());
        GlobalMatrixAuthorizationStrategy gmas = new GlobalMatrixAuthorizationStrategy() {
            @Override public boolean hasPermission(String sid, Permission p) {
                return p == Jenkins.RUN_SCRIPTS ? hasExplicitPermission(sid, p) : super.hasPermission(sid, p);
            }
        };
        gmas.add(Jenkins.ADMINISTER, "alice");
        gmas.add(Jenkins.RUN_SCRIPTS, "alice");
        gmas.add(Jenkins.READ, "bob");
        gmas.add(Jenkins.ADMINISTER, "charlie");
        j.jenkins.setAuthorizationStrategy(gmas);
        WebClient wc = j.createWebClient();
        wc.login("alice");
        wc.assertFails("eval", HttpURLConnection.HTTP_BAD_METHOD);
        assertEquals("3", eval(wc));
        wc.login("bob");
        try {
            eval(wc);
            fail("bob has only READ");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(HttpURLConnection.HTTP_FORBIDDEN, e.getStatusCode());
        }
        wc.login("charlie");
        try {
            eval(wc);
            fail("charlie has ADMINISTER but not RUN_SCRIPTS");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(HttpURLConnection.HTTP_FORBIDDEN, e.getStatusCode());
        }
    }
    private String eval(WebClient wc) throws Exception {
        WebRequest req = new WebRequest(wc.createCrumbedUrl("eval"), HttpMethod.POST);
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
        assertTrue(!Jenkins.getInstance().getACL().hasPermission(Jenkins.ANONYMOUS,Jenkins.READ));

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
        DumbSlave slave = j.createSlave();
        URL url = new URL(j.getURL(), "computer/" + slave.getNodeName() + "/scriptText?script=println(42)");

        WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        WebRequest req = new WebRequest(url, HttpMethod.POST);
        req.setEncodingType(null);
        Page page = wc.getPage(wc.addCrumb(req));
        WebResponse rsp = page.getWebResponse();

        assertThat(rsp.getContentAsString(), containsString("Node is offline"));
        assertThat(rsp.getStatusCode(), equalTo(404));
    }
}
