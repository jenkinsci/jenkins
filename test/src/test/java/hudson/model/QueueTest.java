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

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.oneOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.Functions;
import hudson.Launcher;
import hudson.XmlFile;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.Cause.RemoteCause;
import hudson.model.Cause.UserIdCause;
import hudson.model.Queue.BlockedItem;
import hudson.model.Queue.Executable;
import hudson.model.Queue.WaitingItem;
import hudson.model.listeners.SaveableListener;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.model.queue.QueueTaskFuture;
import hudson.model.queue.ScheduleResult;
import hudson.model.queue.SubTask;
import hudson.security.ACL;
import hudson.security.AuthorizationMatrixProperty;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.Permission;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import hudson.security.SparseACL;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DumbSlave;
import hudson.slaves.OfflineCause;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import hudson.tasks.BatchFile;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Shell;
import hudson.triggers.SCMTrigger.SCMTriggerCause;
import hudson.triggers.TimerTrigger.TimerTriggerCause;
import hudson.util.OneShotEvent;
import jakarta.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.BlockedBecauseOfBuildInProgress;
import jenkins.model.Jenkins;
import jenkins.model.queue.QueueIdStrategy;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import jenkins.util.Timer;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.ScriptResult;
import org.htmlunit.WebRequest;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.xml.XmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockQueueItemAuthenticator;
import org.jvnet.hudson.test.SequenceLock;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
public class QueueTest {

    private final LogRecorder logging = new LogRecorder().record(Queue.class, Level.FINE);

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    /**
     * Checks the persistence of queue.
     */
    @Test
    void persistence() throws Exception {
        Queue q = r.jenkins.getQueue();

        // prevent execution to push stuff into the queue
        r.jenkins.setNumExecutors(0);

        FreeStyleProject testProject = r.createFreeStyleProject("test");
        assertNotNull(testProject.scheduleBuild2(0, new UserIdCause()));
        q.save();

        System.out.println(Files.readString(r.jenkins.getRootDir().toPath().resolve("queue.xml"), StandardCharsets.UTF_8));

        assertEquals(1, q.getItems().length);
        q.clear();
        assertEquals(0, q.getItems().length);

        // load the contents back
        q.load();
        assertEquals(1, q.getItems().length);

        // did it bind back to the same object?
        assertSame(q.getItems()[0].task, testProject);

        // Clear the queue
        assertTrue(q.cancel(testProject));
    }

    /**
     * Make sure the queue can be reconstructed from a List queue.xml.
     * Prior to the Queue.State class, the Queue items were just persisted as a List.
     */
    @LocalData
    @Test
    void recover_from_legacy_list() {
        Queue q = r.jenkins.getQueue();

        // loaded the legacy queue.xml from test LocalData located in
        // resources/hudson/model/QueueTest/recover_from_legacy_list.zip
        assertEquals(1, q.getItems().length);

        // The current counter should be the id from the item brought back
        // from the persisted queue.xml.
        assertEquals(3, QueueIdStrategy.DefaultStrategy.getCurrentCounterValue());

        // Clear the queue
        assertTrue(r.jenkins.getQueue().cancel(r.jenkins.getItemByFullName("test", FreeStyleProject.class)));
    }

    /**
     * Can {@link Queue} successfully recover removal?
     */
    @Test
    void persistence2() throws Exception {
        Queue q = r.jenkins.getQueue();

        resetQueueState();
        assertEquals(0, QueueIdStrategy.DefaultStrategy.getCurrentCounterValue());

        // prevent execution to push stuff into the queue
        r.jenkins.setNumExecutors(0);

        FreeStyleProject testProject = r.createFreeStyleProject("test");
        assertNotNull(testProject.scheduleBuild2(0, new UserIdCause()));
        q.save();

        System.out.println(Files.readString(r.jenkins.getRootDir().toPath().resolve("queue.xml"), StandardCharsets.UTF_8));

        assertEquals(1, q.getItems().length);
        q.clear();
        assertEquals(0, q.getItems().length);

        // delete the project before loading the queue back
        testProject.delete();
        q.load();
        assertEquals(0, q.getItems().length);

        // The counter state should be maintained.
        assertEquals(1, QueueIdStrategy.DefaultStrategy.getCurrentCounterValue());
    }

    /**
     * Forces a reset of the private queue COUNTER.
     * Could make changes to Queue to make that easier, but decided against that.
     */
    private void resetQueueState() throws IOException {
        File queueFile = r.jenkins.getQueue().getXMLQueueFile();
        XmlFile xmlFile = new XmlFile(Queue.XSTREAM, queueFile);
        xmlFile.write(new Queue.State());
        r.jenkins.getQueue().load();
    }

    @Test
    void queue_id_to_run_mapping() throws Exception {
        FreeStyleProject testProject = r.createFreeStyleProject("test");
        FreeStyleBuild build = r.buildAndAssertSuccess(testProject);
        assertNotEquals(Run.QUEUE_ID_UNKNOWN, build.getQueueId());
    }

