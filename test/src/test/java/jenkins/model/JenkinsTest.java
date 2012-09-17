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
import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import hudson.model.UnprotectedRootAction;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import hudson.util.HttpResponses;
import junit.framework.Assert;
import hudson.model.FreeStyleProject;
import hudson.util.FormValidation;

import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.HttpResponse;
import org.xml.sax.SAXException;

import java.io.IOException;

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
        try {
            wc.goTo("/foobar-zot/");
            fail();
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(500,e.getStatusCode());
        }

        assertEquals(3,jenkins.getExtensionList(RootAction.class).get(RootActionImpl.class).count);
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
