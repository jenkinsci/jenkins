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
package hudson.tasks;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.queue.QueueTaskFuture;
import hudson.tasks.ArtifactArchiverTest.CreateArtifact;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

/**
 * Verifies that the last successful and stable builds of a job will be kept if requested.
 */
public class LogRotatorTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void successVsFailure() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.setLogRotator(new LogRotator(-1, 2, -1, -1));
        assertEquals(Result.SUCCESS, build(project)); // #1
        project.getBuildersList().replaceBy(Collections.singleton(new FailureBuilder()));
        assertEquals(Result.FAILURE, build(project)); // #2
        assertEquals(Result.FAILURE, build(project)); // #3
        assertEquals(1, numberOf(project.getLastSuccessfulBuild()));
        project.getBuildersList().replaceBy(Collections.<Builder>emptySet());
        assertEquals(Result.SUCCESS, build(project)); // #4
        assertEquals(4, numberOf(project.getLastSuccessfulBuild()));
        assertEquals(null, project.getBuildByNumber(1));
        assertEquals(null, project.getBuildByNumber(2));
        assertEquals(3, numberOf(project.getLastFailedBuild()));
    }

    @Test
    @Issue("JENKINS-2417")
    public void stableVsUnstable() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.setLogRotator(new LogRotator(-1, 2, -1, -1));
        assertEquals(Result.SUCCESS, build(project)); // #1
        project.getPublishersList().replaceBy(Collections.singleton(new TestsFail()));
        assertEquals(Result.UNSTABLE, build(project)); // #2
        assertEquals(Result.UNSTABLE, build(project)); // #3
        assertEquals(1, numberOf(project.getLastStableBuild()));
        project.getPublishersList().replaceBy(Collections.<Publisher>emptySet());
        assertEquals(Result.SUCCESS, build(project)); // #4
        assertEquals(null, project.getBuildByNumber(1));
        assertEquals(null, project.getBuildByNumber(2));
    }

    @Test
    @Issue("JENKINS-834")
    public void artifactDelete() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.setLogRotator(new LogRotator(-1, 6, -1, 2));
        project.getPublishersList().replaceBy(Collections.singleton(new ArtifactArchiver("f", "", true, false)));
        assertEquals("(no artifacts)", Result.FAILURE, build(project)); // #1
        assertFalse(project.getBuildByNumber(1).getHasArtifacts());
        project.getBuildersList().replaceBy(Collections.singleton(new CreateArtifact()));
        assertEquals(Result.SUCCESS, build(project)); // #2
        assertTrue(project.getBuildByNumber(2).getHasArtifacts());
        project.getBuildersList().replaceBy(Arrays.asList(new CreateArtifact(), new FailureBuilder()));
        assertEquals(Result.FAILURE, build(project)); // #3
        assertTrue(project.getBuildByNumber(2).getHasArtifacts());
        assertTrue(project.getBuildByNumber(3).getHasArtifacts());
        assertEquals(Result.FAILURE, build(project)); // #4
        assertTrue(project.getBuildByNumber(2).getHasArtifacts());
        assertTrue(project.getBuildByNumber(3).getHasArtifacts());
        assertTrue(project.getBuildByNumber(4).getHasArtifacts());
        assertEquals(Result.FAILURE, build(project)); // #5
        assertTrue(project.getBuildByNumber(2).getHasArtifacts());
        assertFalse("no better than #4", project.getBuildByNumber(3).getHasArtifacts());
        assertTrue(project.getBuildByNumber(4).getHasArtifacts());
        assertTrue(project.getBuildByNumber(5).getHasArtifacts());
        project.getBuildersList().replaceBy(Collections.singleton(new CreateArtifact()));
        assertEquals(Result.SUCCESS, build(project)); // #6
        assertFalse("#2 is still lastSuccessful until #6 is complete", project.getBuildByNumber(2).getHasArtifacts());
        assertFalse(project.getBuildByNumber(3).getHasArtifacts());
        assertFalse(project.getBuildByNumber(4).getHasArtifacts());
        assertTrue(project.getBuildByNumber(5).getHasArtifacts());
        assertTrue(project.getBuildByNumber(6).getHasArtifacts());
        assertEquals(Result.SUCCESS, build(project)); // #7
        assertEquals(null, project.getBuildByNumber(1));
        assertNotNull(project.getBuildByNumber(2));
        assertFalse("lastSuccessful was #6 for ArtifactArchiver", project.getBuildByNumber(2).getHasArtifacts());
        assertFalse(project.getBuildByNumber(3).getHasArtifacts());
        assertFalse(project.getBuildByNumber(4).getHasArtifacts());
        assertFalse(project.getBuildByNumber(5).getHasArtifacts());
        assertTrue(project.getBuildByNumber(6).getHasArtifacts());
        assertTrue(project.getBuildByNumber(7).getHasArtifacts());
        assertEquals(Result.SUCCESS, build(project)); // #8
        assertEquals(null, project.getBuildByNumber(2));
        assertNotNull(project.getBuildByNumber(3));
        assertFalse(project.getBuildByNumber(3).getHasArtifacts());
        assertFalse(project.getBuildByNumber(4).getHasArtifacts());
        assertFalse(project.getBuildByNumber(5).getHasArtifacts());
        assertFalse(project.getBuildByNumber(6).getHasArtifacts());
        assertTrue(project.getBuildByNumber(7).getHasArtifacts());
        assertTrue(project.getBuildByNumber(8).getHasArtifacts());
    }
    
    @Test
    @Issue("JENKINS-27836")
    public void artifactsRetainedWhileBuilding() throws Exception {
        j.getInstance().setNumExecutors(3);
        FreeStyleProject p = j.createFreeStyleProject();
        p.setBuildDiscarder(new LogRotator(-1, 3, -1, 1));
        StallBuilder sync = new StallBuilder();
        p.getBuildersList().replaceBy(Arrays.asList(new CreateArtifact(), sync));
        p.setConcurrentBuild(true);
        QueueTaskFuture<FreeStyleBuild> futureRun1 = p.scheduleBuild2(0);
        FreeStyleBuild run1 = futureRun1.waitForStart();
        sync.waitFor(run1.getNumber(), 1, TimeUnit.SECONDS);
        QueueTaskFuture<FreeStyleBuild> futureRun2 = p.scheduleBuild2(0);
        FreeStyleBuild run2 = futureRun2.waitForStart();
        sync.waitFor(run2.getNumber(), 1, TimeUnit.SECONDS);
        QueueTaskFuture<FreeStyleBuild> futureRun3 = p.scheduleBuild2(0);
        FreeStyleBuild run3 = futureRun3.waitForStart();
        sync.waitFor(run3.getNumber(), 1, TimeUnit.SECONDS);
        assertThat("we haven't released run1's guard", run1.isBuilding(), is(true));
        assertThat("we haven't released run2's guard", run2.isBuilding(), is(true));
        assertThat("we haven't released run3's guard", run3.isBuilding(), is(true));
        assertThat("we have artifacts in run1", run1.getHasArtifacts(), is(true));
        assertThat("we have artifacts in run2", run2.getHasArtifacts(), is(true));
        assertThat("we have artifacts in run3", run3.getHasArtifacts(), is(true));
        sync.release(run1.getNumber());
        futureRun1.get();
        assertThat("we have released run1's guard", run1.isBuilding(), is(false));
        assertThat("we haven't released run2's guard", run2.isBuilding(), is(true));
        assertThat("we haven't released run3's guard", run3.isBuilding(), is(true));
        assertThat("run1 is last stable build", p.getLastStableBuild(), is(run1));
        assertThat("run1 is last successful build", p.getLastSuccessfulBuild(), is(run1));
        assertThat("we have artifacts in run1", run1.getHasArtifacts(), is(true));
        assertThat("CRITICAL ASSERTION: we have artifacts in run2", run2.getHasArtifacts(), is(true)); 
        assertThat("we have artifacts in run3", run3.getHasArtifacts(), is(true));
        sync.release(run2.getNumber());
        futureRun2.get();
        assertThat("we have released run2's guard", run2.isBuilding(), is(false));
        assertThat("we haven't released run3's guard", run3.isBuilding(), is(true));
        assertThat("we have no artifacts in run1", run1.getHasArtifacts(), is(false));
        assertThat("run2 is last stable build", p.getLastStableBuild(), is(run2));
        assertThat("run2 is last successful build", p.getLastSuccessfulBuild(), is(run2));
        assertThat("we have artifacts in run2", run2.getHasArtifacts(), is(true));
        assertThat("we have artifacts in run3", run3.getHasArtifacts(), is(true));
        sync.release(run3.getNumber());
        futureRun3.get();
        assertThat("we have released run3's guard", run3.isBuilding(), is(false));
        assertThat("we have no artifacts in run1", run1.getHasArtifacts(), is(false));
        assertThat("we have no artifacts in run2", run2.getHasArtifacts(), is(false));
        assertThat("run3 is last stable build", p.getLastStableBuild(), is(run3));
        assertThat("run3 is last successful build", p.getLastSuccessfulBuild(), is(run3));
        assertThat("we have artifacts in run3", run3.getHasArtifacts(), is(true));
    }

    static Result build(FreeStyleProject project) throws Exception {
        return project.scheduleBuild2(0).get().getResult();
    }

    private static int numberOf(Run<?,?> run) {
        return run != null ? run.getNumber() : -1;
    }

    static class TestsFail extends Publisher {
        public @Override boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) {
            build.setResult(Result.UNSTABLE);
            return true;
        }

        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.NONE;
        }

        public Descriptor<Publisher> getDescriptor() {
            return new Descriptor<Publisher>(TestsFail.class) {
                public String getDisplayName() {
                    return "TestsFail";
                }
            };
        }
    }
    
    static class StallBuilder extends TestBuilder {
        
        private int syncBuildNumber;
        
        private final Object syncLock = new Object();
        
        private int waitBuildNumber;
        
        private final Object waitLock = new Object();
        
        private final ArtifactArchiver archiver = new ArtifactArchiver("f");

        public @Override boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener)
                throws IOException, InterruptedException {
            archiver.perform(build, launcher, listener);
            Logger.getAnonymousLogger().log(Level.INFO, "Building #{0}", build.getNumber());
            synchronized (waitLock) {
                if (waitBuildNumber < build.getNumber()) {
                    waitBuildNumber = build.getNumber();
                    waitLock.notifyAll();
                }
            }
            Logger.getAnonymousLogger().log(Level.INFO, "Waiting #{0}", build.getNumber());
            synchronized (syncLock) {
                while (build.getNumber() > syncBuildNumber) {
                    try {
                        syncLock.wait(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace(listener.fatalError("Interrupted: %s", e.getMessage()));
                        return false;
                    }
                }
            }
            Logger.getAnonymousLogger().log(Level.INFO, "Done #{0}", build.getNumber());
            return true;
        }
        
        public void release(int upToBuildNumber) {
            synchronized (syncLock) {
                if (syncBuildNumber < upToBuildNumber) {
                    Logger.getAnonymousLogger().log(Level.INFO, "Signal #{0}", upToBuildNumber);
                    syncBuildNumber = upToBuildNumber;
                    syncLock.notifyAll();
                }
            }
        }
        
        public void waitFor(int buildNumber, long timeout, TimeUnit units) throws TimeoutException,
                InterruptedException {
            long giveUp = System.nanoTime() + units.toNanos(timeout);
            synchronized (waitLock) {
                while (waitBuildNumber < buildNumber) {
                    long remaining = giveUp - System.nanoTime();
                    if (remaining < 0) {
                        throw new TimeoutException();
                    }
                    waitLock.wait(remaining/1000000L, (int)(remaining%1000000L));
                }
            }
        }

        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.NONE;
        }

    }
}
