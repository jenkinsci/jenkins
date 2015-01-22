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
package hudson.model;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebAssert;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.TextPage;

import hudson.util.TextFile;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import jenkins.model.ProjectNamingStrategy;

import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RunLoadCounter;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * @author Kohsuke Kawaguchi
 */
public class JobTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @SuppressWarnings("unchecked")
    @Test public void jobPropertySummaryIsShownInMainPage() throws Exception {
        AbstractProject project = j.createFreeStyleProject();
        project.addProperty(new JobPropertyImpl("NeedleInPage"));
                
        HtmlPage page = j.createWebClient().getPage(project);
        WebAssert.assertTextPresent(page, "NeedleInPage");
    }

    @Test public void buildNumberSynchronization() throws Exception {
        AbstractProject project = j.createFreeStyleProject();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch stopLatch = new CountDownLatch(2);
        BuildNumberSyncTester test1 = new BuildNumberSyncTester(project, startLatch, stopLatch, true);
        BuildNumberSyncTester test2 = new BuildNumberSyncTester(project, startLatch, stopLatch, false);
        new Thread(test1).start();
        new Thread(test2).start();

        startLatch.countDown();
        stopLatch.await();

        assertTrue(test1.message, test2.passed);
        assertTrue(test2.message, test2.passed);
    }

    public static class BuildNumberSyncTester implements Runnable {
        private final AbstractProject p;
        private final CountDownLatch start;
        private final CountDownLatch stop;
        private final boolean assign;

        String message;
        boolean passed;

        BuildNumberSyncTester(AbstractProject p, CountDownLatch l1, CountDownLatch l2, boolean b) {
            this.p = p;
            this.start = l1;
            this.stop = l2;
            this.assign = b;
            this.message = null;
            this.passed = false;
        }

        public void run() {
            try {
                start.await();

                for (int i = 0; i < 100; i++) {
                    int buildNumber = -1, savedBuildNumber = -1;
                    TextFile f;

                    synchronized (p) {
                        if (assign) {
                            buildNumber = p.assignBuildNumber();
                            f = p.getNextBuildNumberFile();
                            if (f == null) {
                                this.message = "Could not get build number file";
                                this.passed = false;
                                return;
                            }
                            savedBuildNumber = Integer.parseInt(f.readTrim());
                            if (buildNumber != (savedBuildNumber-1)) {
                                this.message = "Build numbers don't match (" + buildNumber + ", " + (savedBuildNumber-1) + ")";
                                this.passed = false;
                                return;
                            }
                        } else {
                            buildNumber = p.getNextBuildNumber() + 100;
                            p.updateNextBuildNumber(buildNumber);
                            f = p.getNextBuildNumberFile();
                            if (f == null) {
                                this.message = "Could not get build number file";
                                this.passed = false;
                                return;
                            }
                            savedBuildNumber = Integer.parseInt(f.readTrim());
                            if (buildNumber != savedBuildNumber) {
                                this.message = "Build numbers don't match (" + buildNumber + ", " + savedBuildNumber + ")";
                                this.passed = false;
                                return;
                            }
                        }
                    }
                }

                this.passed = true;
            }
            catch (InterruptedException e) {}
            catch (IOException e) {
                fail("Failed to assign build number");
            }
            finally {
                stop.countDown();
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static class JobPropertyImpl extends JobProperty<Job<?,?>> {
        public static DescriptorImpl DESCRIPTOR = new DescriptorImpl();
        private final String testString;
        
        public JobPropertyImpl(String testString) {
            this.testString = testString;
        }
        
        public String getTestString() {
            return testString;
        }

        @Override
        public JobPropertyDescriptor getDescriptor() {
            return DESCRIPTOR;
        }

        private static final class DescriptorImpl extends JobPropertyDescriptor {
            public String getDisplayName() {
                return "";
            }
        }
    }

    @LocalData
    @Test public void readPermission() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.assertFails("job/testJob/", HttpURLConnection.HTTP_NOT_FOUND);
        wc.assertFails("jobCaseInsensitive/testJob/", HttpURLConnection.HTTP_NOT_FOUND);
        wc.login("joe");  // Has Item.READ permission
        // Verify we can access both URLs:
        wc.goTo("job/testJob/");
        wc.goTo("jobCaseInsensitive/TESTJOB/");
    }

    @LocalData
    @Test public void configDotXmlPermission() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        JenkinsRule.WebClient wc = j.createWebClient();
        boolean saveEnabled = Item.EXTENDED_READ.getEnabled();
        Item.EXTENDED_READ.setEnabled(true);
        try {
            wc.assertFails("job/testJob/config.xml", HttpURLConnection.HTTP_FORBIDDEN);
            wc.login("alice");  // Has CONFIGURE and EXTENDED_READ permission
            tryConfigDotXml(wc, 500, "Both perms; should get 500");
            wc.login("bob");  // Has only CONFIGURE permission (this should imply EXTENDED_READ)
            tryConfigDotXml(wc, 500, "Config perm should imply EXTENDED_READ");
            wc.login("charlie");  // Has only EXTENDED_READ permission
            tryConfigDotXml(wc, 403, "No permission, should get 403");
        } finally {
            Item.EXTENDED_READ.setEnabled(saveEnabled);
        }
    }

    private static void tryConfigDotXml(JenkinsRule.WebClient wc, int status, String msg) throws Exception {
        // Verify we can GET the config.xml:
        wc.goTo("job/testJob/config.xml", "application/xml");
        // This page is a simple form to POST to /job/testJob/config.xml
        // But it posts invalid data so we expect 500 if we have permission, 403 if not
        HtmlPage page = wc.goTo("userContent/post.html");
        try {
            page.getForms().get(0).submit();
            fail("Expected exception: " + msg);
        } catch (FailingHttpStatusCodeException expected) {
            assertEquals(msg, status, expected.getStatusCode());
        }
        wc.goTo("logout");
    }

    @LocalData @Issue("JENKINS-6371")
    @Test public void getArtifactsUpTo() throws Exception {
        // There was a bug where intermediate directories were counted,
        // so too few artifacts were returned.
        Run r = j.jenkins.getItemByFullName("testJob", Job.class).getLastCompletedBuild();
        assertEquals(3, r.getArtifacts().size());
        assertEquals(3, r.getArtifactsUpTo(3).size());
        assertEquals(2, r.getArtifactsUpTo(2).size());
        assertEquals(1, r.getArtifactsUpTo(1).size());
    }

    @Issue("JENKINS-10182")
    @Test public void emptyDescriptionReturnsEmptyPage() throws Exception {
        // A NPE was thrown if a job had a null (empty) description.
        JenkinsRule.WebClient wc = j.createWebClient();
        FreeStyleProject project = j.createFreeStyleProject("project");
        project.setDescription("description");
        assertEquals("description", ((TextPage) wc.goTo("job/project/description", "text/plain")).getContent());
        project.setDescription(null);
        assertEquals("", ((TextPage) wc.goTo("job/project/description", "text/plain")).getContent());
    }
    
    @Test public void projectNamingStrategy() throws Exception {
        j.jenkins.setProjectNamingStrategy(new ProjectNamingStrategy.PatternProjectNamingStrategy("DUMMY.*", false));
        final FreeStyleProject p = j.createFreeStyleProject("DUMMY_project");
        assertNotNull("no project created", p);
        try {
            j.createFreeStyleProject("project");
            fail("should not get here, the project name is not allowed, therefore the creation must fail!");
        } catch (Failure e) {
            // OK, expected
        }finally{
            // set it back to the default naming strategy, otherwise all other tests would fail to create jobs!
            j.jenkins.setProjectNamingStrategy(ProjectNamingStrategy.DEFAULT_NAMING_STRATEGY);
        }
        j.createFreeStyleProject("project");
    }

    @Issue("JENKINS-16023")
    @Test public void getLastFailedBuild() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        RunLoadCounter.prepare(p);
        p.getBuildersList().add(new FailureBuilder());
        j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        p.getBuildersList().remove(FailureBuilder.class);
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(6, p.getLastSuccessfulBuild().getNumber());
        assertEquals(3, RunLoadCounter.assertMaxLoads(p, 1, new Callable<Integer>() {
            @Override public Integer call() throws Exception {
                return p.getLastFailedBuild().getNumber();
            }
        }).intValue());
    }

    @Issue("JENKINS-19764")
    @Test public void testRenameWithCustomBuildsDirWithSubdir() throws Exception {
        j.jenkins.setRawBuildsDir("${JENKINS_HOME}/builds/${ITEM_FULL_NAME}/builds");
        final FreeStyleProject p = j.createFreeStyleProject();
        p.scheduleBuild2(0).get();
        p.renameTo("different-name");
    }

}
