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

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.html.DomNodeList;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlFileInput;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlFormUtil;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import hudson.ExtensionList;
import hudson.Functions;
import hudson.Launcher;
import hudson.XmlFile;
import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.LabelAxis;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.MatrixRun;
import hudson.matrix.TextAxis;
import hudson.model.Cause.RemoteCause;
import hudson.model.Cause.UserIdCause;
import hudson.model.Queue.BlockedItem;
import hudson.model.Queue.Executable;
import hudson.model.Queue.WaitingItem;
import hudson.model.labels.LabelExpression;
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
import hudson.slaves.DumbSlave;
import hudson.slaves.DummyCloudImpl;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.slaves.NodeProvisionerRule;
import hudson.slaves.OfflineCause;
import hudson.tasks.BatchFile;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Shell;
import hudson.triggers.SCMTrigger.SCMTriggerCause;
import hudson.triggers.TimerTrigger.TimerTriggerCause;
import hudson.util.OneShotEvent;
import hudson.util.XStream2;
import jenkins.model.BlockedBecauseOfBuildInProgress;
import jenkins.model.Jenkins;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import jenkins.security.apitoken.ApiTokenTestHelper;
import jenkins.triggers.ReverseBuildTrigger;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockQueueItemAuthenticator;
import org.jvnet.hudson.test.SequenceLock;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.LocalData;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Level;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Ignore;
import org.jvnet.hudson.test.LoggerRule;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * @author Kohsuke Kawaguchi
 */
public class QueueTest {

    @Rule public JenkinsRule r = new NodeProvisionerRule(-1, 0, 10);

    @Rule
    public LoggerRule logging = new LoggerRule().record(Queue.class, Level.FINE);

    /**
     * Checks the persistence of queue.
     */
    @Test public void persistence() throws Exception {
        Queue q = r.jenkins.getQueue();

        // prevent execution to push stuff into the queue
        r.jenkins.setNumExecutors(0);

        FreeStyleProject testProject = r.createFreeStyleProject("test");
        testProject.scheduleBuild(new UserIdCause());
        q.save();

        System.out.println(FileUtils.readFileToString(new File(r.jenkins.getRootDir(), "queue.xml")));

        assertEquals(1, q.getItems().length);
        q.clear();
        assertEquals(0,q.getItems().length);

        // load the contents back
        q.load();
        assertEquals(1, q.getItems().length);

        // did it bind back to the same object?
        assertSame(q.getItems()[0].task,testProject);
    }

    /**
     * Make sure the queue can be reconstructed from a List queue.xml.
     * Prior to the Queue.State class, the Queue items were just persisted as a List.
     */
    @LocalData
    @Test
    public void recover_from_legacy_list() throws Exception {
        Queue q = r.jenkins.getQueue();

        // loaded the legacy queue.xml from test LocalData located in
        // resources/hudson/model/QueueTest/recover_from_legacy_list.zip
        assertEquals(1, q.getItems().length);

        // The current counter should be the id from the item brought back
        // from the persisted queue.xml.
        assertEquals(3, Queue.WaitingItem.getCurrentCounterValue());
    }

