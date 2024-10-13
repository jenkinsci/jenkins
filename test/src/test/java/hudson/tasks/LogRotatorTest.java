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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

/**
 * Verifies that the last successful and stable builds of a job will be kept if requested.
 */
public class LogRotatorTest {

    @ClassRule
    public static BuildWatcher watcher = new BuildWatcher();

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void successVsFailure() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.setBuildDiscarder(new LogRotator(-1, 2, -1, -1));
        j.buildAndAssertSuccess(project); // #1
        project.getBuildersList().replaceBy(Set.of(new FailureBuilder()));
        j.buildAndAssertStatus(Result.FAILURE, project); // #2
        j.buildAndAssertStatus(Result.FAILURE, project); // #3
        assertEquals(1, numberOf(project.getLastSuccessfulBuild()));
        project.getBuildersList().replaceBy(Collections.emptySet());
        j.buildAndAssertSuccess(project); // #4
        assertEquals(4, numberOf(project.getLastSuccessfulBuild()));
        assertNull(project.getBuildByNumber(1));
        assertNull(project.getBuildByNumber(2));
        assertEquals(3, numberOf(project.getLastFailedBuild()));
    }

    @Test
    public void successVsFailureWithRemoveLastBuild() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        LogRotator logRotator = new LogRotator(-1, 1, -1, -1);
        logRotator.setRemoveLastBuild(true);
        project.setBuildDiscarder(logRotator);
        project.getPublishersList().replaceBy(Set.of(new TestsFail()));
        j.buildAndAssertStatus(Result.UNSTABLE, project); // #1
        project.getBuildersList().replaceBy(Set.of(new FailureBuilder()));
        j.buildAndAssertStatus(Result.FAILURE, project); // #2
        assertNull(project.getBuildByNumber(1));
        assertEquals(2, numberOf(project.getLastFailedBuild()));
    }

    @Test
    public void ableToDeleteCurrentBuild() throws Exception {
        var p = j.createFreeStyleProject();
        // Keep 0 builds, i.e. immediately delete builds as they complete.
        LogRotator logRotator = new LogRotator(-1, 0, -1, -1);
        logRotator.setRemoveLastBuild(true);
        p.setBuildDiscarder(logRotator);
        j.buildAndAssertStatus(Result.SUCCESS, p);
        assertNull(p.getBuildByNumber(1));
    }

    @Test
    @Issue("JENKINS-2417")
    public void stableVsUnstable() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.setBuildDiscarder(new LogRotator(-1, 2, -1, -1));
        j.buildAndAssertSuccess(project); // #1
        project.getPublishersList().replaceBy(Set.of(new TestsFail()));
        j.buildAndAssertStatus(Result.UNSTABLE, project); // #2
        j.buildAndAssertStatus(Result.UNSTABLE, project); // #3
        assertEquals(1, numberOf(project.getLastStableBuild()));
        project.getPublishersList().replaceBy(Collections.emptySet());
        j.buildAndAssertSuccess(project); // #4
        assertNull(project.getBuildByNumber(1));
        assertNull(project.getBuildByNumber(2));
    }

    @Test
    public void stableVsUnstableWithRemoveLastBuild() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        LogRotator logRotator = new LogRotator(-1, 1, -1, -1);
        logRotator.setRemoveLastBuild(true);
        project.setBuildDiscarder(logRotator);
        j.buildAndAssertSuccess(project); // #1
        project.getPublishersList().replaceBy(Set.of(new TestsFail()));
        j.buildAndAssertStatus(Result.UNSTABLE, project); // #2
        project.getBuildersList().replaceBy(Set.of(new FailureBuilder()));
        j.buildAndAssertStatus(Result.FAILURE, project); // #3
        assertNull(project.getBuildByNumber(1));
        assertNull(project.getBuildByNumber(2));
        assertEquals(-1, numberOf(project.getLastSuccessfulBuild()));
        assertEquals(3, numberOf(project.getLastBuild()));
    }

    @Test
    @Issue("JENKINS-834")
    public void artifactDelete() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.setBuildDiscarder(new LogRotator(-1, 6, -1, 2));
        project.getPublishersList().replaceBy(Set.of(new ArtifactArchiver("f", "", true, false)));
        j.buildAndAssertStatus(Result.FAILURE, project); // #1
        assertFalse(project.getBuildByNumber(1).getHasArtifacts());
        project.getBuildersList().replaceBy(Set.of(new CreateArtifact()));
        j.buildAndAssertSuccess(project); // #2
        assertTrue(project.getBuildByNumber(2).getHasArtifacts());
        project.getBuildersList().replaceBy(Arrays.asList(new CreateArtifact(), new FailureBuilder()));
        j.buildAndAssertStatus(Result.FAILURE, project); // #3
        assertTrue(project.getBuildByNumber(2).getHasArtifacts());
        assertTrue(project.getBuildByNumber(3).getHasArtifacts());
        j.buildAndAssertStatus(Result.FAILURE, project); // #4
        assertTrue(project.getBuildByNumber(2).getHasArtifacts());
        assertTrue(project.getBuildByNumber(3).getHasArtifacts());
        assertTrue(project.getBuildByNumber(4).getHasArtifacts());
        j.buildAndAssertStatus(Result.FAILURE, project); // #5
        assertTrue(project.getBuildByNumber(2).getHasArtifacts());
        assertFalse("no better than #4", project.getBuildByNumber(3).getHasArtifacts());
        assertTrue(project.getBuildByNumber(4).getHasArtifacts());
        assertTrue(project.getBuildByNumber(5).getHasArtifacts());
        project.getBuildersList().replaceBy(Set.of(new CreateArtifact()));
        j.buildAndAssertSuccess(project); // #6
        assertFalse("#2 is still lastSuccessful until #6 is complete", project.getBuildByNumber(2).getHasArtifacts());
        assertFalse(project.getBuildByNumber(3).getHasArtifacts());
        assertFalse(project.getBuildByNumber(4).getHasArtifacts());
        assertTrue(project.getBuildByNumber(5).getHasArtifacts());
        assertTrue(project.getBuildByNumber(6).getHasArtifacts());
        j.buildAndAssertSuccess(project); // #7
        assertNull(project.getBuildByNumber(1));
        assertNotNull(project.getBuildByNumber(2));
        assertFalse("lastSuccessful was #6 for ArtifactArchiver", project.getBuildByNumber(2).getHasArtifacts());
        assertFalse(project.getBuildByNumber(3).getHasArtifacts());
        assertFalse(project.getBuildByNumber(4).getHasArtifacts());
        assertFalse(project.getBuildByNumber(5).getHasArtifacts());
        assertTrue(project.getBuildByNumber(6).getHasArtifacts());
        assertTrue(project.getBuildByNumber(7).getHasArtifacts());
        j.buildAndAssertSuccess(project); // #8
        assertNull(project.getBuildByNumber(2));
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

    private static int numberOf(Run<?, ?> run) {
        return run != null ? run.getNumber() : -1;
    }

    static class TestsFail extends Publisher {
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
            build.setResult(Result.UNSTABLE);
            return true;
        }

        @Override
        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.NONE;
        }

        @Override
        public Descriptor<Publisher> getDescriptor() {
            return new Descriptor<>(TestsFail.class) {};
        }
    }

    public static class StallBuilder extends TestBuilder {

        private int syncBuildNumber;

        private final Object syncLock = new Object();

        private int waitBuildNumber;

        private final Object waitLock = new Object();

        private final ArtifactArchiver archiver = new ArtifactArchiver("f");

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
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
                    waitLock.wait(remaining / 1000000L, (int) (remaining % 1000000L));
                }
            }
        }

        @Override
        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.NONE;
        }

    }
}
