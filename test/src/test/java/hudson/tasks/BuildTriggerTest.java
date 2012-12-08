/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Alan Harder
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
package hudson.tasks;

import java.util.Map.Entry;
import java.util.Set;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Fingerprint.RangeSet;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.MockBuilder;

/**
 * Tests for hudson.tasks.BuildTrigger
 * @author Alan.Harder@sun.com
 */
public class BuildTriggerTest extends HudsonTestCase {

    private FreeStyleProject createDownstreamProject(String projectName) throws Exception {
        FreeStyleProject dp = createFreeStyleProject(projectName);

        // Hm, no setQuietPeriod, have to submit form..
        WebClient webClient = new WebClient();
        HtmlPage page = webClient.getPage(dp,"configure");
        HtmlForm form = page.getFormByName("config");
        form.getInputByName("hasCustomQuietPeriod").click();
        form.getInputByName("quiet_period").setValueAttribute("0");
        submit(form);
        assertEquals("set quiet period", 0, dp.getQuietPeriod());

        return dp;
    }

    private void doTriggerTest(boolean evenWhenUnstable, Result triggerResult,
            Result dontTriggerResult) throws Exception {
        FreeStyleProject p = createFreeStyleProject(),
                dp = createDownstreamProject("downstream");
        p.getPublishersList().add(new BuildTrigger("downstream", evenWhenUnstable));
        p.getBuildersList().add(new MockBuilder(dontTriggerResult));
        jenkins.rebuildDependencyGraph();

        // First build should not trigger downstream job
        FreeStyleBuild b = p.scheduleBuild2(0).get();
        assertNoDownstreamBuild(dp, b);

        // Next build should trigger downstream job
        p.getBuildersList().replace(new MockBuilder(triggerResult));
        b = p.scheduleBuild2(0).get();
        assertDownstreamBuild(dp, b);
    }

    private void assertNoDownstreamBuild(FreeStyleProject dp, Run<?,?> b) throws Exception {
        for (int i = 0; i < 3; i++) {
            Thread.sleep(200);
            assertTrue("downstream build should not run!  upstream log: " + getLog(b),
                       !dp.isInQueue() && !dp.isBuilding() && dp.getLastBuild()==null);
        }
    }

    private void assertDownstreamBuild(FreeStyleProject dp, Run<?,?> b) throws Exception {
        // Wait for downstream build
        for (int i = 0; dp.getLastBuild()==null && i < 20; i++) Thread.sleep(100);
        assertNotNull("downstream build didn't run.. upstream log: " + getLog(b), dp.getLastBuild());
    }

    public void testBuildTrigger() throws Exception {
        doTriggerTest(false, Result.SUCCESS, Result.UNSTABLE);
    }

    public void testTriggerEvenWhenUnstable() throws Exception {
        doTriggerTest(true, Result.UNSTABLE, Result.FAILURE);
    }

    private void doMavenTriggerTest(boolean evenWhenUnstable) throws Exception {
        FreeStyleProject dp = createDownstreamProject("downstream");
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.getPublishersList().add(new BuildTrigger("downstream", evenWhenUnstable));
        if (!evenWhenUnstable) {
            // Configure for UNSTABLE
            m.setGoals("clean test");
            m.setScm(new ExtractResourceSCM(getClass().getResource("maven-test-failure.zip")));
        } // otherwise do nothing which gets FAILURE
        // First build should not trigger downstream project
        MavenModuleSetBuild b = m.scheduleBuild2(0).get();
        assertNoDownstreamBuild(dp, b);

        if (evenWhenUnstable) {
            // Configure for UNSTABLE
            m.setGoals("clean test");
            m.setScm(new ExtractResourceSCM(getClass().getResource("maven-test-failure.zip")));
        } else {
            // Configure for SUCCESS
            m.setGoals("clean");
            m.setScm(new ExtractResourceSCM(getClass().getResource("maven-empty.zip")));
        }
        // Next build should trigger downstream project
        b = m.scheduleBuild2(0).get();
        assertDownstreamBuild(dp, b);
    }

    public void testMavenBuildTrigger() throws Exception {
        doMavenTriggerTest(false);
    }

    public void testMavenTriggerEvenWhenUnstable() throws Exception {
        doMavenTriggerTest(true);
    }

    public void testCircularDirectDownstreamBuildDoesNotLoopForever() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject("main");
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-empty.zip")));
        MavenModuleSet dp = createMavenProject("downstream");
        dp.setScm(new ExtractResourceSCM(getClass().getResource("maven-empty.zip")));
        m.setGoals("clean");
        dp.setGoals("clean");
        m.getPublishersList().add(new BuildTrigger("downstream", false));
        dp.getPublishersList().add(new BuildTrigger("main", false));
        m.scheduleBuild2(0).get();

        waitForProjectToBuild(dp);

        assertFalse(m.isBuilding());
        assertFalse(m.isInQueue());
    }

    public void testCircularNonDirectDownstreamBuildDoesNotLoopForever() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject("main");
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-empty.zip")));
        MavenModuleSet dp = createMavenProject("downstream");
        dp.setScm(new ExtractResourceSCM(getClass().getResource("maven-empty.zip")));
        MavenModuleSet dp2 = createMavenProject("downstream2");
        dp2.setScm(new ExtractResourceSCM(getClass().getResource("maven-empty.zip")));
        m.setGoals("clean");
        dp.setGoals("clean");
        dp2.setGoals("clean");
        m.getPublishersList().add(new BuildTrigger("downstream", false));
        dp.getPublishersList().add(new BuildTrigger("downstream2", false));
        dp2.getPublishersList().add(new BuildTrigger("main", false));
        m.scheduleBuild2(0).get();

        waitForProjectToBuild(dp);
        waitForProjectToBuild(dp2);

        assertFalse(m.isBuilding());
        assertFalse(m.isInQueue());
    }

    private void waitForProjectToBuild(MavenModuleSet project) throws InterruptedException {
        for (int i = 0; project.isInQueue() || project.isBuilding() && i < 40; i++) {
            Thread.sleep(200);
        }
        assertNotNull(project.getLastBuild());
    }
}