    /**
     * Can {@link Queue} successfully recover removal?
     */
     @Test public void persistence2() throws Exception {
        Queue q = r.jenkins.getQueue();

        resetQueueState();
        assertEquals(0, Queue.WaitingItem.getCurrentCounterValue());

        // prevent execution to push stuff into the queue
        r.jenkins.setNumExecutors(0);

        FreeStyleProject testProject = r.createFreeStyleProject("test");
        testProject.scheduleBuild(new UserIdCause());
        q.save();

        System.out.println(FileUtils.readFileToString(new File(r.jenkins.getRootDir(), "queue.xml")));

        assertEquals(1, q.getItems().length);
        q.clear();
        assertEquals(0,q.getItems().length);

        // delete the project before loading the queue back
        testProject.delete();
        q.load();
        assertEquals(0,q.getItems().length);

        // The counter state should be maintained.
        assertEquals(1, Queue.WaitingItem.getCurrentCounterValue());
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
    public void queue_id_to_run_mapping() throws Exception {
        FreeStyleProject testProject = r.createFreeStyleProject("test");
        FreeStyleBuild build = r.assertBuildStatusSuccess(testProject.scheduleBuild2(0));
        Assert.assertNotEquals(Run.QUEUE_ID_UNKNOWN, build.getQueueId());
    }

    /**
     * {@link hudson.model.Queue.BlockedItem} is not static. Make sure its persistence doesn't end up re-persisting the whole Queue instance.
     */
    @Test public void persistenceBlockedItem() throws Exception {
        Queue q = r.jenkins.getQueue();
        final SequenceLock seq = new SequenceLock();

        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                seq.phase(0);   // first, we let one build going

                seq.phase(2);
                return true;
            }
        });

        Future<FreeStyleBuild> b1 = p.scheduleBuild2(0);
        seq.phase(1);   // and make sure we have one build under way

        // get another going
        Future<FreeStyleBuild> b2 = p.scheduleBuild2(0);

        q.scheduleMaintenance().get();
        Queue.Item[] items = q.getItems();
        assertEquals(1,items.length);
        assertTrue("Got "+items[0], items[0] instanceof BlockedItem);

        q.save();
    }

    public static final class FileItemPersistenceTestServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;
        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("text/html");
            resp.getWriter().println(
                    "<html><body><form action='/' method=post name=main enctype='multipart/form-data'>" +
                    "<input type=file name=test><input type=submit>"+
                    "</form></body></html>"
            );
        }

        @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            try {
                ServletFileUpload f = new ServletFileUpload(new DiskFileItemFactory());
                List<?> v = f.parseRequest(req);
                assertEquals(1,v.size());
                XStream2 xs = new XStream2();
                System.out.println(xs.toXML(v.get(0)));
            } catch (FileUploadException e) {
                throw new ServletException(e);
            }
        }
    }

    @Test public void fileItemPersistence() throws Exception {
        // TODO: write a synchronous connector?
        byte[] testData = new byte[1024];
        for( int i=0; i<testData.length; i++ )  testData[i] = (byte)i;


        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        server.addConnector(connector);

        ServletHandler handler = new ServletHandler();
        handler.addServletWithMapping(new ServletHolder(new FileItemPersistenceTestServlet()),"/");
        server.setHandler(handler);

        server.start();

        try {
            JenkinsRule.WebClient wc = r.createWebClient();
            @SuppressWarnings("deprecation")
            HtmlPage p = (HtmlPage) wc.getPage("http://localhost:" + connector.getLocalPort() + '/');
            HtmlForm f = p.getFormByName("main");
            HtmlFileInput input = (HtmlFileInput) f.getInputByName("test");
            input.setData(testData);
            HtmlFormUtil.submit(f);
        } finally {
            server.stop();
        }
    }

    @Issue("JENKINS-33467")
    @Test public void foldableCauseAction() throws Exception {
        final OneShotEvent buildStarted = new OneShotEvent();
        final OneShotEvent buildShouldComplete = new OneShotEvent();

        r.setQuietPeriod(0);
        FreeStyleProject project = r.createFreeStyleProject();
        // Make build sleep a while so it blocks new builds
        project.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                buildStarted.signal();
                buildShouldComplete.block();
                return true;
            }
        });

        // Start one build to block others
        assertTrue(project.scheduleBuild(new UserIdCause()));
        buildStarted.block(); // wait for the build to really start

        // Schedule a new build, and trigger it many ways while it sits in queue
        Future<FreeStyleBuild> fb = project.scheduleBuild2(0, new UserIdCause());
        assertNotNull(fb);
        assertTrue(project.scheduleBuild(new SCMTriggerCause("")));
        assertTrue(project.scheduleBuild(new UserIdCause()));
        assertTrue(project.scheduleBuild(new TimerTriggerCause()));
        assertTrue(project.scheduleBuild(new RemoteCause("1.2.3.4", "test")));
        assertTrue(project.scheduleBuild(new RemoteCause("4.3.2.1", "test")));
        assertTrue(project.scheduleBuild(new SCMTriggerCause("")));
        assertTrue(project.scheduleBuild(new RemoteCause("1.2.3.4", "test")));
        assertTrue(project.scheduleBuild(new RemoteCause("1.2.3.4", "foo")));
        assertTrue(project.scheduleBuild(new SCMTriggerCause("")));
        assertTrue(project.scheduleBuild(new TimerTriggerCause()));

        // Wait for 2nd build to finish
        buildShouldComplete.signal();
        FreeStyleBuild build = fb.get();

        // Make sure proper folding happened.
        CauseAction ca = build.getAction(CauseAction.class);
        assertNotNull(ca);
        StringBuilder causes = new StringBuilder();
        for (Cause c : ca.getCauses()) causes.append(c.getShortDescription() + "\n");
        assertEquals("Build causes should have all items, even duplicates",
                "Started by user SYSTEM\nStarted by user SYSTEM\n"
                + "Started by an SCM change\nStarted by an SCM change\nStarted by an SCM change\n"
                + "Started by timer\nStarted by timer\n"
                + "Started by remote host 1.2.3.4 with note: test\n"
                + "Started by remote host 1.2.3.4 with note: test\n"
                + "Started by remote host 4.3.2.1 with note: test\n"
                + "Started by remote host 1.2.3.4 with note: foo\n",
                causes.toString());

        // View for build should group duplicates
        JenkinsRule.WebClient wc = r.createWebClient();
        String nl = System.getProperty("line.separator");
        String buildPage = wc.getPage(build, "").asText().replace(nl," ");
        assertTrue("Build page should combine duplicates and show counts: " + buildPage,
                   buildPage.contains("Started by user SYSTEM (2 times) "
                        + "Started by an SCM change (3 times) "
                        + "Started by timer (2 times) "
                        + "Started by remote host 1.2.3.4 with note: test (2 times) "
                        + "Started by remote host 4.3.2.1 with note: test "
                        + "Started by remote host 1.2.3.4 with note: foo"));
        System.out.println(new XmlFile(new File(build.getRootDir(), "build.xml")).asString());
    }

    @Issue("JENKINS-8790")
    @Test public void flyweightTasks() throws Exception {
        MatrixProject m = r.jenkins.createProject(MatrixProject.class, "p");
        m.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("FOO","value")
        ));
        if (Functions.isWindows()) {
            m.getBuildersList().add(new BatchFile("ping -n 3 127.0.0.1 >nul"));
        } else {
            m.getBuildersList().add(new Shell("sleep 3"));
        }
        m.setAxes(new AxisList(new TextAxis("DoesntMatter", "aaa","bbb")));

        List<Future<MatrixBuild>> futures = new ArrayList<Future<MatrixBuild>>();

        for (int i = 0; i < 3; i++) {
            futures.add(m.scheduleBuild2(0, new UserIdCause(), new ParametersAction(new StringParameterValue("FOO", "value" + i))));
        }

        for (Future<MatrixBuild> f : futures) {
            r.assertBuildStatusSuccess(f);
        }
    }

    @Issue("JENKINS-7291")
    @Test public void flyweightTasksWithoutMasterExecutors() throws Exception {
        DummyCloudImpl cloud = new DummyCloudImpl(r, 0);
        cloud.label = r.jenkins.getLabel("remote");
        r.jenkins.clouds.add(cloud);
        r.jenkins.setNumExecutors(0);
        r.jenkins.setNodes(Collections.<Node>emptyList());
        MatrixProject m = r.jenkins.createProject(MatrixProject.class, "p");
        m.setAxes(new AxisList(new LabelAxis("label", Collections.singletonList("remote"))));
        MatrixBuild build;
        try {
            build = m.scheduleBuild2(0).get(60, TimeUnit.SECONDS);
        } catch (TimeoutException x) {
            throw (AssertionError) new AssertionError(r.jenkins.getQueue().getItems().toString()).initCause(x);
        }
        r.assertBuildStatusSuccess(build);
        assertEquals("", build.getBuiltOnStr());
        List<MatrixRun> runs = build.getRuns();
        assertEquals(1, runs.size());
        assertEquals("slave0", runs.get(0).getBuiltOnStr());
    }

    @Issue("JENKINS-10944")
    @Test public void flyweightTasksBlockedByShutdown() throws Exception {
        r.jenkins.doQuietDown(true, 0, null);
        AtomicInteger cnt = new AtomicInteger();
        TestFlyweightTask task = new TestFlyweightTask(cnt, null);
        assertTrue(Queue.isBlockedByShutdown(task));
        r.jenkins.getQueue().schedule2(task, 0);
        r.jenkins.getQueue().maintain();
        r.jenkins.doCancelQuietDown();
        assertFalse(Queue.isBlockedByShutdown(task));
        r.waitUntilNoActivity();
        assertEquals(1, cnt.get());
        assert task.exec instanceof OneOffExecutor : task.exec;
    }

    @Issue("JENKINS-24519")
    @Test public void flyweightTasksBlockedBySlave() throws Exception {
        Label label = Label.get("myslave");
        AtomicInteger cnt = new AtomicInteger();
        TestFlyweightTask task = new TestFlyweightTask(cnt, label);
        r.jenkins.getQueue().schedule2(task, 0);
        r.jenkins.getQueue().maintain();
        r.createSlave(label);
        r.waitUntilNoActivity();
        assertEquals(1, cnt.get());
        assert task.exec instanceof OneOffExecutor : task.exec;
    }

    @Issue("JENKINS-41127")
    @Test public void flyweightTasksUnwantedConcurrency() throws Exception {
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

    @Issue("JENKINS-27256")
    @Test public void inQueueTaskLookupByAPI() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        Label label = Label.get("unknown-slave");

        // Give the project an "unknown-slave" label, forcing it to
        // stay in the queue after we schedule it, allowing us to query it.
        p.setAssignedLabel(label);
        p.scheduleBuild2(0);

        JenkinsRule.WebClient webclient = r.createWebClient();

        XmlPage queueItems = webclient.goToXml("queue/api/xml");
        String queueTaskId = queueItems.getXmlDocument().getElementsByTagName("id").item(0).getTextContent();
        assertNotNull(queueTaskId);
        XmlPage queueItem = webclient.goToXml("queue/item/" + queueTaskId + "/api/xml");
        assertNotNull(queueItem);
        String tagName = queueItem.getDocumentElement().getTagName();
        assertTrue(tagName.equals("blockedItem") || tagName.equals("buildableItem"));
    }
    
    @Issue("JENKINS-28926")
    @Test
    public void upstreamDownstreamCycle() throws Exception {
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

    @Test public void taskEquality() throws Exception {
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
        @Override public CauseOfBlockage getCauseOfBlockage() {return isBlocked ? CauseOfBlockage.fromMessage(Messages._Queue_Unknown()) : null;}
        @Override public String getName() {return "test";}
        @Override public String getFullDisplayName() {return "Test";}
        @Override public void checkAbortPermission() {}
        @Override public boolean hasAbortPermission() {return true;}
        @Override public String getUrl() {return "test/";}
        @Override public String getDisplayName() {return "Test";}
        @Override public ResourceList getResourceList() {return new ResourceList();}
        protected void doRun() {}
        @Override public Executable createExecutable() throws IOException {
            return new Executable() {
                @Override public SubTask getParent() {return TestTask.this;}
                @Override public long getEstimatedDuration() {return -1;}
                @Override public void run() {
                    doRun();
                    cnt.incrementAndGet();
                }
            };
        }
    }

    @Test public void waitForStart() throws Exception {
        final OneShotEvent ev = new OneShotEvent();
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                ev.block();
                return true;
            }
        });

        QueueTaskFuture<FreeStyleBuild> v = p.scheduleBuild2(0);
        FreeStyleBuild b = v.waitForStart();
        assertEquals(1,b.getNumber());
        assertTrue(b.isBuilding());
        assertSame(p, b.getProject());

        ev.signal();    // let the build complete
        FreeStyleBuild b2 = r.assertBuildStatusSuccess(v);
        assertSame(b, b2);
    }

    /**
     * Make sure that the running build actually carries an credential.
     */
    @Test public void accessControl() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(new MockQueueItemAuthenticator(Collections.singletonMap(p.getFullName(), alice)));
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                assertEquals(alice2, Jenkins.getAuthentication2());
                return true;
            }
        });
        r.assertBuildStatusSuccess(p.scheduleBuild2(0));
    }

    private static Authentication alice2 = new UsernamePasswordAuthenticationToken("alice","alice", Collections.emptySet());
    private static org.acegisecurity.Authentication alice = org.acegisecurity.Authentication.fromSpring(alice2);


    /**
     * Make sure that the slave assignment honors the permissions.
     *
     * We do this test by letting a build run twice to determine its natural home,
     * and then introduce a security restriction to prohibit that.
     */
    @Test public void permissionSensitiveSlaveAllocations() throws Exception {
        r.jenkins.setNumExecutors(0); // restrict builds to those agents
        DumbSlave s1 = r.createSlave();
        DumbSlave s2 = r.createSlave();

        FreeStyleProject p = r.createFreeStyleProject();
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(new MockQueueItemAuthenticator(Collections.singletonMap(p.getFullName(), alice)));
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                assertEquals(alice2, Jenkins.getAuthentication2());
                return true;
            }
        });

        final FreeStyleBuild b1 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        final FreeStyleBuild b2 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));

        // scheduling algorithm would prefer running the same job on the same node
        // kutzi: 'prefer' != 'enforce', therefore disabled this assertion: assertSame(b1.getBuiltOn(),b2.getBuiltOn());

        r.jenkins.setAuthorizationStrategy(new AliceCannotBuild(b1.getBuiltOnStr()));

        // now that we prohibit alice to do a build on the same node, the build should run elsewhere
        for (int i=0; i<3; i++) {
            FreeStyleBuild b3 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
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
                acl.add(new PrincipalSid(alice2), Computer.BUILD, false);
                acl.add(new PrincipalSid("anonymous"), Jenkins.ADMINISTER, true);
                return acl;
            }
            return super.getACL(node);
        }
    }

    @Test public void pendingsConsistenceAfterErrorDuringMaintain() throws IOException, ExecutionException, InterruptedException{
        FreeStyleProject project1 = r.createFreeStyleProject();
        FreeStyleProject project2 = r.createFreeStyleProject();
        TopLevelItemDescriptor descriptor = new TopLevelItemDescriptor(FreeStyleProject.class){
         @Override
            public FreeStyleProject newInstance(ItemGroup parent, String name) {
                return (FreeStyleProject) new FreeStyleProject(parent,name){
                     @Override
                    public Label getAssignedLabel(){
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
        while(project2.getLastBuild()==null){
             if(!e.isAlive()){
                    break; // executor is dead due to exception
             }
             if(e.isIdle()){
                 assertTrue("Node went to idle before project had" + project2.getDisplayName() + " been started", v.isDone());
             }
                Thread.sleep(1000);
        }
        if(project2.getLastBuild()!=null)
            return;
        Queue.getInstance().cancel(projectError); // cancel job which cause dead of executor
        while(!e.isIdle()){ //executor should take project2 from queue
            Thread.sleep(1000);
        }
        //project2 should not be in pendings
        List<Queue.BuildableItem> items = Queue.getInstance().getPendingItems();
        for(Queue.BuildableItem item : items){
            assertFalse("Project " + project2.getDisplayName() + " stuck in pendings",item.task.getName().equals(project2.getName()));
        }
    }

    @Test public void cancelInQueue() throws Exception {
        // parepare an offline slave.
        DumbSlave slave = r.createOnlineSlave();
        assertFalse(slave.toComputer().isOffline());
        slave.toComputer().disconnect(null).get();
        assertTrue(slave.toComputer().isOffline());

        FreeStyleProject p = r.createFreeStyleProject();
        p.setAssignedNode(slave);

        QueueTaskFuture<FreeStyleBuild> f = p.scheduleBuild2(0);
        try {
            f.get(3, TimeUnit.SECONDS);
            fail("Should time out (as the slave is offline).");
        } catch (TimeoutException e) {
        }

        Queue.Item item = Queue.getInstance().getItem(p);
        assertNotNull(item);
        Queue.getInstance().doCancelItem(item.getId());
        assertNull(Queue.getInstance().getItem(p));

        try {
            f.get(10, TimeUnit.SECONDS);
            fail("Should not get (as it is cancelled).");
        } catch (CancellationException e) {
        }
    }

    @Test public void waitForStartAndCancelBeforeStart() throws Exception {
        final OneShotEvent ev = new OneShotEvent();
        FreeStyleProject p = r.createFreeStyleProject();

        QueueTaskFuture<FreeStyleBuild> f = p.scheduleBuild2(10);
        final Queue.Item item = Queue.getInstance().getItem(p);
        assertNotNull(item);

        final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        executor.schedule(new Runnable() {
            @Override
            public void run() {
                   try {
                       Queue.getInstance().doCancelItem(item.getId());
                   } catch (IOException | ServletException e) {
                       e.printStackTrace();
                   }
            }
            }, 2, TimeUnit.SECONDS);

        try {
            f.waitForStart();
            fail("Expected an CancellationException to be thrown");
        } catch (CancellationException e) {}
    }

    @Ignore("TODO flakes in CI")
    @Issue("JENKINS-27871")
    @Test public void testBlockBuildWhenUpstreamBuildingLock() throws Exception {
        final String prefix = "JENKINS-27871";
        r.getInstance().setNumExecutors(4);
        
        final FreeStyleProject projectA = r.createFreeStyleProject(prefix+"A");
        projectA.getBuildersList().add(new SleepBuilder(5000));
        
        final FreeStyleProject projectB = r.createFreeStyleProject(prefix+"B");
        projectB.getBuildersList().add(new SleepBuilder(10000));     
        projectB.setBlockBuildWhenUpstreamBuilding(true);

        final FreeStyleProject projectC = r.createFreeStyleProject(prefix+"C");
        projectC.getBuildersList().add(new SleepBuilder(10000));
        projectC.setBlockBuildWhenUpstreamBuilding(true);
        
        projectA.getPublishersList().add(new BuildTrigger(Collections.singletonList(projectB), Result.SUCCESS));
        projectB.getPublishersList().add(new BuildTrigger(Collections.singletonList(projectC), Result.SUCCESS));
        
        final QueueTaskFuture<FreeStyleBuild> taskA = projectA.scheduleBuild2(0, new TimerTriggerCause());
        Thread.sleep(1000);
        final QueueTaskFuture<FreeStyleBuild> taskB = projectB.scheduleBuild2(0, new TimerTriggerCause());
        final QueueTaskFuture<FreeStyleBuild> taskC = projectC.scheduleBuild2(0, new TimerTriggerCause());
        
        final FreeStyleBuild buildA = taskA.get(60, TimeUnit.SECONDS);       
        final FreeStyleBuild buildB = taskB.get(60, TimeUnit.SECONDS);     
        final FreeStyleBuild buildC = taskC.get(60, TimeUnit.SECONDS);
        long buildBEndTime = buildB.getStartTimeInMillis() + buildB.getDuration();
        assertTrue("Project B build should be finished before the build of project C starts. " +
                "B finished at " + buildBEndTime + ", C started at " + buildC.getStartTimeInMillis(), 
                buildC.getStartTimeInMillis() >= buildBEndTime);
    }

    @Issue("JENKINS-30084")
    @Test
    /*
     * When a flyweight task is restricted to run on a specific node, the node will be provisioned
     * and the flyweight task will be executed.
     */
    public void shouldRunFlyweightTaskOnProvisionedNodeWhenNodeRestricted() throws Exception {
        MatrixProject matrixProject = r.jenkins.createProject(MatrixProject.class, "p");
        matrixProject.setAxes(new AxisList(
                new Axis("axis", "a", "b")
        ));
        Label label = LabelExpression.get("aws-linux-dummy");
        DummyCloudImpl dummyCloud = new DummyCloudImpl(r, 0);
        dummyCloud.label = label;
        r.jenkins.clouds.add(dummyCloud);
        matrixProject.setAssignedLabel(label);
        r.assertBuildStatusSuccess(matrixProject.scheduleBuild2(0));
        assertEquals("aws-linux-dummy", matrixProject.getBuilds().getLastBuild().getBuiltOn().getLabelString());
    }

    @Ignore("TODO too flaky; upstream can finish before we even examine the queue")
    @Issue("JENKINS-30084")
    @Test
    public void shouldBeAbleToBlockFlyweightTaskAtTheLastMinute() throws Exception {
        MatrixProject matrixProject = r.jenkins.createProject(MatrixProject.class, "downstream");
        matrixProject.setDisplayName("downstream");
        matrixProject.setAxes(new AxisList(
                new Axis("axis", "a", "b")
        ));

        Label label = LabelExpression.get("aws-linux-dummy");
        DummyCloudImpl dummyCloud = new DummyCloudImpl(r, 0);
        dummyCloud.label = label;
        BlockDownstreamProjectExecution property = new BlockDownstreamProjectExecution();
        dummyCloud.getNodeProperties().add(property);
        r.jenkins.clouds.add(dummyCloud);
        matrixProject.setAssignedLabel(label);

        FreeStyleProject upstreamProject = r.createFreeStyleProject("upstream");
        upstreamProject.getBuildersList().add(new SleepBuilder(10000));
        upstreamProject.setDisplayName("upstream");

        //let's assume the flyweighttask has an upstream project and that must be blocked
        // when the upstream project is running
        matrixProject.addTrigger(new ReverseBuildTrigger("upstream", Result.SUCCESS));
        matrixProject.setBlockBuildWhenUpstreamBuilding(true);

        //we schedule the project but we pretend no executors are available thus
        //the flyweight task is in the buildable queue without being executed
        QueueTaskFuture downstream = matrixProject.scheduleBuild2(0);
        if (downstream == null) {
            throw new Exception("the flyweight task could not be scheduled, thus the test will be interrupted");
        }
        //let s wait for the Queue instance to be updated
        while (Queue.getInstance().getBuildableItems().size() != 1) {
            Thread.sleep(10);
        }
        //in this state the build is not blocked, it's just waiting for an available executor
        assertFalse(Queue.getInstance().getItems()[0].isBlocked());

        //we start the upstream project that should block the downstream one
        QueueTaskFuture upstream = upstreamProject.scheduleBuild2(0);
        if (upstream == null) {
            throw new Exception("the upstream task could not be scheduled, thus the test will be interrupted");
        }
        //let s wait for the Upstream to enter the buildable Queue
        Thread.sleep(1000);
        boolean enteredTheQueue = false;
        while (!enteredTheQueue) {
            for (Queue.BuildableItem item : Queue.getInstance().getBuildableItems()) {
                if (item.task.getDisplayName() != null && item.task.getDisplayName().equals(upstreamProject.getDisplayName())) {
                    enteredTheQueue = true;
                }
            }
            Thread.sleep(10);
        }
        //let's wait for the upstream project to actually start so that we're sure the Queue has been updated
        //when the upstream starts the downstream has already left the buildable queue and the queue is empty
        while (!Queue.getInstance().getBuildableItems().isEmpty()) {
            Thread.sleep(10);
        }
        assertTrue(Queue.getInstance().getItems()[0].isBlocked());
        assertTrue(Queue.getInstance().getBlockedItems().get(0).task.getDisplayName().equals(matrixProject.displayName));

        //once the upstream is completed, the downstream can join the buildable queue again.
        r.assertBuildStatusSuccess(upstream);
        while (Queue.getInstance().getBuildableItems().isEmpty()) {
            Thread.sleep(10);
        }
        assertFalse(Queue.getInstance().getItems()[0].isBlocked());
        assertTrue(Queue.getInstance().getBlockedItems().isEmpty());
        assertTrue(Queue.getInstance().getBuildableItems().get(0).task.getDisplayName().equals(matrixProject.displayName));
    }

    //let's make sure that the downstream project is not started before the upstream --> we want to simulate
    // the case: buildable-->blocked-->buildable
    public static class BlockDownstreamProjectExecution extends NodeProperty<Slave> {
        @Override
        public CauseOfBlockage canTake(Queue.BuildableItem item) {
            if (item.task.getName().equals("downstream")) {
                return new CauseOfBlockage() {
                    @Override
                    public String getShortDescription() {
                        return "slave not provisioned";
                    }
                };
            }
            return null;
        }

        @TestExtension("shouldBeAbleToBlockFlyWeightTaskOnLastMinute")
        public static class DescriptorImpl extends NodePropertyDescriptor {}
    }

    @Issue({"SECURITY-186", "SECURITY-618"})
    @Test
    public void queueApiOutputShouldBeFilteredByUserPermission() throws Exception {
        ApiTokenTestHelper.enableLegacyBehavior();

        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        ProjectMatrixAuthorizationStrategy str = new ProjectMatrixAuthorizationStrategy();
        str.add(Jenkins.READ, "bob");
        str.add(Jenkins.READ, "alice");
        str.add(Jenkins.READ, "james");
        r.jenkins.setAuthorizationStrategy(str);

        FreeStyleProject project = r.createFreeStyleProject("project");

        Map<Permission, Set<String>> permissions = new HashMap<Permission, Set<String>>();
        permissions.put(Item.READ, Collections.singleton("bob"));
        permissions.put(Item.DISCOVER, Collections.singleton("james"));
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
        for (DomNode element: p.getFirstChild().getFirstChild().getChildNodes()){
            if (element.getNodeName().equals("task")) {
                for (DomNode child: ((DomElement) element).getChildNodes()) {
                    if (child.getNodeName().equals("name")) {
                        assertEquals(child.asText(), "project");
                    } else if (child.getNodeName().equals("url")) {
                        assertNotNull(child.asText());
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
    public void testGetCauseOfBlockageForNonConcurrentFreestyle() throws Exception {
        Queue queue = r.getInstance().getQueue();
        FreeStyleProject t1 = r.createFreeStyleProject("project");
        t1.getBuildersList().add(new SleepBuilder(TimeUnit.SECONDS.toMillis(30)));
        t1.setConcurrentBuild(false);

        t1.scheduleBuild2(0).waitForStart();
        t1.scheduleBuild2(0);

        queue.maintain();

        assertEquals(1, r.jenkins.getQueue().getBlockedItems().size());
        CauseOfBlockage actual = r.jenkins.getQueue().getBlockedItems().get(0).getCauseOfBlockage();
        CauseOfBlockage expected = new BlockedBecauseOfBuildInProgress(t1.getFirstBuild());

        assertEquals(expected.getShortDescription(), actual.getShortDescription());
    }

    @Test @LocalData
    public void load_queue_xml() {
        Queue q = r.getInstance().getQueue();
        Queue.Item[] items = q.getItems();
        assertEquals(Arrays.asList(items).toString(), 11, items.length);
        assertEquals("Loading the queue should not generate saves", 0, QueueSaveSniffer.count);
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
    public void doCancelItem_PermissionIsChecked() throws Exception {
        checkCancelOperationUsingUrl(item -> "queue/cancelItem?id=" + item.getId(), false);
    }

    @Test
    @Issue("SECURITY-891")
    public void doCancelQueue_PermissionIsChecked() throws Exception {
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
                .grant(Jenkins.READ, Item.CANCEL).everywhere().to("admin")
                .grant(Jenkins.READ).everywhere().to("user")
        );

        // prevent execution to push stuff into the queue
        r.jenkins.setNumExecutors(0);
        assertThat(q.getItems().length, equalTo(0));

        FreeStyleProject testProject = r.createFreeStyleProject("test");
        testProject.scheduleBuild(new UserIdCause());

        Queue.Item[] items = q.getItems();
        assertThat(items.length, equalTo(1));
        Queue.Item currentOne = items[0];
        assertFalse(currentOne.getFuture().isCancelled());

        WebRequest request = new WebRequest(new URL(r.getURL() + urlProvider.apply(currentOne)), HttpMethod.POST);

        { // user without right cannot cancel
            JenkinsRule.WebClient wc = r.createWebClient()
                    .withRedirectEnabled(false)
                    .withThrowExceptionOnFailingStatusCode(false);
            wc.login("user");
            if(legacyRedirect) {
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
    public void flyweightsRunOnMasterIfPossible() throws Exception {
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
    public void flyweightsRunOnAgentIfNecessary() throws Exception {
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
    public void brokenAffinityKey() throws Exception {
        BrokenAffinityKeyProject brokenProject = r.createProject(BrokenAffinityKeyProject.class, "broken-project");
        // Before the JENKINS-57805 fix, the test times out because the `NullPointerException` repeatedly thrown from
        // `BrokenAffinityKeyProject.getAffinityKey()` prevents `Queue.maintain()` from completing.
        r.buildAndAssertSuccess(brokenProject);
    }

    @Test
    @Issue("SECURITY-1537")
    public void regularTooltipDisplayedCorrectly() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();

        String expectedLabel = "\"expected label\"";
        p.setAssignedLabel(Label.get(expectedLabel));

        p.scheduleBuild2(0);

        String tooltip = buildAndExtractTooltipAttribute();
        assertThat(tooltip, containsString(expectedLabel.substring(1, expectedLabel.length() - 1)));
    }

    @Test
    @Issue("SECURITY-1537")
    public void preventXssInCauseOfBlocking() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.setAssignedLabel(Label.get("\"<img/src='x' onerror=alert(123)>xss\""));

        p.scheduleBuild2(0);

        String tooltip = buildAndExtractTooltipAttribute();
        assertThat(tooltip, not(containsString("<img")));
        assertThat(tooltip, containsString("&lt;"));
    }

    private String buildAndExtractTooltipAttribute() throws Exception {
        JenkinsRule.WebClient wc = r.createWebClient();

        HtmlPage page = wc.goTo("");

        DomElement buildQueue = page.getElementById("buildQueue");
        DomNodeList<HtmlElement> anchors = buildQueue.getElementsByTagName("a");
        HtmlAnchor anchorWithTooltip = (HtmlAnchor) anchors.stream()
                .filter(a -> StringUtils.isNotEmpty(a.getAttribute("tooltip")))
                .findFirst().orElseThrow(IllegalStateException::new);

        String tooltip = anchorWithTooltip.getAttribute("tooltip");
        return tooltip;
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
}
