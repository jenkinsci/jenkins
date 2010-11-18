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

import hudson.util.TextFile;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * @author Kohsuke Kawaguchi
 */
public class JobTest extends HudsonTestCase {

    @SuppressWarnings("unchecked")
    public void testJobPropertySummaryIsShownInMainPage() throws Exception {
        AbstractProject project = createFreeStyleProject();
        project.addProperty(new JobPropertyImpl("NeedleInPage"));
                
        HtmlPage page = new WebClient().getPage(project);
        WebAssert.assertTextPresent(page, "NeedleInPage");
    }

    public void testBuildNumberSynchronization() throws Exception {
        AbstractProject project = createFreeStyleProject();
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
    public void testReadPermission() throws Exception {
        WebClient wc = new WebClient();
        try {
            HtmlPage page = wc.goTo("job/testJob/");
            fail("getJob bypassed Item.READ permission: " + page.getTitleText());
        } catch (FailingHttpStatusCodeException expected) { }
        try {
            HtmlPage page = wc.goTo("jobCaseInsensitive/testJob/");
            fail("getJobCaseInsensitive bypassed Item.READ permission: " + page.getTitleText());
        } catch (FailingHttpStatusCodeException expected) { }
        wc.login("joe");  // Has Item.READ permission
        // Verify we can access both URLs:
        wc.goTo("job/testJob/");
        wc.goTo("jobCaseInsensitive/TESTJOB/");
    }

    @LocalData
    public void testConfigDotXmlPermission() throws Exception {
        hudson.setCrumbIssuer(null);
        WebClient wc = new WebClient();
        boolean saveEnabled = Item.EXTENDED_READ.getEnabled();
        Item.EXTENDED_READ.setEnabled(true);
        try {
            try {
                wc.goTo("job/testJob/config.xml", "text/plain");
                fail("doConfigDotXml bypassed EXTENDED_READ permission");
            } catch (FailingHttpStatusCodeException expected) {
                assertEquals("403 for no permission", 403, expected.getStatusCode());
            }
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

    private static void tryConfigDotXml(WebClient wc, int status, String msg) throws Exception {
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

    @LocalData @Bug(6371)
    public void testGetArtifactsUpTo() throws Exception {
        // There was a bug where intermediate directories were counted,
        // so too few artifacts were returned.
        Run r = hudson.getItemByFullName("testJob", Job.class).getLastCompletedBuild();
        assertEquals(3, r.getArtifacts().size());
        assertEquals(3, r.getArtifactsUpTo(3).size());
        assertEquals(2, r.getArtifactsUpTo(2).size());
        assertEquals(1, r.getArtifactsUpTo(1).size());
    }
}
