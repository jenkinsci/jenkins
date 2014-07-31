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

import hudson.FilePath;
import hudson.Launcher;
import hudson.util.OneShotEvent;
import hudson.util.StreamTaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;
import hudson.model.Cause.UserCause;
import hudson.scm.NullSCM;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.triggers.SCMTrigger.SCMTriggerCause;
import hudson.triggers.SCMTrigger.BuildAction;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.SingleFileSCM;
import org.jvnet.hudson.test.TestBuilder;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Future;
import java.util.List;
/**
 * @author Alan Harder
 */
public class SCMTriggerTest extends HudsonTestCase {
    /**
     * Make sure that SCMTrigger doesn't trigger another build when a build has just started,
     * but not yet completed its SCM update.
     */
    @Bug(2671)
    public void testSimultaneousPollAndBuild() throws Exception {
        FreeStyleProject p = createFreeStyleProject();

        // used to coordinate polling and check out
        final OneShotEvent checkoutStarted = new OneShotEvent();

        p.setScm(new TestSCM(checkoutStarted));

        Future<FreeStyleBuild> build = p.scheduleBuild2(0, new Cause.UserCause());
        checkoutStarted.block();
        assertFalse("SCM-poll after build has started should wait until that build finishes SCM-update", p.pollSCMChanges(StreamTaskListener.fromStdout()));
        build.get();  // let mock build finish
    }

    private static class TestSCM extends NullSCM {
        private volatile int myRev = 1;
        private final OneShotEvent checkoutStarted;

        public TestSCM(OneShotEvent checkoutStarted) {
            this.checkoutStarted = checkoutStarted;
        }

        @Override synchronized
        public boolean pollChanges(AbstractProject project, Launcher launcher, FilePath dir, TaskListener listener) throws IOException {
            return myRev < 2;
        }

        @Override
        public boolean checkout(AbstractBuild<?,?> build, Launcher launcher, FilePath remoteDir, BuildListener listener, File changeLogFile) throws IOException, InterruptedException {
            checkoutStarted.signal();
            Thread.sleep(400);  // processing time for mock update
            synchronized (this) { if (myRev < 2) myRev = 2; }
            return super.checkout(build, launcher, remoteDir, listener, changeLogFile);
        }
    }

    /**
     * Make sure that only one polling result shows up per build.
     */
    @Bug(7649)
    public void testMultiplePollingOneBuildAction() throws Exception {
        final OneShotEvent buildStarted = new OneShotEvent();
        final OneShotEvent buildShouldComplete = new OneShotEvent();
        FreeStyleProject p = createFreeStyleProject();
        // Make build sleep a while so it blocks new builds
        p.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                buildStarted.signal();
                buildShouldComplete.block();
                return true;
            }
        });

        SCMTrigger t = new SCMTrigger("@daily");
        t.start(p,true);
        p.addTrigger(t);

        // Start one build to block others
        assertTrue(p.scheduleBuild(new UserCause()));
        buildStarted.block(); // wait for the build to really start

        // Schedule a new build, and trigger it many ways while it sits in queue
        Future<FreeStyleBuild> fb = p.scheduleBuild2(0, new UserCause());
        assertNotNull(fb);
        assertTrue(p.scheduleBuild(new SCMTriggerCause("First poll")));
        assertTrue(p.scheduleBuild(new SCMTriggerCause("Second poll")));
        assertTrue(p.scheduleBuild(new SCMTriggerCause("Third poll")));

        // Wait for 2nd build to finish
        buildShouldComplete.signal();
        FreeStyleBuild build = fb.get();

        List<BuildAction> ba = build.getActions(BuildAction.class);

        assertFalse("There should only be one BuildAction.", ba.size()!=1);
    }
}

