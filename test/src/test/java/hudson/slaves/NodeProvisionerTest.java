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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import hudson.BulkChange;
import hudson.Functions;
import hudson.Launcher;
import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.LabelAxis;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.MatrixRun;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.queue.QueueTaskFuture;
import hudson.tasks.Builder;
import io.jenkins.lib.support_log_formatter.SupportLogFormatter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

/**
 * @author Kohsuke Kawaguchi
 */
class NodeProvisionerTest {

    @RegisterExtension
    private final RealJenkinsExtension rr = new RealJenkinsExtension();

    @BeforeEach
    void setUp() {
        // run 10x the regular speed to speed up the test
        rr.javaOptions(
                "-Dhudson.model.LoadStatistics.clock=" + TimeUnit.SECONDS.toMillis(1),
                "-Dhudson.slaves.NodeProvisioner.initialDelay=" + TimeUnit.SECONDS.toMillis(10),
                "-Dhudson.slaves.NodeProvisioner.recurrencePeriod=" + TimeUnit.SECONDS.toMillis(1));
    }

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
    @Test
    void autoProvision() throws Throwable {
        assumeFalse(Functions.isWindows() && System.getenv("CI") != null, "TODO: Windows container agents do not have enough resources to run this test");
        rr.then(NodeProvisionerTest::_autoProvision);
    }

