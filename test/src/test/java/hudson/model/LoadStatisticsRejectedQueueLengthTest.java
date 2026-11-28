/*
 * The MIT License
 *
 * Copyright (c) 2026, Jenkins project contributors
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.slaves.DumbSlave;
import java.io.File;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests {@link LoadStatistics.LoadStatisticsSnapshot#getRejectedQueueLength}, which tells
 * {@link hudson.slaves.NodeProvisioner} that idle executors refused by the queue are not usable capacity.
 */
@WithJenkins
class LoadStatisticsRejectedQueueLengthTest {

    private static final String LABEL = "leased";

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * A node that is already running a build of a throttled category refuses further builds of that
     * category, so its remaining free executor cannot serve the queue and a new node is needed.
     */
    @Test
    void freeExecutorOnBusyNodeRefusingTheQueueIsNotCapacity() throws Exception {
        // the built-in node keeps its idle executors on purpose: they do not match the label, so they must
        // not be mistaken for nodes that would refuse the item no matter how much capacity is added
        DumbSlave agent = createAgent("leased-1", 2);
        FreeStyleProject a = createThrottledJob("a");
        FreeStyleProject b = createThrottledJob("b");

        FreeStyleBuild running = a.scheduleBuild2(0).waitForStart();
        try {
            b.scheduleBuild2(0);
            makeBuildable(b);

            LoadStatistics.LoadStatisticsSnapshot snapshot = Label.get(LABEL).loadStatistics.computeSnapshot();
            assertEquals(2, snapshot.getOnlineExecutors(), () -> String.valueOf(snapshot));
            assertEquals(1, snapshot.getBusyExecutors(), () -> String.valueOf(snapshot));
            // the second executor of the agent really is idle and accepting tasks...
            assertEquals(1, snapshot.getAvailableExecutors(), () -> String.valueOf(snapshot));
            assertEquals(1, snapshot.getQueueLength(), () -> String.valueOf(snapshot));
            // ...but it refused the only queued item, so it must not count as capacity for it
            assertEquals(1, snapshot.getRejectedQueueLength(), () -> String.valueOf(snapshot));
            assertTrue(j.jenkins.getQueue().getBuildableItems().stream()
                    .allMatch(Queue.BuildableItem::isRejectedByAllAvailableExecutors));
        } finally {
            j.jenkins.getQueue().clear();
            running.doStop();
            j.waitForCompletion(running);
            j.jenkins.removeNode(agent);
        }
    }

    /**
     * A node that refuses the item while being completely idle would refuse it no matter how much
     * capacity is added, so this must not be reported as a lack of capacity.
     */
    @Test
    void idleNodeRefusingTheQueueIsNotReportedAsMissingCapacity() throws Exception {
        DumbSlave agent = createAgent("leased-1", 2);
        FreeStyleProject c = createThrottledJob("c");
        try {
            c.scheduleBuild2(0);
            makeBuildable(c);

            LoadStatistics.LoadStatisticsSnapshot snapshot = Label.get(LABEL).loadStatistics.computeSnapshot();
            assertEquals(2, snapshot.getAvailableExecutors(), () -> String.valueOf(snapshot));
            assertEquals(1, snapshot.getQueueLength(), () -> String.valueOf(snapshot));
            assertEquals(0, snapshot.getRejectedQueueLength(), () -> String.valueOf(snapshot));
        } finally {
            j.jenkins.getQueue().clear();
            j.jenkins.removeNode(agent);
        }
    }

    private DumbSlave createAgent(String name, int numExecutors) throws Exception {
        DumbSlave agent = new DumbSlave(
                name,
                new File(j.jenkins.getRootDir(), "agent-work-dirs/" + name).getAbsolutePath(),
                j.createComputerLauncher(null));
        agent.setNumExecutors(numExecutors);
        agent.setLabelString(LABEL);
        j.jenkins.addNode(agent);
        j.waitOnline(agent);
        return agent;
    }

    private FreeStyleProject createThrottledJob(String name) throws Exception {
        FreeStyleProject p = j.createFreeStyleProject(name);
        p.setAssignedLabel(Label.get(LABEL));
        p.getBuildersList().add(new SleepBuilder(Long.MAX_VALUE));
        return p;
    }

    /**
     * Runs {@link Queue#maintain} until the given project is buildable, then once more so that a full
     * allocation pass has been performed while it was buildable.
     */
    private void makeBuildable(Queue.Task project) throws InterruptedException {
        Queue queue = j.jenkins.getQueue();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            queue.maintain();
            if (queue.getBuildableItems().stream().anyMatch(i -> i.task == project)) {
                break;
            }
            Thread.sleep(100);
        }
        queue.maintain();
    }

    /**
     * Stands in for the throttle-concurrent-builds plugin: at most one build of the category may run on a
     * given node at a time.
     */
    @TestExtension("freeExecutorOnBusyNodeRefusingTheQueueIsNotCapacity")
    public static class OneBuildOfTheCategoryPerNode extends QueueTaskDispatcher {

        private static final Set<String> CATEGORY = Set.of("a", "b");

        @Override
        public CauseOfBlockage canTake(Node node, Queue.BuildableItem item) {
            if (!CATEGORY.contains(item.task.getName())) {
                return null;
            }
            Computer c = node.toComputer();
            if (c == null) {
                return null;
            }
            for (Executor e : c.getExecutors()) {
                Queue.Executable executable = e.getCurrentExecutable();
                if (executable != null && CATEGORY.contains(executable.getParent().getOwnerTask().getName())) {
                    return refusal("the category is already running on " + node.getDisplayName());
                }
            }
            return null;
        }
    }

    /**
     * Refuses the item everywhere, including on nodes that are not doing anything at all.
     */
    @TestExtension("idleNodeRefusingTheQueueIsNotReportedAsMissingCapacity")
    public static class RefuseEverywhere extends QueueTaskDispatcher {

        @Override
        public CauseOfBlockage canTake(Node node, Queue.BuildableItem item) {
            return refusal("refusing everything");
        }
    }

    private static CauseOfBlockage refusal(String description) {
        return new CauseOfBlockage() {
            @Override
            public String getShortDescription() {
                return description;
            }
        };
    }
}