    /**
     * {@link hudson.model.Queue.BlockedItem} is not static. Make sure its persistence doesn't end up re-persisting the whole Queue instance.
     */
    @Test
    void persistenceBlockedItem() throws Exception {
        Queue q = r.jenkins.getQueue();
        final SequenceLock seq = new SequenceLock();

        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
                seq.phase(0);   // first, we let one build going

                seq.phase(2);
                return true;
            }
        });

        FreeStyleBuild b1 = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b1);
        seq.phase(1);   // and make sure we have one build under way

        // get another going
        Future<FreeStyleBuild> b2 = p.scheduleBuild2(0);
        assertNotNull(b2);

        q.scheduleMaintenance().get();
        Queue.Item[] items = q.getItems();
        assertEquals(1, items.length);
        assertThat(items[0], instanceOf(BlockedItem.class));

        q.save();

        assertTrue(q.cancel(items[0]));
        seq.done();
        r.assertBuildStatusSuccess(r.waitForCompletion(b1));
    }

    @Issue("JENKINS-33467")
    @Test
    void foldableCauseAction() throws Exception {
        final OneShotEvent buildStarted = new OneShotEvent();
        final OneShotEvent buildShouldComplete = new OneShotEvent();

        r.setQuietPeriod(0);
        FreeStyleProject project = r.createFreeStyleProject();
        // Make build sleep a while so it blocks new builds
        project.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
                buildStarted.signal();
                buildShouldComplete.block();
                return true;
            }
        });

        // Start one build to block others
        project.scheduleBuild2(0, new UserIdCause()).waitForStart();
        buildStarted.block(); // wait for the build to really start

        // Schedule a new build, and trigger it many ways while it sits in queue
        final Future<FreeStyleBuild> fb = project.scheduleBuild2(0, new UserIdCause());
        assertNotNull(fb);
        assertNotNull(project.scheduleBuild2(0, new SCMTriggerCause("")));
        assertNotNull(project.scheduleBuild2(0, new UserIdCause()));
        assertNotNull(project.scheduleBuild2(0, new TimerTriggerCause()));
        assertNotNull(project.scheduleBuild2(0, new RemoteCause("1.2.3.4", "test")));
        assertNotNull(project.scheduleBuild2(0, new RemoteCause("4.3.2.1", "test")));
        assertNotNull(project.scheduleBuild2(0, new SCMTriggerCause("")));
        assertNotNull(project.scheduleBuild2(0, new RemoteCause("1.2.3.4", "test")));
        assertNotNull(project.scheduleBuild2(0, new RemoteCause("1.2.3.4", "foo")));
        assertNotNull(project.scheduleBuild2(0, new SCMTriggerCause("")));
        assertNotNull(project.scheduleBuild2(0, new TimerTriggerCause()));

        // Wait for 2nd build to finish
        buildShouldComplete.signal();
        FreeStyleBuild build = fb.get();

        // Make sure proper folding happened.
        CauseAction ca = build.getAction(CauseAction.class);
        assertNotNull(ca);
        StringBuilder causes = new StringBuilder();
        for (Cause c : ca.getCauses()) causes.append(c.getShortDescription()).append("\n");
        assertEquals("""
                        Started by user SYSTEM
                        Started by user SYSTEM
                        Started by an SCM change
                        Started by an SCM change
                        Started by an SCM change
                        Started by timer
                        Started by timer
                        Started by remote host 1.2.3.4 with note: test
                        Started by remote host 1.2.3.4 with note: test
                        Started by remote host 4.3.2.1 with note: test
                        Started by remote host 1.2.3.4 with note: foo
                        """,
                causes.toString(),
                "Build causes should have all items, even duplicates");

        // View for build should group duplicates
        JenkinsRule.WebClient wc = r.createWebClient();
        String buildPage = wc.getPage(build, "").asNormalizedText();
        assertTrue(buildPage.contains("""
                        Started by user SYSTEM (2 times)
                        Started by an SCM change (3 times)
                        Started by timer (2 times)
                        Started by remote host 1.2.3.4 with note: test (2 times)
                        Started by remote host 4.3.2.1 with note: test
                        Started by remote host 1.2.3.4 with note: foo"""),
                   "Build page should combine duplicates and show counts: " + buildPage);
        System.out.println(new XmlFile(new File(build.getRootDir(), "build.xml")).asString());
    }

    @Issue("JENKINS-8790")
    @Test
    void flyweightTasks() throws Exception {
        MatrixProject m = r.jenkins.createProject(MatrixProject.class, "p");
        m.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("FOO", "value")
        ));
        if (Functions.isWindows()) {
            m.getBuildersList().add(new BatchFile("ping -n 3 127.0.0.1 >nul"));
        } else {
            m.getBuildersList().add(new Shell("sleep 3"));
        }
        m.setAxes(new AxisList(new TextAxis("DoesntMatter", "aaa", "bbb")));

        List<Future<MatrixBuild>> futures = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            futures.add(m.scheduleBuild2(0, new UserIdCause(), new ParametersAction(new StringParameterValue("FOO", "value" + i))));
        }

        for (Future<MatrixBuild> f : futures) {
            r.assertBuildStatusSuccess(f);
        }
    }

    @Issue("JENKINS-10944")
    @Test
    void flyweightTasksBlockedByShutdown() throws Exception {
        r.jenkins.doQuietDown(true, 0, null, false);
        AtomicInteger cnt = new AtomicInteger();
        TestFlyweightTask task = new TestFlyweightTask(cnt, null);
        assertTrue(Queue.isBlockedByShutdown(task));
        r.jenkins.getQueue().schedule2(task, 0);
        r.jenkins.getQueue().maintain();
        r.jenkins.doCancelQuietDown();
        assertFalse(Queue.isBlockedByShutdown(task));
        r.waitUntilNoActivity();
        assertEquals(1, cnt.get());
        assertNotNull(task.exec);
        assertThat(task.exec, instanceOf(OneOffExecutor.class));
    }

    @Issue("JENKINS-24519")
    @Test
    void flyweightTasksBlockedBySlave() throws Exception {
        Label label = Label.get("myslave");
        AtomicInteger cnt = new AtomicInteger();
        TestFlyweightTask task = new TestFlyweightTask(cnt, label);
        r.jenkins.getQueue().schedule2(task, 0);
        r.jenkins.getQueue().maintain();
        r.createSlave(label);
        r.waitUntilNoActivity();
        assertEquals(1, cnt.get());
        assertNotNull(task.exec);
        assertThat(task.exec, instanceOf(OneOffExecutor.class));
    }

    @Issue("JENKINS-41127")
    @Test
    void flyweightTasksUnwantedConcurrency() {
        Label label = r.jenkins.getSelfLabel();
        AtomicInteger cnt = new AtomicInteger();
        TestFlyweightTask task1 = new TestFlyweightTask(cnt, label);
        TestFlyweightTask task2 = new TestFlyweightTask(cnt, label);
        assertFalse(task1.isConcurrentBuild());
        assertFalse(task2.isConcurrentBuild());
        // We need to call Queue#maintain without any interleaving Queue modification to reproduce the issue.
        Queue.withLock(() -> {
            r.jenkins.getQueue().schedule2(task1, 0);
            r.jenkins.getQueue().maintain();
            Queue.Item item1 = r.jenkins.getQueue().getItem(task1);
            assertThat(r.jenkins.getQueue().getPendingItems(), contains(item1));
            r.jenkins.getQueue().schedule2(task2, 0);
            r.jenkins.getQueue().maintain();
            Queue.Item item2 = r.jenkins.getQueue().getItem(task2);
            // Before the fix, item1 would no longer be present in the pending items (but would
            // still be assigned to a live executor), and item2 would not be blocked, which would
            // allow the tasks to execute concurrently.
            assertThat(r.jenkins.getQueue().getPendingItems(), contains(item1));
            assertTrue(item2.isBlocked());
        });
    }

    @Test
    void tryWithTimeoutSuccessfullyAcquired() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Submit one task that takes 50ms
            final AtomicBoolean task1Complete =  new AtomicBoolean(false);
            executor.submit(Queue.wrapWithLock(() -> {
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                task1Complete.set(true);
            }));

            // Try to acquire lock with 100ms timeout
            final AtomicBoolean task2Complete = new AtomicBoolean(false);
            boolean result = Queue.tryWithLock(() -> {
                task2Complete.set(true);
            }, Duration.ofMillis(100));

            assertTrue(result);
            assertTrue(task1Complete.get());
            assertTrue(task2Complete.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void tryWithTimeoutFailedToAcquire() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Submit one task that takes 100ms
            final CountDownLatch task1Complete =  new CountDownLatch(1);
            executor.submit(Queue.wrapWithLock(() -> {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                task1Complete.countDown();
            }));

            // Try to acquire lock with 50ms timeout
            final AtomicBoolean task2Complete = new AtomicBoolean(false);
            boolean result = Queue.tryWithLock(() -> {
                task2Complete.set(true);
            }, Duration.ofMillis(50));

            task1Complete.await();
            assertFalse(result);
            assertFalse(task2Complete.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void tryWithTimeoutImmediatelyAcquired() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final AtomicBoolean taskComplete = new AtomicBoolean(false);
            boolean result = Queue.tryWithLock(() -> {
                taskComplete.set(true);
            }, Duration.ofMillis(1));
            assertTrue(result);
            assertTrue(taskComplete.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Issue("JENKINS-27256")
    @Test
    void inQueueTaskLookupByAPI() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        Label label = Label.get("unknown-slave");

        // Give the project an "unknown-slave" label, forcing it to
        // stay in the queue after we schedule it, allowing us to query it.
        p.setAssignedLabel(label);
        p.scheduleBuild2(0);

        // Wait 3 seconds if job is not already in the queue, reduce test flakes
        if (!p.isInQueue()) {
            Thread.sleep(3000);
        }

        assertTrue(p.isInQueue(), "Build not queued");

        JenkinsRule.WebClient webclient = r.createWebClient();

        XmlPage queueItems = webclient.goToXml("queue/api/xml");
        String queueTaskId = queueItems.getXmlDocument().getElementsByTagName("id").item(0).getTextContent();
        assertNotNull(queueTaskId);
        XmlPage queueItem = webclient.goToXml("queue/item/" + queueTaskId + "/api/xml");
        assertNotNull(queueItem);
        String tagName = queueItem.getDocumentElement().getTagName();
        assertThat(tagName, oneOf("blockedItem", "buildableItem"));

        // Clear the queue
        assertTrue(r.jenkins.getQueue().cancel(p));
    }

    @Issue("JENKINS-28926")
    @Test
    void upstreamDownstreamCycle() throws Exception {
        FreeStyleProject trigger = r.createFreeStyleProject();
        FreeStyleProject chain1 = r.createFreeStyleProject();
        FreeStyleProject chain2a = r.createFreeStyleProject();
        FreeStyleProject chain2b = r.createFreeStyleProject();
        FreeStyleProject chain3 = r.createFreeStyleProject();
        trigger.getPublishersList().add(new BuildTrigger(String.format("%s, %s, %s, %s", chain1.getName(), chain2a.getName(), chain2b.getName(), chain3.getName()), true));
        trigger.setQuietPeriod(0);
        chain1.setQuietPeriod(1);
        chain2a.setQuietPeriod(1);
        chain2b.setQuietPeriod(1);
        chain3.setQuietPeriod(1);
        chain1.getPublishersList().add(new BuildTrigger(String.format("%s, %s", chain2a.getName(), chain2b.getName()), true));
        chain2a.getPublishersList().add(new BuildTrigger(chain3.getName(), true));
        chain2b.getPublishersList().add(new BuildTrigger(chain3.getName(), true));
        chain1.setBlockBuildWhenDownstreamBuilding(true);
        chain2a.setBlockBuildWhenDownstreamBuilding(true);
        chain2b.setBlockBuildWhenDownstreamBuilding(true);
        chain3.setBlockBuildWhenUpstreamBuilding(true);
        r.jenkins.rebuildDependencyGraph();
        r.buildAndAssertSuccess(trigger);
        // the trigger should build immediately and schedule the cycle
        r.waitUntilNoActivity();
        final Queue queue = r.getInstance().getQueue();
        assertThat("The cycle should have been defanged and chain1 executed", queue.getItem(chain1), nullValue());
        assertThat("The cycle should have been defanged and chain2a executed", queue.getItem(chain2a), nullValue());
        assertThat("The cycle should have been defanged and chain2b executed", queue.getItem(chain2b), nullValue());
        assertThat("The cycle should have been defanged and chain3 executed", queue.getItem(chain3), nullValue());
    }


    @TestExtension({"upstreamProjectsInQueueBlock", "downstreamProjectsInQueueBlock", "handleCauseOfBlockageThatIsNull"})
    public static class BlockingQueueTaskDispatcher extends QueueTaskDispatcher {

        public static final String NAME_OF_BLOCKED_PROJECT = "blocked project";

        public static final String NAME_OF_ANOTHER_BLOCKED_PROJECT = "another blocked project";

        @Override
        public CauseOfBlockage canRun(hudson.model.Queue.Item item) {
            if (item.task.getOwnerTask().getDisplayName().equals(NAME_OF_BLOCKED_PROJECT)) {
                return new CauseOfBlockage() {

                    @Override
                    public String getShortDescription() {
                        return NAME_OF_BLOCKED_PROJECT + " is permanently blocked.";
                    }

                };
            }
            return super.canRun(item);
        }

    }

    private void waitUntilWaitingListIsEmpty(Queue q) throws InterruptedException {
        boolean waitingItemsPresent = true;
        while (waitingItemsPresent) {
            waitingItemsPresent = false;
            for (Queue.Item i : q.getItems()) {
                if (i instanceof WaitingItem) {
                    waitingItemsPresent = true;
                    break;
                }
            }
            Thread.sleep(1000);
        }
    }

    @Issue("JENKINS-68780")
    @Test
    void upstreamProjectsInQueueBlock() throws Exception {

       FreeStyleProject a = r.createFreeStyleProject(BlockingQueueTaskDispatcher.NAME_OF_BLOCKED_PROJECT);
       FreeStyleProject b = r.createFreeStyleProject();
       a.getPublishersList().add(new BuildTrigger(b.getName(), true));
       b.setBlockBuildWhenUpstreamBuilding(true);

       r.jenkins.rebuildDependencyGraph();

       a.scheduleBuild(0, new UserIdCause());

       Queue q = r.jenkins.getQueue();

       waitUntilWaitingListIsEmpty(q);

       b.scheduleBuild(0, new UserIdCause());

       waitUntilWaitingListIsEmpty(q);

       // This call is necessary because the queue blocks projects
       // at first only temporarily. By calling the maintain method
       // all temporarily blocked projects either become buildable or
       // become permanently blocked
       q.scheduleMaintenance().get();

       assertEquals(2, q.getBlockedItems().size(), "Queue should contain two blocked items but didn't.");

       //Ensure orderly shutdown
       q.clear();
       r.waitUntilNoActivity();
    }

    @Issue("JENKINS-68780")
    @Test
    void downstreamProjectsInQueueBlock() throws Exception {

       FreeStyleProject a = r.createFreeStyleProject();
       FreeStyleProject b = r.createFreeStyleProject(BlockingQueueTaskDispatcher.NAME_OF_BLOCKED_PROJECT);
       a.getPublishersList().add(new BuildTrigger(b.getName(), true));
       a.setBlockBuildWhenDownstreamBuilding(true);

       r.jenkins.rebuildDependencyGraph();

       b.scheduleBuild(0, new UserIdCause());

       Queue q = r.jenkins.getQueue();

       waitUntilWaitingListIsEmpty(q);

       a.scheduleBuild(0, new UserIdCause());

       waitUntilWaitingListIsEmpty(q);

       // This call is necessary because the queue blocks projects
       // at first only temporarily. By calling the maintain method
       // all temporarily blocked projects either become buildable or
       // become permanently blocked
       q.scheduleMaintenance().get();

       assertEquals(2, q.getBlockedItems().size(), "Queue should contain two blocked items but didn't.");

       //Ensure orderly shutdown
       q.clear();
       r.waitUntilNoActivity();
    }

    @Issue("JENKINS-69850")
    @Test
    void handleCauseOfBlockageThatIsNull() throws Exception {

        FreeStyleProject a = r.createFreeStyleProject(BlockingQueueTaskDispatcher.NAME_OF_BLOCKED_PROJECT);
        FreeStyleProject b = r.createFreeStyleProject(BlockingQueueTaskDispatcher.NAME_OF_ANOTHER_BLOCKED_PROJECT);
        a.getPublishersList().add(new BuildTrigger(b.getName(), true));
        a.setBlockBuildWhenDownstreamBuilding(true);
        b.setBlockBuildWhenUpstreamBuilding(true);
        r.jenkins.rebuildDependencyGraph();

        Queue q = r.jenkins.getQueue();
        Queue.withLock(() -> {
            a.scheduleBuild(0, new UserIdCause());
            b.scheduleBuild(0, new UserIdCause());
            // Move both projects from pending to blocked
            q.maintain();

            q.save();
            // Loading blocked items sets the CauseOfBlockage to null
            q.load();
            // Before JENKINS-69850 was fixed the null CauseOfBlockage caused a stack overflow
            q.maintain();
        });

        //Ensure orderly shutdown
        q.clear();
        r.waitUntilNoActivity();
    }

    public static class TestFlyweightTask extends TestTask implements Queue.FlyweightTask {
        Executor exec;
        private final Label assignedLabel;

        public TestFlyweightTask(AtomicInteger cnt, Label assignedLabel) {
            super(cnt);
            this.assignedLabel = assignedLabel;
        }

        @Override protected void doRun() {
            exec = Executor.currentExecutor();
        }

        @Override public Label getAssignedLabel() {
            return assignedLabel;
        }

        public Computer getOwner() {
            return exec == null ? null : exec.getOwner();
        }
    }

    @Test
    void taskEquality() throws Exception {
        AtomicInteger cnt = new AtomicInteger();
        TestTask originalTask = new TestTask(cnt, true);
        ScheduleResult result = r.jenkins.getQueue().schedule2(originalTask, 0);
        assertTrue(result.isCreated());
        WaitingItem item = result.getCreateItem();
        assertFalse(r.jenkins.getQueue().schedule2(new TestTask(cnt), 0).isCreated());
        originalTask.isBlocked = false;
        item.getFuture().get();
        r.waitUntilNoActivity();
        assertEquals(1, cnt.get());
    }

    static class TestTask implements Queue.Task {
        private final AtomicInteger cnt;
        boolean isBlocked;

        TestTask(AtomicInteger cnt) {
            this(cnt, false);
        }

        TestTask(AtomicInteger cnt, boolean isBlocked) {
            this.cnt = cnt;
            this.isBlocked = isBlocked;
        }

        @Override public boolean equals(Object o) {
            return o instanceof TestTask && cnt == ((TestTask) o).cnt;
        }

        @Override public int hashCode() {
            return cnt.hashCode();
        }

        @Override public CauseOfBlockage getCauseOfBlockage() {
            return isBlocked ? CauseOfBlockage.fromMessage(Messages._Queue_Unknown()) : null;
        }

        @Override public String getName() {
            return "test";
        }

        @Override public String getFullDisplayName() {
            return "Test";
        }

        @Override public void checkAbortPermission() {}

        @Override public boolean hasAbortPermission() {
            return true;
        }

        @Override public String getUrl() {
            return "test/";
        }

        @Override public String getDisplayName() {
            return "Test";
        }

        @Override public ResourceList getResourceList() {
            return new ResourceList();
        }

        protected void doRun() {}

        @Override public Executable createExecutable() {
            return new Executable() {
                @Override public SubTask getParent() {
                    return TestTask.this;
                }

                @Override public long getEstimatedDuration() {
                    return -1;
                }

                @Override public void run() {
                    doRun();
                    cnt.incrementAndGet();
                }
            };
        }
    }

    @Test
    void waitForStart() throws Exception {
        final OneShotEvent ev = new OneShotEvent();
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
                ev.block();
                return true;
            }
        });

        QueueTaskFuture<FreeStyleBuild> v = p.scheduleBuild2(0);
        FreeStyleBuild b = v.waitForStart();
        assertEquals(1, b.getNumber());
        assertTrue(b.isBuilding());
        assertSame(p, b.getProject());

        ev.signal();    // let the build complete
        FreeStyleBuild b2 = r.assertBuildStatusSuccess(v);
        assertSame(b, b2);
    }

    /**
     * Make sure that the running build actually carries an credential.
     */
    @Test
    void accessControl() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(new MockQueueItemAuthenticator().authenticate(p.getFullName(), alice));
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
                assertEquals(alice, Jenkins.getAuthentication2());
                return true;
            }
        });
        r.buildAndAssertSuccess(p);
    }

    private static Authentication alice = new UsernamePasswordAuthenticationToken("alice", "alice", Collections.emptySet());


    /**
     * Make sure that the slave assignment honors the permissions.
     *
     * We do this test by letting a build run twice to determine its natural home,
     * and then introduce a security restriction to prohibit that.
     */
    @Test
    void permissionSensitiveSlaveAllocations() throws Exception {
        r.jenkins.setNumExecutors(0); // restrict builds to those agents
        DumbSlave s1 = r.createSlave();
        DumbSlave s2 = r.createSlave();

        FreeStyleProject p = r.createFreeStyleProject();
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(new MockQueueItemAuthenticator().authenticate(p.getFullName(), alice));
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
                assertEquals(alice, Jenkins.getAuthentication2());
                return true;
            }
        });

        final FreeStyleBuild b1 = r.buildAndAssertSuccess(p);
        final FreeStyleBuild b2 = r.buildAndAssertSuccess(p);

        // scheduling algorithm would prefer running the same job on the same node
        // kutzi: 'prefer' != 'enforce', therefore disabled this assertion: assertSame(b1.getBuiltOn(),b2.getBuiltOn());

        r.jenkins.setAuthorizationStrategy(new AliceCannotBuild(b1.getBuiltOnStr()));

        // now that we prohibit alice to do a build on the same node, the build should run elsewhere
        for (int i = 0; i < 3; i++) {
            FreeStyleBuild b3 = r.buildAndAssertSuccess(p);
            assertNotSame(b3.getBuiltOnStr(), b1.getBuiltOnStr());
        }
    }

    private static class AliceCannotBuild extends GlobalMatrixAuthorizationStrategy {
        private final String blocked;

        AliceCannotBuild(String blocked) {
            add(Jenkins.ADMINISTER, "anonymous");
            this.blocked = blocked;
        }

        @Override
        public ACL getACL(Node node) {
            if (node.getNodeName().equals(blocked)) {
                // ACL that allow anyone to do anything except Alice can't build.
                SparseACL acl = new SparseACL(null);
                acl.add(new PrincipalSid(alice), Computer.BUILD, false);
                acl.add(new PrincipalSid("anonymous"), Jenkins.ADMINISTER, true);
                return acl;
            }
            return super.getACL(node);
        }
    }

    @Test
    void pendingsConsistenceAfterErrorDuringMaintain() throws IOException, InterruptedException {
        FreeStyleProject project1 = r.createFreeStyleProject("project1");
        FreeStyleProject project2 = r.createFreeStyleProject("project2");
        TopLevelItemDescriptor descriptor = new TopLevelItemDescriptor(FreeStyleProject.class) {
         @Override
            public FreeStyleProject newInstance(ItemGroup parent, String name) {
                return new FreeStyleProject(parent, name) {
                     @Override
                    public Label getAssignedLabel() {
                        throw new IllegalArgumentException("Test exception"); //cause dead of executor
                    }

                    @Override
                     public void save(){
                         //do not need save
                     }
            };
        }
        };
        FreeStyleProject projectError = (FreeStyleProject) r.jenkins.createProject(descriptor, "throw-error");
        project1.setAssignedLabel(r.jenkins.getSelfLabel());
        project2.setAssignedLabel(r.jenkins.getSelfLabel());
        project1.getBuildersList().add(new Shell("sleep 2"));
        project1.scheduleBuild2(0);
        QueueTaskFuture<FreeStyleBuild> v = project2.scheduleBuild2(0);
        projectError.scheduleBuild2(0);
        Executor e = r.jenkins.toComputer().getExecutors().get(0);
        Thread.sleep(2000);
        while (project2.getLastBuild() == null) {
             if (!e.isAlive()) {
                    break; // executor is dead due to exception
             }
             if (e.isIdle()) {
                 assertTrue(v.isDone(), "Node went to idle before project had" + project2.getDisplayName() + " been started");
             }
             Thread.sleep(1000);
        }
        if (project2.getLastBuild() == null) {
            Queue.getInstance().cancel(projectError); // cancel job which cause dead of executor
            while (!e.isIdle()) { //executor should take project2 from queue
                Thread.sleep(1000);
            }
            //project2 should not be in pendings
            List<Queue.BuildableItem> items = Queue.getInstance().getPendingItems();
            for (Queue.BuildableItem item : items) {
                assertNotEquals(item.task.getName(), project2.getName(), "Project " + project2.getDisplayName() + " stuck in pendings");
            }
        }
        for (var p : r.jenkins.allItems(FreeStyleProject.class)) {
            for (var b : p.getBuilds()) {
                r.waitForCompletion(b);
                b.delete();
                Logger.getLogger(QueueTest.class.getName()).info(() -> "Waited for " + b);
            }
            p.delete();
            Logger.getLogger(QueueTest.class.getName()).info(() -> "Cleaned up " + p);
        }
    }

    @Test
    void cancelInQueue() throws Exception {
        // parepare an offline slave.
        DumbSlave slave = r.createOnlineSlave();
        assertFalse(slave.toComputer().isOffline());
        slave.toComputer().disconnect(null).get();
        assertTrue(slave.toComputer().isOffline());

        FreeStyleProject p = r.createFreeStyleProject();
        p.setAssignedNode(slave);

        QueueTaskFuture<FreeStyleBuild> f = p.scheduleBuild2(0);
        assertThrows(TimeoutException.class, () -> f.get(3, TimeUnit.SECONDS), "Should time out (as the agent is offline)");

        Queue.Item item = Queue.getInstance().getItem(p);
        assertNotNull(item);
        Queue.getInstance().doCancelItem(item.getId());
        assertNull(Queue.getInstance().getItem(p));

        assertThrows(CancellationException.class, () -> f.get(10, TimeUnit.SECONDS), "Should not get (as it is cancelled)");
    }

    @Test
    void waitForStartAndCancelBeforeStart() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();

        QueueTaskFuture<FreeStyleBuild> f = p.scheduleBuild2(30);
        final Queue.Item item = Queue.getInstance().getItem(p);
        assertNotNull(item);

        final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        executor.schedule(() -> {
               try {
                   Queue.getInstance().doCancelItem(item.getId());
               } catch (IOException e) {
                   throw new UncheckedIOException(e);
               } catch (ServletException e) {
                   throw new RuntimeException(e);
               }
        }, 2, TimeUnit.SECONDS);

        assertThrows(CancellationException.class, f::waitForStart);
    }

    @Disabled("TODO flakes in CI")
    @Issue("JENKINS-27871")
    @Test
    void testBlockBuildWhenUpstreamBuildingLock() throws Exception {
        final String prefix = "JENKINS-27871";
        r.getInstance().setNumExecutors(4);

        final FreeStyleProject projectA = r.createFreeStyleProject(prefix + "A");
        projectA.getBuildersList().add(new SleepBuilder(5000));

        final FreeStyleProject projectB = r.createFreeStyleProject(prefix + "B");
        projectB.getBuildersList().add(new SleepBuilder(10000));
        projectB.setBlockBuildWhenUpstreamBuilding(true);

        final FreeStyleProject projectC = r.createFreeStyleProject(prefix + "C");
        projectC.getBuildersList().add(new SleepBuilder(10000));
        projectC.setBlockBuildWhenUpstreamBuilding(true);

        projectA.getPublishersList().add(new BuildTrigger(List.of(projectB), Result.SUCCESS));
        projectB.getPublishersList().add(new BuildTrigger(List.of(projectC), Result.SUCCESS));

        final QueueTaskFuture<FreeStyleBuild> taskA = projectA.scheduleBuild2(0, new TimerTriggerCause());
        Thread.sleep(1000);
        final QueueTaskFuture<FreeStyleBuild> taskB = projectB.scheduleBuild2(0, new TimerTriggerCause());
        final QueueTaskFuture<FreeStyleBuild> taskC = projectC.scheduleBuild2(0, new TimerTriggerCause());

        final FreeStyleBuild buildA = taskA.get(60, TimeUnit.SECONDS);
        final FreeStyleBuild buildB = taskB.get(60, TimeUnit.SECONDS);
        final FreeStyleBuild buildC = taskC.get(60, TimeUnit.SECONDS);
        long buildBEndTime = buildB.getStartTimeInMillis() + buildB.getDuration();
        assertTrue(buildC.getStartTimeInMillis() >= buildBEndTime,
                "Project B build should be finished before the build of project C starts. " +
                "B finished at " + buildBEndTime + ", C started at " + buildC.getStartTimeInMillis());
    }

    @Issue({"SECURITY-186", "SECURITY-618"})
    @Test
    void queueApiOutputShouldBeFilteredByUserPermission() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        ProjectMatrixAuthorizationStrategy str = new ProjectMatrixAuthorizationStrategy();
        str.add(Jenkins.READ, "bob");
        str.add(Jenkins.READ, "alice");
        str.add(Jenkins.READ, "james");
        r.jenkins.setAuthorizationStrategy(str);

        FreeStyleProject project = r.createFreeStyleProject("project");

        Map<Permission, Set<String>> permissions = new HashMap<>();
        permissions.put(Item.READ, Set.of("bob"));
        permissions.put(Item.DISCOVER, Set.of("james"));
        AuthorizationMatrixProperty prop1 = new AuthorizationMatrixProperty(permissions);
        project.addProperty(prop1);
        project.getBuildersList().add(new SleepBuilder(10));
        project.scheduleBuild2(0);

        User alice = User.getById("alice", true);
        User bob = User.getById("bob", true);
        User james = User.getById("james", true);

        JenkinsRule.WebClient webClient = r.createWebClient();
        webClient.withBasicApiToken(bob);
        XmlPage p = webClient.goToXml("queue/api/xml");

        //bob has permission on the project and will be able to see it in the queue together with information such as the URL and the name.
        for (DomNode element : p.getFirstChild().getFirstChild().getChildNodes()) {
            if (element.getNodeName().equals("task")) {
                for (DomNode child : element.getChildNodes()) {
                    if (child.getNodeName().equals("name")) {
                        assertEquals("project", child.asNormalizedText());
                    } else if (child.getNodeName().equals("url")) {
                        assertNotNull(child.asNormalizedText());
                    }
                }
            }
        }

        webClient = r.createWebClient();
        webClient.withBasicApiToken(alice);
        XmlPage p2 = webClient.goToXml("queue/api/xml");
        //alice does not have permission on the project and will not see it in the queue.
        assertTrue(p2.getByXPath("/queue/node()").isEmpty());

        webClient = r.createWebClient();
        webClient.withBasicApiToken(james);
        XmlPage p3 = webClient.goToXml("queue/api/xml");

        //james has DISCOVER permission on the project and will only be able to see the task name.
        List projects = p3.getByXPath("/queue/discoverableItem/task/name/text()");
        assertEquals(1, projects.size());
        assertEquals("project", projects.get(0).toString());

        // Also check individual item exports.
        String url = project.getQueueItem().getUrl() + "api/xml";
        r.createWebClient().withBasicApiToken(bob).goToXml(url); // OK, 200
        r.createWebClient().withBasicApiToken(james).assertFails(url, HttpURLConnection.HTTP_FORBIDDEN); // only DISCOVER → AccessDeniedException
        r.createWebClient().withBasicApiToken(alice).assertFails(url, HttpURLConnection.HTTP_NOT_FOUND); // not even DISCOVER

        // Clear the queue
        assertTrue(r.jenkins.getQueue().cancel(project));
    }

    //we force the project not to be executed so that it stays in the queue
    @TestExtension("queueApiOutputShouldBeFilteredByUserPermission")
    public static class MyQueueTaskDispatcher extends QueueTaskDispatcher {
        @Override
        public CauseOfBlockage canTake(Node node, Queue.BuildableItem item) {
            return new CauseOfBlockage() {
                @Override
                public String getShortDescription() {
                    return "blocked by canTake";
                }
            };
        }
    }

    @Test
    void testGetCauseOfBlockageForNonConcurrentFreestyle() throws Exception {
        Queue queue = r.getInstance().getQueue();
        FreeStyleProject t1 = r.createFreeStyleProject("project");
        t1.getBuildersList().add(new SleepBuilder(TimeUnit.SECONDS.toMillis(30)));
        t1.setConcurrentBuild(false);

        FreeStyleBuild build = t1.scheduleBuild2(0).waitForStart();
        t1.scheduleBuild2(0);

        queue.maintain();

        assertEquals(1, r.jenkins.getQueue().getBlockedItems().size());
        CauseOfBlockage actual = r.jenkins.getQueue().getBlockedItems().get(0).getCauseOfBlockage();
        CauseOfBlockage expected = new BlockedBecauseOfBuildInProgress(t1.getFirstBuild());

        assertEquals(expected.getShortDescription(), actual.getShortDescription());
        Queue.getInstance().doCancelItem(r.jenkins.getQueue().getBlockedItems().get(0).getId());
        r.assertBuildStatusSuccess(r.waitForCompletion(build));
    }

    @Test
    @LocalData
    void load_queue_xml() {
        Queue q = r.getInstance().getQueue();
        Queue.Item[] items = q.getItems();
        assertEquals(11, items.length, Arrays.asList(items).toString());
        assertEquals(0, QueueSaveSniffer.count, "Loading the queue should not generate saves");

        // Clear the queue
        assertTrue(r.jenkins.getQueue().cancel(r.jenkins.getItemByFullName("a", FreeStyleProject.class)));
        assertTrue(r.jenkins.getQueue().cancel(r.jenkins.getItemByFullName("b", FreeStyleProject.class)));
        assertTrue(r.jenkins.getQueue().cancel(r.jenkins.getItemByFullName("c", FreeStyleProject.class)));
        assertTrue(r.jenkins.getQueue().cancel(r.jenkins.getItemByFullName("d", FreeStyleProject.class)));
        assertTrue(r.jenkins.getQueue().cancel(r.jenkins.getItemByFullName("e", FreeStyleProject.class)));
        assertTrue(r.jenkins.getQueue().cancel(r.jenkins.getItemByFullName("f", FreeStyleProject.class)));
        assertTrue(r.jenkins.getQueue().cancel(r.jenkins.getItemByFullName("g", FreeStyleProject.class)));
        assertTrue(r.jenkins.getQueue().cancel(r.jenkins.getItemByFullName("h", FreeStyleProject.class)));
        assertTrue(r.jenkins.getQueue().cancel(r.jenkins.getItemByFullName("i", FreeStyleProject.class)));
        assertTrue(r.jenkins.getQueue().cancel(r.jenkins.getItemByFullName("j", FreeStyleProject.class)));
        assertTrue(r.jenkins.getQueue().cancel(r.jenkins.getItemByFullName("k", FreeStyleProject.class)));
    }

    @TestExtension("load_queue_xml")
    public static final class QueueSaveSniffer extends SaveableListener {
        private static int count = 0;

        @Override public void onChange(Saveable o, XmlFile file) {
            if (o instanceof Queue) {
                count++;
            }
        }
    }

    @Test
    @Issue("SECURITY-891")
    void doCancelItem_PermissionIsChecked() throws Exception {
        checkCancelOperationUsingUrl(item -> "queue/cancelItem?id=" + item.getId(), false);
    }

    @Test
    @Issue("SECURITY-891")
    void doCancelQueue_PermissionIsChecked() throws Exception {
        checkCancelOperationUsingUrl(item -> "queue/item/" + item.getId() + "/cancelQueue", true);
    }

    /**
     *
     * @param urlProvider the endpoint to query
     * @param legacyRedirect whether the endpoint has the legacy behavior (ie makes a redirect no matter the result)
     *                       Or it uses the newer response codes introduced by JENKINS-21311
     */
    private void checkCancelOperationUsingUrl(Function<Queue.Item, String> urlProvider, boolean legacyRedirect) throws Exception {
        Queue q = r.jenkins.getQueue();

        r.jenkins.setCrumbIssuer(null);
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Item.READ, Item.CANCEL).everywhere().to("admin")
                .grant(Jenkins.READ).everywhere().to("user")
        );

        // prevent execution to push stuff into the queue
        r.jenkins.setNumExecutors(0);
        assertThat(q.getItems().length, equalTo(0));

        FreeStyleProject testProject = r.createFreeStyleProject("test");
        assertNotNull(testProject.scheduleBuild2(0, new UserIdCause()));

        Queue.Item[] items = q.getItems();
        assertThat(items.length, equalTo(1));
        Queue.Item currentOne = items[0];
        assertFalse(currentOne.getFuture().isCancelled());

        WebRequest request = new WebRequest(new URI(r.getURL() + urlProvider.apply(currentOne)).toURL(), HttpMethod.POST);

        { // user without right cannot cancel
            JenkinsRule.WebClient wc = r.createWebClient()
                    .withRedirectEnabled(false)
                    .withThrowExceptionOnFailingStatusCode(false);
            wc.login("user");
            if (legacyRedirect) {
                Page p = wc.getPage(request);
                // the legacy endpoint returns a redirection to the previously visited page, none in our case
                // (so force no redirect to avoid false positive error)
                // see JENKINS-21311
                assertThat(p.getWebResponse().getStatusCode(), lessThan(400));
            }
            assertFalse(currentOne.getFuture().isCancelled());
        }
        { // user with right can
            JenkinsRule.WebClient wc = r.createWebClient()
                    .withRedirectEnabled(false)
                    .withThrowExceptionOnFailingStatusCode(false);
            wc.login("admin");
            Page p = wc.getPage(request);
            assertThat(p.getWebResponse().getStatusCode(), lessThan(400));

            assertTrue(currentOne.getFuture().isCancelled());
        }
    }

    @Test
    void flyweightsRunOnMasterIfPossible() throws Exception {
        r.createOnlineSlave();
        r.jenkins.setNumExecutors(0);
        List<TestFlyweightTask> tasks = new ArrayList<>();
        Queue q = r.jenkins.getQueue();

        for (int i = 0; i < 100; i++) {
            TestFlyweightTask task = new TestFlyweightTask(new AtomicInteger(i), null);
            tasks.add(task);
            q.schedule2(task, 0);
        }

        q.maintain();
        r.waitUntilNoActivityUpTo(10000);
        assertThat(tasks, everyItem(hasProperty("owner", equalTo(Jenkins.get().toComputer()))));
    }

    @Test
    void flyweightsRunOnAgentIfNecessary() throws Exception {
        r.createOnlineSlave();
        r.jenkins.setNumExecutors(0);
        r.jenkins.toComputer().setTemporarilyOffline(true, new OfflineCause.UserCause(null, null));
        List<TestFlyweightTask> tasks = new ArrayList<>();
        Queue q = r.jenkins.getQueue();

        for (int i = 0; i < 10; i++) {
            TestFlyweightTask task = new TestFlyweightTask(new AtomicInteger(i), null);
            tasks.add(task);
            q.schedule2(task, 0);
        }

        q.maintain();
        r.waitUntilNoActivityUpTo(10000);
        assertThat(tasks, everyItem(hasProperty("owner", not(equalTo(Jenkins.get().toComputer())))));
    }

    @Test
    @Issue("JENKINS-57805")
    void brokenAffinityKey() throws Exception {
        BrokenAffinityKeyProject brokenProject = r.createProject(BrokenAffinityKeyProject.class, "broken-project");
        // Before the JENKINS-57805 fix, the test times out because the `NullPointerException` repeatedly thrown from
        // `BrokenAffinityKeyProject.getAffinityKey()` prevents `Queue.maintain()` from completing.
        r.buildAndAssertSuccess(brokenProject);
    }

    @Test
    @Issue("SECURITY-1537")
    void regularTooltipDisplayedCorrectly() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();

        String expectedLabel = "\"expected label\"";
        p.setAssignedLabel(Label.get(expectedLabel));

        p.scheduleBuild2(0);

        String tooltip = buildAndExtractTooltipAttribute();
        assertThat(tooltip, containsString(expectedLabel.substring(1, expectedLabel.length() - 1)));

        // Clear the queue
        assertTrue(r.jenkins.getQueue().cancel(p));
    }

    @Test
    @Issue("SECURITY-1537")
    void preventXssInCauseOfBlocking() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.setAssignedLabel(Label.get("\"<img/src='x' onerror=alert(123)>xss\""));

        p.scheduleBuild2(0);

        String tooltip = buildAndExtractTooltipAttribute();
        assertThat(tooltip, not(containsString("<img")));
        assertThat(tooltip, containsString("&lt;"));

        // Clear the queue
        assertTrue(r.jenkins.getQueue().cancel(p));
    }

    private String buildAndExtractTooltipAttribute() throws Exception {
        JenkinsRule.WebClient wc = r.createWebClient();

        HtmlPage page = wc.goTo("");

        page.executeJavaScript("document.querySelector('#buildQueue a[tooltip]:not([tooltip=\"\"])')._tippy.show()");
        wc.waitForBackgroundJavaScript(1000);
        ScriptResult result = page.executeJavaScript("document.querySelector('.tippy-content').innerHTML;");

        return result.getJavaScriptResult().toString();
    }

    public static class BrokenAffinityKeyProject extends Project<BrokenAffinityKeyProject, BrokenAffinityKeyBuild> implements TopLevelItem {
        public BrokenAffinityKeyProject(ItemGroup parent, String name) {
            super(parent, name);
        }

        @Override
        public String getAffinityKey() {
            throw new NullPointerException("oops!");
        }

        @Override
        protected Class<BrokenAffinityKeyBuild> getBuildClass() {
            return BrokenAffinityKeyBuild.class;
        }

        @Override
        public TopLevelItemDescriptor getDescriptor() {
            return ExtensionList.lookupSingleton(DescriptorImpl.class);
        }

        @TestExtension("brokenAffinityKey")
        public static class DescriptorImpl extends AbstractProjectDescriptor {
            @Override
            public TopLevelItem newInstance(ItemGroup parent, String name) {
                return new BrokenAffinityKeyProject(parent, name);
            }

            @NonNull
            @Override
            public String getDisplayName() {
                return "Broken Affinity Key Project";
            }
        }
    }

    public static class BrokenAffinityKeyBuild extends Build<BrokenAffinityKeyProject, BrokenAffinityKeyBuild> {
        public BrokenAffinityKeyBuild(BrokenAffinityKeyProject project) throws IOException {
            super(project);
        }

        public BrokenAffinityKeyBuild(BrokenAffinityKeyProject project, File buildDir) throws IOException {
            super(project, buildDir);
        }

        @Override
        public void run() {
            execute(new BuildExecution());
        }
    }

    private static class SlowSlave extends Slave {
        SlowSlave(String name, File remoteFS, ComputerLauncher launcher) throws Descriptor.FormException, IOException {
            super(name, remoteFS.getAbsolutePath(), launcher);
        }

        @Override public Computer createComputer() {
            return new SlowComputer(this);
        }
    }

    private static class SlowComputer extends SlaveComputer {
        SlowComputer(SlowSlave slave) {
            super(slave);
        }

        @Override
        public boolean isOffline() {
            try {
                // This delay is just big enough to allow the test to simulate a computer failure at the time we expect.
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // ignore
            }
            return super.isOffline();
        }
    }

    @Test
    void computerFailsJustAfterCreatingExecutor() throws Throwable {
        r.jenkins.setNumExecutors(0);
        var p = r.createFreeStyleProject();
        p.setAssignedLabel(r.jenkins.getLabel("agent"));
        Slave onlineSlave = new SlowSlave("special", new File(r.jenkins.getRootDir(), "agent-work-dirs/special"), r.createComputerLauncher(null));
        onlineSlave.setLabelString("agent");
        onlineSlave.setRetentionStrategy(RetentionStrategy.NOOP);
        r.jenkins.addNode(onlineSlave);
        r.waitOnline(onlineSlave);

        var computer = onlineSlave.toComputer();
        Timer.get().execute(() -> {
            // Simulate a computer failure just after the executor is created
            while (computer.getExecutors().get(0).getStartTime() == 0) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            computer.disconnect(new OfflineCause.ChannelTermination(new IllegalStateException()));
        });
        var f = p.scheduleBuild2(0);
        await().until(computer::isOffline);
        Thread.sleep(1000);
        assertFalse(r.jenkins.getQueue().isEmpty(), "Queue item should be back as the executor got killed before it could be picked up");
        // Put the computer back online
        r.waitOnline(onlineSlave);
        r.assertBuildStatusSuccess(f);
        assertTrue(r.jenkins.getQueue().isEmpty());
    }
}
