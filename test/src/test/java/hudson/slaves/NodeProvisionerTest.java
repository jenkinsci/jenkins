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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeFalse;

import hudson.BulkChange;
import hudson.Functions;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import hudson.tasks.Builder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;

/**
 * @author Kohsuke Kawaguchi
 */
public class NodeProvisionerTest {

    @Rule public JenkinsRule r = new NodeProvisionerRule(/* run x1000 the regular speed to speed up the test */10, 100, 10);

    /**
     * Latch synchronization primitive that waits for N thread to pass the checkpoint.
     * <p>
     * This is used to make sure we get a set of builds that run long enough.
     */
    static class Latch {
        /** Initial value */
        public final transient CountDownLatch counter;
        private final int init;

        Latch(int n) {
            this.init = n;
            this.counter = new CountDownLatch(n);
        }

        void block() throws InterruptedException {
            this.counter.countDown();
            this.counter.await(60, TimeUnit.SECONDS);
        }

        /**
         * Creates a builder that blocks until the latch opens.
         */
        public Builder createBuilder() {
            return new Builder() {
                @Override
                public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
                    block();
                    return true;
                }
            };
        }
    }

    /**
     * Scenario: schedule a build and see if one agent is provisioned.
     */
    // TODO fragile
    @Test public void autoProvision() throws Exception {
        try (BulkChange bc = new BulkChange(r.jenkins)) {
            DummyCloudImpl cloud = initHudson(10);


            FreeStyleProject p = createJob(new SleepBuilder(10));

            Future<FreeStyleBuild> f = p.scheduleBuild2(0);
            f.get(30, TimeUnit.SECONDS); // if it's taking too long, abort.

            // since there's only one job, we expect there to be just one slave
            assertEquals(1, cloud.numProvisioned);
        }
    }

    /**
     * Scenario: we got a lot of jobs all of the sudden, and we need to fire up a few nodes.
     */
    // TODO fragile
    @Test public void loadSpike() throws Exception {
        assumeFalse("TODO: Windows container agents do not have enough resources to run this test", Functions.isWindows() && System.getenv("CI") != null);
        try (BulkChange bc = new BulkChange(r.jenkins)) {
            DummyCloudImpl cloud = initHudson(0);

            verifySuccessfulCompletion(buildAll(create5SlowJobs(new Latch(5))));

            // the time it takes to complete a job is eternally long compared to the time it takes to launch
            // a new slave, so in this scenario we end up allocating 5 slaves for 5 jobs.
            assertEquals(5, cloud.numProvisioned);
        }
    }

    /**
     * Scenario: make sure we take advantage of statically configured agents.
     */
    // TODO fragile
    @Test public void baselineSlaveUsage() throws Exception {
        assumeFalse("TODO: Windows container agents do not have enough resources to run this test", Functions.isWindows() && System.getenv("CI") != null);
        try (BulkChange bc = new BulkChange(r.jenkins)) {
            DummyCloudImpl cloud = initHudson(0);
            // add agents statically upfront
            r.createSlave().toComputer().connect(false).get();
            r.createSlave().toComputer().connect(false).get();

            verifySuccessfulCompletion(buildAll(create5SlowJobs(new Latch(5))));

            // we should have used two static slaves, thus only 3 slaves should have been provisioned
            assertEquals(3, cloud.numProvisioned);
        }
    }

    /**
     * Scenario: loads on one label shouldn't translate to load on another label.
     */
    // TODO fragile
    @Test public void labels() throws Exception {
        assumeFalse("TODO: Windows container agents do not have enough resources to run this test", Functions.isWindows() && System.getenv("CI") != null);
        try (BulkChange bc = new BulkChange(r.jenkins)) {
            DummyCloudImpl cloud = initHudson(0);
            Label blue = r.jenkins.getLabel("blue");
            Label red = r.jenkins.getLabel("red");
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
            assertEquals(5, cloud.numProvisioned);

            // and all blue jobs should be still stuck in the queue
            for (Future<FreeStyleBuild> bb : blueBuilds)
                assertFalse(bb.isDone());
        }
    }

    @Issue("JENKINS-67635")
    @Test
    public void testJobWithCloudLabelExpressionProvisionsOnlyOneAgent() throws Exception {
        DummyCloudImpl3 cloud1 = new DummyCloudImpl3(r);
        DummyCloudImpl3 cloud2 = new DummyCloudImpl3(r);

        cloud1.label = Label.get("cloud-1-label");
        cloud2.label = Label.get("cloud-2-label");

        r.jenkins.clouds.add(cloud1);
        r.jenkins.clouds.add(cloud2);

        FreeStyleProject p = r.createFreeStyleProject();
        p.setAssignedLabel(Label.parseExpression("cloud-1-label || cloud-2-label"));

        QueueTaskFuture<FreeStyleBuild> futureBuild = p.scheduleBuild2(0);
        futureBuild.waitForStart();
        r.assertBuildStatus(Result.SUCCESS, futureBuild);

        assertEquals(1, cloud1.numProvisioned);
        assertEquals(0, cloud2.numProvisioned);
    }

    private static class DummyCloudImpl3 extends Cloud {
        private final transient JenkinsRule caller;
        public int numProvisioned;
        private Label label;

        DummyCloudImpl3() {
            super("DummyCloudImpl3");
            this.caller = null;
        }

        DummyCloudImpl3(JenkinsRule caller) {
            super("DummyCloudImpl3");
            this.caller = caller;
        }

        @Override
        public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
            List<NodeProvisioner.PlannedNode> r = new ArrayList<>();
            if (! this.canProvision(label))
                return r;

            while (excessWorkload > 0) {
                numProvisioned++;
                Future<Node> f = Computer.threadPoolForRemoting.submit(new DummyCloudImpl3.Launcher());
                r.add(new NodeProvisioner.PlannedNode(name + " #" + numProvisioned, f, 1));
                excessWorkload -= 1;
            }
            return r;
        }

        @Override
        public boolean canProvision(Label label) {
            return label.matches(this.label.listAtoms());
        }

        private final class Launcher implements Callable<Node> {
            private volatile Computer computer;

            private Launcher() {}

            @Override
            public Node call() throws Exception {
                DumbSlave slave = caller.createSlave(label);
                computer = slave.toComputer();
                computer.connect(false).get();
                return slave;
            }
        }

        @Override
        public Descriptor<Cloud> getDescriptor() {
            throw new UnsupportedOperationException();
        }
    }


    private FreeStyleProject createJob(Builder builder) throws IOException {
        FreeStyleProject p = r.createFreeStyleProject();
        p.setAssignedLabel(null);   // let it roam free, or else it ties itself to the built-in node since we have no agents
        p.getBuildersList().add(builder);
        return p;
    }

    private DummyCloudImpl initHudson(int delay) throws IOException {
        // start a dummy service
        DummyCloudImpl cloud = new DummyCloudImpl(r, delay);
        r.jenkins.clouds.add(cloud);

        // no build on the built-in node, to make sure we get everything from the cloud
        r.jenkins.setNumExecutors(0);
        r.jenkins.setNodes(Collections.emptyList());
        return cloud;
    }

    private List<FreeStyleProject> create5SlowJobs(Latch l) throws IOException {
        List<FreeStyleProject> jobs = new ArrayList<>();
        for (int i = 0; i < l.init; i++)
            //set a large delay, to simulate the situation where we need to provision more agents
            // to keep up with the load
            jobs.add(createJob(l.createBuilder()));
        return jobs;
    }

    /**
     * Builds all the given projects at once.
     */
    private List<Future<FreeStyleBuild>> buildAll(List<FreeStyleProject> jobs) {
        System.out.println("Scheduling builds for " + jobs.size() + " jobs");
        List<Future<FreeStyleBuild>> builds = new ArrayList<>();
        for (FreeStyleProject job : jobs)
            builds.add(job.scheduleBuild2(0));
        return builds;
    }

    private void verifySuccessfulCompletion(List<Future<FreeStyleBuild>> builds) throws Exception {
        System.out.println("Waiting for a completion");
        for (Future<FreeStyleBuild> f : builds) {
            try {
                r.assertBuildStatus(Result.SUCCESS, f.get(90, TimeUnit.SECONDS));
            } catch (TimeoutException e) {
                // time out so that the automated test won't hang forever, even when we have bugs
                System.out.println("Build didn't complete in time");
                throw e;
            }
        }
    }
}
