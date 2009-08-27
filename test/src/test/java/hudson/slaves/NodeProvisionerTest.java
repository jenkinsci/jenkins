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
package hudson.slaves;

import hudson.BulkChange;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.Builder;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.SleepBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Kohsuke Kawaguchi
 */
public class NodeProvisionerTest extends HudsonTestCase {
    private int original;

    @Override
    protected void setUp() throws Exception {
        original = LoadStatistics.CLOCK;
        LoadStatistics.CLOCK = 10; // run x1000 the regular speed to speed up the test
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        LoadStatistics.CLOCK = original;
    }

    /**
     * Latch synchronization primitive that waits for N thread to pass the checkpoint.
     * <p>
     * This is used to make sure we get a set of builds that run long enough.
     */
    static class Latch {
        /** Initial value */
        public final int init;
        private int n;

        Latch(int n) {
            this.n = init = n;
        }

        synchronized void block() throws InterruptedException {
            if(--n==0)  notifyAll();    // wake up everyone else
            else        wait(60*1000);  // if a test takes t oo long, abort.
        }

        /**
         * Creates a builder that blocks until the latch opens.
         */
        public Builder createBuilder() {
            return new Builder() {
                public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                    block();
                    return true;
                }
            };
        }
    }

    /**
     * Scenario: schedule a build and see if one slave is provisioned.
     */
    public void testAutoProvision() throws Exception {
        BulkChange bc = new BulkChange(hudson);
        try {
            DummyCloudImpl cloud = initHudson(10);


            FreeStyleProject p = createJob(new SleepBuilder(10));

            Future<FreeStyleBuild> f = p.scheduleBuild2(0);
            f.get(30, TimeUnit.SECONDS); // if it's taking too long, abort.

            // since there's only one job, we expect there to be just one slave
            assertEquals(1,cloud.numProvisioned);
        } finally {
            bc.abort();
        }
    }

    /**
     * Scenario: we got a lot of jobs all of the sudden, and we need to fire up a few nodes.
     */
    public void testLoadSpike() throws Exception {
        BulkChange bc = new BulkChange(hudson);
        try {
            DummyCloudImpl cloud = initHudson(0);

            verifySuccessfulCompletion(buildAll(create5SlowJobs(new Latch(5))));

            // the time it takes to complete a job is eternally long compared to the time it takes to launch
            // a new slave, so in this scenario we end up allocating 5 slaves for 5 jobs.
            assertEquals(5,cloud.numProvisioned);
        } finally {
            bc.abort();
        }
    }

    /**
     * Scenario: make sure we take advantage of statically configured slaves.
     */
    public void testBaselineSlaveUsage() throws Exception {
        BulkChange bc = new BulkChange(hudson);
        try {
            DummyCloudImpl cloud = initHudson(0);
            // add slaves statically upfront
            createSlave().toComputer().connect(false).get();
            createSlave().toComputer().connect(false).get();

            verifySuccessfulCompletion(buildAll(create5SlowJobs(new Latch(5))));

            // we should have used two static slaves, thus only 3 slaves should have been provisioned
            assertEquals(3,cloud.numProvisioned);
        } finally {
            bc.abort();
        }
    }

    /**
     * Scenario: loads on one label shouldn't translate to load on another label.
     */
    public void testLabels() throws Exception {
        BulkChange bc = new BulkChange(hudson);
        try {
            DummyCloudImpl cloud = initHudson(0);
            Label blue = hudson.getLabel("blue");
            Label red = hudson.getLabel("red");
            cloud.label = red;

            // red jobs
            List<FreeStyleProject> redJobs = create5SlowJobs(new Latch(5));
            for (FreeStyleProject p : redJobs)
                p.setAssignedLabel(red);

            // blue jobs
            List<FreeStyleProject> blueJobs = create5SlowJobs(new Latch(5));
            for (FreeStyleProject p : blueJobs)
                p.setAssignedLabel(blue);

            // build all
            List<Future<FreeStyleBuild>> blueBuilds = buildAll(blueJobs);
            verifySuccessfulCompletion(buildAll(redJobs));

            // cloud should only give us 5 nodes for 5 red jobs
            assertEquals(5,cloud.numProvisioned);

            // and all blue jobs should be still stuck in the queue
            for (Future<FreeStyleBuild> bb : blueBuilds)
                assertFalse(bb.isDone());
        } finally {
            bc.abort();
        }
    }


    private FreeStyleProject createJob(Builder builder) throws IOException {
        FreeStyleProject p = createFreeStyleProject();
        p.setAssignedLabel(null);   // let it roam free, or else it ties itself to the master since we have no slaves
        p.getBuildersList().add(builder);
        return p;
    }

    private DummyCloudImpl initHudson(int delay) throws IOException {
        // start a dummy service
        DummyCloudImpl cloud = new DummyCloudImpl(this, delay);
        hudson.clouds.add(cloud);

        // no build on the master, to make sure we get everything from the cloud
        hudson.setNumExecutors(0);
        hudson.setNodes(Collections.<Node>emptyList());
        return cloud;
    }

    private List<FreeStyleProject> create5SlowJobs(Latch l) throws IOException {
        List<FreeStyleProject> jobs = new ArrayList<FreeStyleProject>();
        for( int i=0; i<l.init; i++)
            //set a large delay, to simulate the situation where we need to provision more slaves
            // to keep up with the load
            jobs.add(createJob(l.createBuilder()));
        return jobs;
    }

    /**
     * Builds all the given projects at once.
     */
    private List<Future<FreeStyleBuild>> buildAll(List<FreeStyleProject> jobs) {
        System.out.println("Scheduling builds for "+jobs.size()+" jobs");
        List<Future<FreeStyleBuild>> builds = new ArrayList<Future<FreeStyleBuild>>();
        for (FreeStyleProject job : jobs)
            builds.add(job.scheduleBuild2(0));
        return builds;
    }

    private void verifySuccessfulCompletion(List<Future<FreeStyleBuild>> builds) throws Exception {
        System.out.println("Waiting for a completion");
        for (Future<FreeStyleBuild> f : builds) {
            try {
                assertBuildStatus(Result.SUCCESS, f.get(1, TimeUnit.MINUTES));
            } catch (TimeoutException e) {
                // time out so that the automated test won't hang forever, even when we have bugs
                System.out.println("Build didn't complete in time");
                throw e;
            }
        }
    }
}
