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

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.RootAction;
import hudson.model.UnprotectedRootAction;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import hudson.util.HttpResponses;
import hudson.model.FreeStyleProject;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.LegacySecurityRealm;
import hudson.security.Permission;
import hudson.util.FormValidation;

import org.junit.Assert;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.HttpResponse;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * @author kingfai
 *
 */
public class JenkinsTest extends HudsonTestCase {

    @Test
    public void testIsDisplayNameUniqueTrue() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        FreeStyleProject curProject = createFreeStyleProject(curJobName);
        curProject.setDisplayName("currentProjectDisplayName");
        
        FreeStyleProject p = createFreeStyleProject(jobName);
        p.setDisplayName("displayName");
        
        Jenkins jenkins = Jenkins.getInstance();
        Assert.assertTrue(jenkins.isDisplayNameUnique("displayName1", curJobName));
        Assert.assertTrue(jenkins.isDisplayNameUnique(jobName, curJobName));
    }

    @Test
    public void testIsDisplayNameUniqueFalse() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        final String displayName = "displayName";
        
        FreeStyleProject curProject = createFreeStyleProject(curJobName);
        curProject.setDisplayName("currentProjectDisplayName");
        
        FreeStyleProject p = createFreeStyleProject(jobName);
        p.setDisplayName(displayName);
        
        Jenkins jenkins = Jenkins.getInstance();
        Assert.assertFalse(jenkins.isDisplayNameUnique(displayName, curJobName));
    }
    
    @Test
    public void testIsDisplayNameUniqueSameAsCurrentJob() throws Exception {
        final String curJobName = "curJobName";
        final String displayName = "currentProjectDisplayName";
        
        FreeStyleProject curProject = createFreeStyleProject(curJobName);
        curProject.setDisplayName(displayName);
        
        Jenkins jenkins = Jenkins.getInstance();
        // should be true as we don't test against the current job
        Assert.assertTrue(jenkins.isDisplayNameUnique(displayName, curJobName));
        
    }
    
    @Test
    public void testIsNameUniqueTrue() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        createFreeStyleProject(curJobName);        
        createFreeStyleProject(jobName);
        
        Jenkins jenkins = Jenkins.getInstance();
        Assert.assertTrue(jenkins.isNameUnique("jobName1", curJobName));
    }

    @Test
    public void testIsNameUniqueFalse() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        createFreeStyleProject(curJobName);        
        createFreeStyleProject(jobName);
        
        Jenkins jenkins = Jenkins.getInstance();
        Assert.assertFalse(jenkins.isNameUnique(jobName, curJobName));
    }

    @Test
    public void testIsNameUniqueSameAsCurrentJob() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        createFreeStyleProject(curJobName);        
        createFreeStyleProject(jobName);
        
        Jenkins jenkins = Jenkins.getInstance();
        // true because we don't test against the current job
        Assert.assertTrue(jenkins.isNameUnique(curJobName, curJobName));        
    }
    
    @Test
    public void testDoCheckDisplayNameUnique() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        FreeStyleProject curProject = createFreeStyleProject(curJobName);
        curProject.setDisplayName("currentProjectDisplayName");
        
        FreeStyleProject p = createFreeStyleProject(jobName);
        p.setDisplayName("displayName");
        
        Jenkins jenkins = Jenkins.getInstance();
        FormValidation v = jenkins.doCheckDisplayName("1displayName", curJobName);
        Assert.assertEquals(FormValidation.ok(), v);
    }

    @Test
    public void testDoCheckDisplayNameSameAsDisplayName() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        final String displayName = "displayName";
        FreeStyleProject curProject = createFreeStyleProject(curJobName);
        curProject.setDisplayName("currentProjectDisplayName");
        
        FreeStyleProject p = createFreeStyleProject(jobName);
        p.setDisplayName(displayName);
        
        Jenkins jenkins = Jenkins.getInstance();
        FormValidation v = jenkins.doCheckDisplayName(displayName, curJobName);
        Assert.assertEquals(FormValidation.Kind.WARNING, v.kind);        
    }

    @Test
    public void testDoCheckDisplayNameSameAsJobName() throws Exception {
        final String curJobName = "curJobName";
        final String jobName = "jobName";
        final String displayName = "displayName";
        FreeStyleProject curProject = createFreeStyleProject(curJobName);
        curProject.setDisplayName("currentProjectDisplayName");
        
        FreeStyleProject p = createFreeStyleProject(jobName);
        p.setDisplayName(displayName);
        
        Jenkins jenkins = Jenkins.getInstance();
        FormValidation v = jenkins.doCheckDisplayName(jobName, curJobName);
        Assert.assertEquals(FormValidation.Kind.WARNING, v.kind);                
    }

    @Test
    public void testDoCheckViewName_GoodName() throws Exception {
        String[] viewNames = new String[] {
            "", "Jenkins"    
        };
        
        Jenkins jenkins = Jenkins.getInstance();
        for (String viewName : viewNames) {
            FormValidation v = jenkins.doCheckViewName(viewName);
            Assert.assertEquals(FormValidation.Kind.OK, v.kind);
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
            Assert.assertEquals(FormValidation.Kind.ERROR, v.kind);
        }
    }
    
    @Bug(12251)
    public void testItemFullNameExpansion() throws Exception {
        HtmlForm f = createWebClient().goTo("/configure").getFormByName("config");
        f.getInputByName("_.rawBuildsDir").setValueAttribute("${JENKINS_HOME}/test12251_builds/${ITEM_FULL_NAME}");
        f.getInputByName("_.rawWorkspaceDir").setValueAttribute("${JENKINS_HOME}/test12251_ws/${ITEM_FULL_NAME}");
        submit(f);

        // build a dummy project
        MavenModuleSet m = createMavenProject();
        m.setScm(new ExtractResourceSCM(getClass().getResource("/simple-projects.zip")));
        MavenModuleSetBuild b = m.scheduleBuild2(0).get();

        // make sure these changes are effective
        assertTrue(b.getWorkspace().getRemote().contains("test12251_ws"));
        assertTrue(b.getRootDir().toString().contains("test12251_builds"));
    }

    /**
     * Makes sure access to "/foobar" for UnprotectedRootAction gets through.
     */
    @Bug(14113)
    public void testUnprotectedRootAction() throws Exception {
        jenkins.setSecurityRealm(createDummySecurityRealm());
        jenkins.setAuthorizationStrategy(new FullControlOnceLoggedInAuthorizationStrategy());
        WebClient wc = createWebClient();
        wc.goTo("/foobar");
        wc.goTo("/foobar/");
        wc.goTo("/foobar/zot");

        // and make sure this fails
        wc.assertFails("/foobar-zot/", HttpURLConnection.HTTP_INTERNAL_ERROR);

        assertEquals(3,jenkins.getExtensionList(RootAction.class).get(RootActionImpl.class).count);
    }

    public void testDoScript() throws Exception {
        jenkins.setSecurityRealm(new LegacySecurityRealm());
        GlobalMatrixAuthorizationStrategy gmas = new GlobalMatrixAuthorizationStrategy() {
            @Override public boolean hasPermission(String sid, Permission p) {
                return p == Jenkins.RUN_SCRIPTS ? hasExplicitPermission(sid, p) : super.hasPermission(sid, p);
            }
        };
        gmas.add(Jenkins.ADMINISTER, "alice");
        gmas.add(Jenkins.RUN_SCRIPTS, "alice");
        gmas.add(Jenkins.READ, "bob");
        gmas.add(Jenkins.ADMINISTER, "charlie");
        jenkins.setAuthorizationStrategy(gmas);
        WebClient wc = createWebClient();
        wc.login("alice");
        wc.goTo("script");
        wc.assertFails("script?script=System.setProperty('hack','me')", HttpURLConnection.HTTP_BAD_METHOD);
        assertNull(System.getProperty("hack"));
        WebRequestSettings req = new WebRequestSettings(new URL(wc.getContextPath() + "script?script=System.setProperty('hack','me')"), HttpMethod.POST);
        wc.getPage(wc.addCrumb(req));
        assertEquals("me", System.getProperty("hack"));
        wc.assertFails("scriptText?script=System.setProperty('hack','me')", HttpURLConnection.HTTP_BAD_METHOD);
        req = new WebRequestSettings(new URL(wc.getContextPath() + "scriptText?script=System.setProperty('huck','you')"), HttpMethod.POST);
        wc.getPage(wc.addCrumb(req));
        assertEquals("you", System.getProperty("huck"));
        wc.login("bob");
        wc.assertFails("script", HttpURLConnection.HTTP_FORBIDDEN);
        wc.login("charlie");
        wc.assertFails("script", HttpURLConnection.HTTP_FORBIDDEN);
    }

    public void testDoEval() throws Exception {
        jenkins.setSecurityRealm(new LegacySecurityRealm());
        GlobalMatrixAuthorizationStrategy gmas = new GlobalMatrixAuthorizationStrategy() {
            @Override public boolean hasPermission(String sid, Permission p) {
                return p == Jenkins.RUN_SCRIPTS ? hasExplicitPermission(sid, p) : super.hasPermission(sid, p);
            }
        };
        gmas.add(Jenkins.ADMINISTER, "alice");
        gmas.add(Jenkins.RUN_SCRIPTS, "alice");
        gmas.add(Jenkins.READ, "bob");
        gmas.add(Jenkins.ADMINISTER, "charlie");
        jenkins.setAuthorizationStrategy(gmas);
        // Otherwise get "RuntimeException: Trying to set the request parameters, but the request body has already been specified;the two are mutually exclusive!" from WebRequestSettings.setRequestParameters when POSTing content:
        jenkins.setCrumbIssuer(null);
        WebClient wc = createWebClient();
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
        WebRequestSettings req = new WebRequestSettings(new URL(wc.getContextPath() + "eval"), HttpMethod.POST);
        req.setRequestBody("<j:jelly xmlns:j='jelly:core'>${1+2}</j:jelly>");
        return wc.getPage(/*wc.addCrumb(*/req/*)*/).getWebResponse().getContentAsString();
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
}
