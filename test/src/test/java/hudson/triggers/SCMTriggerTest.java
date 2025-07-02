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

package hudson.triggers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.scm.NullSCM;
import hudson.triggers.SCMTrigger.BuildAction;
import hudson.triggers.SCMTrigger.SCMTriggerCause;
import hudson.util.OneShotEvent;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import jenkins.scm.SCMDecisionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Alan Harder
 */
@WithJenkins
class SCMTriggerTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Make sure that SCMTrigger doesn't trigger another build when a build has just started,
     * but not yet completed its SCM update.
     */
    @Test
    @Issue("JENKINS-2671")
    void simultaneousPollAndBuild() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        // used to coordinate polling and check out
        final OneShotEvent checkoutStarted = new OneShotEvent();

        p.setScm(new TestSCM(checkoutStarted));

        Future<FreeStyleBuild> build = p.scheduleBuild2(0, new Cause.UserCause());
        assertNotNull(build);
        checkoutStarted.block();
        assertFalse(p.pollSCMChanges(StreamTaskListener.fromStdout()), "SCM-poll after build has started should wait until that build finishes SCM-update");
        build.get();  // let mock build finish
    }

    /**
     * Make sure that SCMTrigger doesn't poll when there is a polling veto in place.
     */
    @Test
    @Issue("JENKINS-36123")
    void pollingExcludedByExtensionPoint() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        PollDecisionHandlerImpl handler =
                ExtensionList.lookup(SCMDecisionHandler.class).get(PollDecisionHandlerImpl.class);
        handler.blacklist.add(p);

        // used to coordinate polling and check out
        final OneShotEvent checkoutStarted = new OneShotEvent();

        p.setScm(new TestSCM(checkoutStarted));

        assertFalse(p.pollSCMChanges(StreamTaskListener.fromStdout()), "SCM-poll with blacklist should report no changes");
        handler.blacklist.remove(p);
        assertTrue(p.pollSCMChanges(StreamTaskListener.fromStdout()), "SCM-poll with blacklist removed should report changes");
    }

    private static class TestSCM extends NullSCM {
        private volatile int myRev = 1;
        private final OneShotEvent checkoutStarted;

        TestSCM(OneShotEvent checkoutStarted) {
            this.checkoutStarted = checkoutStarted;
        }

        @Override
        public synchronized boolean pollChanges(AbstractProject project, Launcher launcher, FilePath dir, TaskListener listener) {
            return myRev < 2;
        }

        @Override
        public boolean checkout(AbstractBuild<?, ?> build, Launcher launcher, FilePath remoteDir, BuildListener listener, File changeLogFile) throws IOException, InterruptedException {
            checkoutStarted.signal();
            Thread.sleep(400);  // processing time for mock update
            synchronized (this) { if (myRev < 2) myRev = 2; }
            return super.checkout(build, launcher, remoteDir, listener, changeLogFile);
        }
    }

    /**
     * Make sure that only one polling result shows up per build.
     */
    @Test
    @Issue("JENKINS-7649")
    void multiplePollingOneBuildAction() throws Exception {
        final OneShotEvent buildStarted = new OneShotEvent();
        final OneShotEvent buildShouldComplete = new OneShotEvent();
        FreeStyleProject p = j.createFreeStyleProject();
        // Make build sleep a while so it blocks new builds
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
                buildStarted.signal();
                buildShouldComplete.block();
                return true;
            }
        });

        SCMTrigger t = new SCMTrigger("@daily");
        t.start(p, true);
        p.addTrigger(t);

        // Start one build to block others
        p.scheduleBuild2(0, new Cause.UserCause()).waitForStart();
        buildStarted.block(); // wait for the build to really start

        // Schedule a new build, and trigger it many ways while it sits in queue
        Future<FreeStyleBuild> fb = p.scheduleBuild2(0, new Cause.UserCause());
        assertNotNull(fb);
        assertNotNull(p.scheduleBuild2(0, new SCMTriggerCause("First poll")));
        assertNotNull(p.scheduleBuild2(0, new SCMTriggerCause("Second poll")));
        assertNotNull(p.scheduleBuild2(0, new SCMTriggerCause("Third poll")));

        // Wait for 2nd build to finish
        buildShouldComplete.signal();
        FreeStyleBuild build = fb.get();

        List<BuildAction> ba = build.getActions(BuildAction.class);

        assertEquals(1, ba.size(), "There should only be one BuildAction.");
    }

    @TestExtension
    public static class PollDecisionHandlerImpl extends SCMDecisionHandler {

        Set<Item> blacklist = new HashSet<>();

        @Override
        public boolean shouldPoll(@NonNull Item item) {
            return !blacklist.contains(item);
        }
    }
}