    private static void _autoProvision(JenkinsRule r) throws Exception {
        try (BulkChange bc = new BulkChange(r.jenkins)) {
            DummyCloudImpl cloud = initHudson(10, r);

            FreeStyleProject p = createJob(new SleepBuilder(10), r);

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
    @Test
    void loadSpike() throws Throwable {
        assumeFalse(Functions.isWindows() && System.getenv("CI") != null, "TODO: Windows container agents do not have enough resources to run this test");
        rr.then(NodeProvisionerTest::_loadSpike);
    }

    private static void _loadSpike(JenkinsRule r) throws Exception {
        try (BulkChange bc = new BulkChange(r.jenkins)) {
            DummyCloudImpl cloud = initHudson(0, r);

            verifySuccessfulCompletion(buildAll(create5SlowJobs(new Latch(5), r)), r);

            // the time it takes to complete a job is eternally long compared to the time it takes to launch
            // a new slave, so in this scenario we end up allocating 5 slaves for 5 jobs.
            // sometimes we can end up allocating 6 due to the conservative estimation in StandardStrategyImpl#apply
            assertThat(cloud.numProvisioned, anyOf(equalTo(5), equalTo(6)));
        }
    }

    /**
     * Scenario: make sure we take advantage of statically configured agents.
     */
    // TODO fragile
    @Test
    void baselineSlaveUsage() throws Throwable {
        assumeFalse(Functions.isWindows() && System.getenv("CI") != null, "TODO: Windows container agents do not have enough resources to run this test");
        rr.then(NodeProvisionerTest::_baselineSlaveUsage);
    }

    private static void _baselineSlaveUsage(JenkinsRule r) throws Exception {
        try (BulkChange bc = new BulkChange(r.jenkins)) {
            DummyCloudImpl cloud = initHudson(0, r);
            // add agents statically upfront
            r.createSlave().toComputer().connect(false).get();
            r.createSlave().toComputer().connect(false).get();

            verifySuccessfulCompletion(buildAll(create5SlowJobs(new Latch(5), r)), r);

            // we should have used two static slaves, thus only 3 slaves should have been provisioned
            // sometimes we can end up allocating 4 due to the conservative estimation in StandardStrategyImpl#apply
            assertThat(cloud.numProvisioned, anyOf(equalTo(3), equalTo(4)));
        }
    }

    /**
     * Scenario: loads on one label shouldn't translate to load on another label.
     */
    // TODO fragile
    @Test
    void labels() throws Throwable {
        assumeFalse(Functions.isWindows() && System.getenv("CI") != null, "TODO: Windows container agents do not have enough resources to run this test");
        rr.then(NodeProvisionerTest::_labels);
    }

    private static void _labels(JenkinsRule r) throws Exception {
        try (BulkChange bc = new BulkChange(r.jenkins)) {
            DummyCloudImpl cloud = initHudson(0, r);
            Label blue = r.jenkins.getLabel("blue");
            Label red = r.jenkins.getLabel("red");
            cloud.label = red;

            // red jobs
            List<FreeStyleProject> redJobs = create5SlowJobs(new Latch(5), r);
            for (FreeStyleProject p : redJobs)
                p.setAssignedLabel(red);

            // blue jobs
            List<FreeStyleProject> blueJobs = create5SlowJobs(new Latch(5), r);
            for (FreeStyleProject p : blueJobs)
                p.setAssignedLabel(blue);

            // build all
            List<Future<FreeStyleBuild>> blueBuilds = buildAll(blueJobs);
            verifySuccessfulCompletion(buildAll(redJobs), r);

            // cloud should only give us 5 nodes for 5 red jobs
            // sometimes we can end up allocating 6 due to the conservative estimation in StandardStrategyImpl#apply
            assertThat(cloud.numProvisioned, anyOf(equalTo(5), equalTo(6)));

            // and all blue jobs should be still stuck in the queue
            for (Future<FreeStyleBuild> bb : blueBuilds)
                assertFalse(bb.isDone());
        }
    }

    @Issue("JENKINS-7291")
    @Test
    void flyweightTasksWithoutMasterExecutors() throws Throwable {
        assumeFalse(Functions.isWindows() && System.getenv("CI") != null, "TODO: Windows container agents do not have enough resources to run this test");
        rr.then(NodeProvisionerTest::_flyweightTasksWithoutMasterExecutors);
    }

    private static void _flyweightTasksWithoutMasterExecutors(JenkinsRule r) throws Exception {
        DummyCloudImpl cloud = new DummyCloudImpl(r, 0);
        cloud.label = r.jenkins.getLabel("remote");
        r.jenkins.clouds.add(cloud);
        r.jenkins.setNumExecutors(0);
        r.jenkins.setNodes(Collections.emptyList());
        MatrixProject m = r.jenkins.createProject(MatrixProject.class, "p");
        m.setAxes(new AxisList(new LabelAxis("label", List.of("remote"))));
        MatrixBuild build;
        try {
            build = m.scheduleBuild2(0).get(60, TimeUnit.SECONDS);
        } catch (TimeoutException x) {
            throw new AssertionError(Arrays.toString(r.jenkins.getQueue().getItems()), x);
        }
        r.assertBuildStatusSuccess(build);
        assertEquals("", build.getBuiltOnStr());
        List<MatrixRun> runs = build.getRuns();
        assertEquals(1, runs.size());
        assertEquals("slave0", runs.getFirst().getBuiltOnStr());
    }

    /**
     * When a flyweight task is restricted to run on a specific node, the node will be provisioned
     * and the flyweight task will be executed.
     */
    @Issue("JENKINS-30084")
    @Test
    void shouldRunFlyweightTaskOnProvisionedNodeWhenNodeRestricted() throws Throwable {
        assumeFalse(Functions.isWindows() && System.getenv("CI") != null, "TODO: Windows container agents do not have enough resources to run this test");
        rr.then(NodeProvisionerTest::_shouldRunFlyweightTaskOnProvisionedNodeWhenNodeRestricted);
    }

    private static void _shouldRunFlyweightTaskOnProvisionedNodeWhenNodeRestricted(JenkinsRule r) throws Exception {
        MatrixProject matrixProject = r.jenkins.createProject(MatrixProject.class, "p");
        matrixProject.setAxes(new AxisList(new Axis("axis", "a", "b")));
        Label label = Label.get("aws-linux-dummy");
        DummyCloudImpl dummyCloud = new DummyCloudImpl(r, 0);
        dummyCloud.label = label;
        r.jenkins.clouds.add(dummyCloud);
        matrixProject.setAssignedLabel(label);
        r.buildAndAssertSuccess(matrixProject);
        assertEquals("aws-linux-dummy", matrixProject.getBuilds().getLastBuild().getBuiltOn().getLabelString());
    }

    @Issue("JENKINS-67635")
    @Test
    void testJobWithCloudLabelExpressionProvisionsOnlyOneAgent() throws Throwable {
        assumeFalse(Functions.isWindows() && System.getenv("CI") != null, "TODO: Windows container agents do not have enough resources to run this test");
        rr.then(NodeProvisionerTest::_testJobWithCloudLabelExpressionProvisionsOnlyOneAgent);
    }

    private static void _testJobWithCloudLabelExpressionProvisionsOnlyOneAgent(JenkinsRule r) throws Exception {
        DummyCloudImpl3 cloud1 = new DummyCloudImpl3(r);
        DummyCloudImpl3 cloud2 = new DummyCloudImpl3(r);

        cloud1.label = Label.get("cloud-1-label");
        cloud2.label = Label.get("cloud-2-label");

        r.jenkins.clouds.add(cloud1);
        r.jenkins.clouds.add(cloud2);

        FreeStyleProject p = r.createFreeStyleProject();
        p.setAssignedLabel(Label.parseExpression("cloud-1-label || cloud-2-label"));

        QueueTaskFuture<FreeStyleBuild> futureBuild = p.scheduleBuild2(0);
        FreeStyleBuild build = futureBuild.waitForStart();
        r.assertBuildStatusSuccess(r.waitForCompletion(build));

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


    private static FreeStyleProject createJob(Builder builder, JenkinsRule r) throws IOException {
        FreeStyleProject p = r.createFreeStyleProject();
        p.setAssignedLabel(null);   // let it roam free, or else it ties itself to the built-in node since we have no agents
        p.getBuildersList().add(builder);
        return p;
    }

    private static DummyCloudImpl initHudson(int delay, JenkinsRule r) throws IOException {
        // start a dummy service
        DummyCloudImpl cloud = new DummyCloudImpl(r, delay);
        r.jenkins.clouds.add(cloud);

        // no build on the built-in node, to make sure we get everything from the cloud
        r.jenkins.setNumExecutors(0);
        r.jenkins.setNodes(Collections.emptyList());

        // TODO RealJenkinsRule does not yet support LoggerRule
        Handler handler = new ConsoleHandler();
        handler.setFormatter(new SupportLogFormatter());
        handler.setLevel(Level.FINER);
        Logger logger = Logger.getLogger(NodeProvisioner.class.getName());
        logger.setLevel(Level.FINER);
        logger.addHandler(handler);

        return cloud;
    }

    private static List<FreeStyleProject> create5SlowJobs(Latch l, JenkinsRule r) throws IOException {
        List<FreeStyleProject> jobs = new ArrayList<>();
        for (int i = 0; i < l.init; i++)
            //set a large delay, to simulate the situation where we need to provision more agents
            // to keep up with the load
            jobs.add(createJob(l.createBuilder(), r));
        return jobs;
    }

    /**
     * Builds all the given projects at once.
     */
    private static List<Future<FreeStyleBuild>> buildAll(List<FreeStyleProject> jobs) {
        System.out.println("Scheduling builds for " + jobs.size() + " jobs");
        List<Future<FreeStyleBuild>> builds = new ArrayList<>();
        for (FreeStyleProject job : jobs)
            builds.add(job.scheduleBuild2(0));
        return builds;
    }

    private static void verifySuccessfulCompletion(List<Future<FreeStyleBuild>> builds, JenkinsRule r) throws Exception {
        System.out.println("Waiting for a completion");
        for (Future<FreeStyleBuild> f : builds) {
            r.assertBuildStatusSuccess(f.get());
        }
    }
}
