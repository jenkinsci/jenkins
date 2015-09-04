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

import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.html.HtmlFileInput;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import hudson.Launcher;
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
import hudson.model.queue.AbstractQueueTask;
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
import hudson.slaves.NodeProvisioner;
import hudson.tasks.Shell;
import hudson.triggers.SCMTrigger.SCMTriggerCause;
import hudson.triggers.TimerTrigger.TimerTriggerCause;
import hudson.util.OneShotEvent;
import hudson.util.XStream2;
import java.io.File;
import java.io.IOException;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jenkins.model.Jenkins;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.MockQueueItemAuthenticator;
import org.jvnet.hudson.test.SequenceLock;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.ServletHolder;

/**
 * @author Kohsuke Kawaguchi
 */
public class QueueTest extends HudsonTestCase {
    /**
     * Checks the persistence of queue.
     */
    public void testPersistence() throws Exception {
        Queue q = jenkins.getQueue();

        // prevent execution to push stuff into the queue
        jenkins.setNumExecutors(0);
        jenkins.setNodes(jenkins.getNodes());

        FreeStyleProject testProject = createFreeStyleProject("test");
        testProject.scheduleBuild(new UserIdCause());
        q.save();

        System.out.println(FileUtils.readFileToString(new File(jenkins.getRootDir(), "queue.xml")));

        assertEquals(1,q.getItems().length);
        q.clear();
        assertEquals(0,q.getItems().length);

        // load the contents back
        q.load();
        assertEquals(1,q.getItems().length);

        // did it bind back to the same object?
        assertSame(q.getItems()[0].task,testProject);        
    }

    /**
     * Can {@link Queue} successfully recover removal?
     */
    public void testPersistence2() throws Exception {
        Queue q = jenkins.getQueue();

        // prevent execution to push stuff into the queue
        jenkins.setNumExecutors(0);
        jenkins.setNodes(jenkins.getNodes());

        FreeStyleProject testProject = createFreeStyleProject("test");
        testProject.scheduleBuild(new UserIdCause());
        q.save();

        System.out.println(FileUtils.readFileToString(new File(jenkins.getRootDir(), "queue.xml")));

        assertEquals(1,q.getItems().length);
        q.clear();
        assertEquals(0,q.getItems().length);

        // delete the project before loading the queue back
        testProject.delete();
        q.load();
        assertEquals(0,q.getItems().length);
    }

    /**
     * {@link hudson.model.Queue.BlockedItem} is not static. Make sure its persistence doesn't end up re-persisting the whole Queue instance.
     */
    public void testPersistenceBlockedItem() throws Exception {
        Queue q = jenkins.getQueue();
        final SequenceLock seq = new SequenceLock();

        FreeStyleProject p = createFreeStyleProject();
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

    public void testFileItemPersistence() throws Exception {
        // TODO: write a synchronous connector?
        byte[] testData = new byte[1024];
        for( int i=0; i<testData.length; i++ )  testData[i] = (byte)i;


        Server server = new Server();
        SocketConnector connector = new SocketConnector();
        server.addConnector(connector);

        ServletHandler handler = new ServletHandler();
        handler.addServletWithMapping(new ServletHolder(new FileItemPersistenceTestServlet()),"/");
        server.addHandler(handler);

        server.start();

        localPort = connector.getLocalPort();

        try {
            WebClient wc = new WebClient();
            HtmlPage p = (HtmlPage) wc.getPage("http://localhost:" + localPort + '/');
            HtmlForm f = p.getFormByName("main");
            HtmlFileInput input = (HtmlFileInput) f.getInputByName("test");
            input.setData(testData);
            f.submit();
        } finally {
            server.stop();
        }
    }

    public void testFoldableCauseAction() throws Exception {
        final OneShotEvent buildStarted = new OneShotEvent();
        final OneShotEvent buildShouldComplete = new OneShotEvent();

        setQuietPeriod(0);
        FreeStyleProject project = createFreeStyleProject();
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
                "Started by user SYSTEM\nStarted by an SCM change\n"
                + "Started by user SYSTEM\nStarted by timer\n"
                + "Started by remote host 1.2.3.4 with note: test\n"
                + "Started by remote host 4.3.2.1 with note: test\n"
                + "Started by an SCM change\n"
                + "Started by remote host 1.2.3.4 with note: test\n"
                + "Started by remote host 1.2.3.4 with note: foo\n"
                + "Started by an SCM change\nStarted by timer\n",
                causes.toString());

        // View for build should group duplicates
        WebClient wc = new WebClient();
        String nl = System.getProperty("line.separator");
        String buildPage = wc.getPage(build, "").asText().replace(nl," ");
        assertTrue("Build page should combine duplicates and show counts: " + buildPage,
                   buildPage.contains("Started by user SYSTEM (2 times) "
                        + "Started by an SCM change (3 times) "
                        + "Started by timer (2 times) "
                        + "Started by remote host 1.2.3.4 with note: test (2 times) "
                        + "Started by remote host 4.3.2.1 with note: test "
                        + "Started by remote host 1.2.3.4 with note: foo"));
    }

    @Bug(8790)
    public void testFlyweightTasks() throws Exception {
        MatrixProject m = createMatrixProject();
        m.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("FOO","value")
        ));
        m.getBuildersList().add(new Shell("sleep 3"));
        m.setAxes(new AxisList(new TextAxis("DoesntMatter", "aaa","bbb")));

        List<Future<MatrixBuild>> r = new ArrayList<Future<MatrixBuild>>();

        for (int i=0; i<3; i++)
            r.add(m.scheduleBuild2(0,new UserIdCause(),new ParametersAction(new StringParameterValue("FOO","value"+i))));

        for (Future<MatrixBuild> f : r)
            assertBuildStatusSuccess(f);
    }

    private int INITIALDELAY;
    private int RECURRENCEPERIOD;
    @Override protected void setUp() throws Exception {
        INITIALDELAY = NodeProvisioner.NodeProvisionerInvoker.INITIALDELAY;
        NodeProvisioner.NodeProvisionerInvoker.INITIALDELAY = 0;
        RECURRENCEPERIOD = NodeProvisioner.NodeProvisionerInvoker.RECURRENCEPERIOD;
        NodeProvisioner.NodeProvisionerInvoker.RECURRENCEPERIOD = 10;
        super.setUp();
    }
    @Override protected void tearDown() throws Exception {
        super.tearDown();
        NodeProvisioner.NodeProvisionerInvoker.INITIALDELAY = INITIALDELAY;
        NodeProvisioner.NodeProvisionerInvoker.RECURRENCEPERIOD = RECURRENCEPERIOD;
    }
    @Bug(7291)
    public void testFlyweightTasksWithoutMasterExecutors() throws Exception {
        DummyCloudImpl cloud = new DummyCloudImpl(this, 0);
        cloud.label = jenkins.getLabel("remote");
        jenkins.clouds.add(cloud);
        jenkins.setNumExecutors(0);
        jenkins.setNodes(Collections.<Node>emptyList());
        MatrixProject m = createMatrixProject();
        m.setAxes(new AxisList(new LabelAxis("label", Arrays.asList("remote"))));
        MatrixBuild build;
        try {
            build = m.scheduleBuild2(0).get(60, TimeUnit.SECONDS);
        } catch (TimeoutException x) {
            throw (AssertionError) new AssertionError(jenkins.getQueue().getApproximateItemsQuickly().toString()).initCause(x);
        }
        assertBuildStatusSuccess(build);
        assertEquals("", build.getBuiltOnStr());
        List<MatrixRun> runs = build.getRuns();
        assertEquals(1, runs.size());
        assertEquals("slave0", runs.get(0).getBuiltOnStr());
    }

    public void testTaskEquality() throws Exception {
        AtomicInteger cnt = new AtomicInteger();
        ScheduleResult result = jenkins.getQueue().schedule2(new TestTask(cnt), 0);
        assertTrue(result.isCreated());
        WaitingItem item = result.getCreateItem();
        assertFalse(jenkins.getQueue().schedule2(new TestTask(cnt), 0).isCreated());
        item.getFuture().get();
        waitUntilNoActivity();
        assertEquals(1, cnt.get());
    }
    private static final class TestTask extends AbstractQueueTask {
        private final AtomicInteger cnt;
        TestTask(AtomicInteger cnt) {
            this.cnt = cnt;
        }
        @Override public boolean equals(Object o) {
            return o instanceof TestTask && cnt == ((TestTask) o).cnt;
        }
        @Override public int hashCode() {
            return cnt.hashCode();
        }
        @Override public boolean isBuildBlocked() {return false;}
        @Override public String getWhyBlocked() {return null;}
        @Override public String getName() {return "test";}
        @Override public String getFullDisplayName() {return "Test";}
        @Override public void checkAbortPermission() {}
        @Override public boolean hasAbortPermission() {return true;}
        @Override public String getUrl() {return "test/";}
        @Override public String getDisplayName() {return "Test";}
        @Override public Label getAssignedLabel() {return null;}
        @Override public Node getLastBuiltOn() {return null;}
        @Override public long getEstimatedDuration() {return -1;}
        @Override public ResourceList getResourceList() {return new ResourceList();}
        @Override public Executable createExecutable() throws IOException {
            return new Executable() {
                @Override public SubTask getParent() {return TestTask.this;}
                @Override public long getEstimatedDuration() {return -1;}
                @Override public void run() {
                    cnt.incrementAndGet();
                }
            };
        }
    }

    public void testWaitForStart() throws Exception {
        final OneShotEvent ev = new OneShotEvent();
        FreeStyleProject p = createFreeStyleProject();
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
        FreeStyleBuild b2 = assertBuildStatusSuccess(v);
        assertSame(b, b2);
    }

    @Inject
    QueueItemAuthenticatorConfiguration qac;

    /**
     * Make sure that the running build actually carries an credential.
     */
    public void testAccessControl() throws Exception {
        configureUserRealm();
        FreeStyleProject p = createFreeStyleProject();
        qac.getAuthenticators().add(new MockQueueItemAuthenticator(Collections.singletonMap(p.getFullName(), alice)));
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                assertEquals(alice,Jenkins.getAuthentication());
                return true;
            }
        });
        assertBuildStatusSuccess(p.scheduleBuild2(0));
    }

    private static Authentication alice = new UsernamePasswordAuthenticationToken("alice","alice",new GrantedAuthority[0]);


    /**
     * Make sure that the slave assignment honors the permissions.
     *
     * We do this test by letting a build run twice to determine its natural home,
     * and then introduce a security restriction to prohibit that.
     */
    public void testPermissionSensitiveSlaveAllocations() throws Exception {
        jenkins.setNumExecutors(0); // restrict builds to those slaves
        DumbSlave s1 = createSlave();
        DumbSlave s2 = createSlave();

        configureUserRealm();
        FreeStyleProject p = createFreeStyleProject();
        qac.getAuthenticators().add(new MockQueueItemAuthenticator(Collections.singletonMap(p.getFullName(), alice)));
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                assertEquals(alice,Jenkins.getAuthentication());
                return true;
            }
        });

        final FreeStyleBuild b1 = assertBuildStatusSuccess(p.scheduleBuild2(0));
        final FreeStyleBuild b2 = assertBuildStatusSuccess(p.scheduleBuild2(0));

        // scheduling algorithm would prefer running the same job on the same node
        // kutzi: 'prefer' != 'enforce', therefore disabled this assertion: assertSame(b1.getBuiltOn(),b2.getBuiltOn());

        // ACL that allow anyone to do anything except Alice can't build.
        final SparseACL aliceCantBuild = new SparseACL(null);
        aliceCantBuild.add(new PrincipalSid(alice), Computer.BUILD, false);
        aliceCantBuild.add(new PrincipalSid("anonymous"), Jenkins.ADMINISTER, true);

        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy() {
            @Override
            public ACL getACL(Node node) {
                if (node==b1.getBuiltOn())
                    return aliceCantBuild;
                return super.getACL(node);
            }
        };
        auth.add(Jenkins.ADMINISTER,"anonymous");
        jenkins.setAuthorizationStrategy(auth);

        // now that we prohibit alice to do a build on the same node, the build should run elsewhere
        for (int i=0; i<3; i++) {
            FreeStyleBuild b3 = assertBuildStatusSuccess(p.scheduleBuild2(0));
            assertNotSame(b3.getBuiltOnStr(), b1.getBuiltOnStr());
        }
    }

    public void testPendingsConsistenceAfterErrorDuringMaintain() throws IOException, ExecutionException, InterruptedException{
        FreeStyleProject project1 = createFreeStyleProject();
        FreeStyleProject project2 = createFreeStyleProject();
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

            @Override
            public String getDisplayName() {
                return "simulate-error";
            }
        };
        FreeStyleProject projectError = (FreeStyleProject) jenkins.createProject(descriptor, "throw-error");
        project1.setAssignedLabel(jenkins.getSelfLabel());
        project2.setAssignedLabel(jenkins.getSelfLabel());
        project1.getBuildersList().add(new Shell("sleep 2"));
        project1.scheduleBuild2(0);
        QueueTaskFuture<FreeStyleBuild> v = project2.scheduleBuild2(0);
        projectError.scheduleBuild2(0);
        Executor e = jenkins.toComputer().getExecutors().get(0);
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
        e.doYank(); //restart executor
        while(!e.isIdle()){ //executor should take project2 from queue
            Thread.sleep(1000); 
        }
        //project2 should not be in pendings
        List<Queue.BuildableItem> items = Queue.getInstance().getPendingItems();
        for(Queue.BuildableItem item : items){
            assertFalse("Project " + project2.getDisplayName() + " stuck in pendings",item.task.getName().equals(project2.getName())); 
        }
    }
    
    public void testCancelInQueue() throws Exception
    {
        // parepare an offline slave.
        DumbSlave slave = createOnlineSlave();
        assertFalse(slave.toComputer().isOffline());
        slave.toComputer().disconnect(null).get();
        assertTrue(slave.toComputer().isOffline());
        
        FreeStyleProject p = createFreeStyleProject();
        p.setAssignedNode(slave);
        
        QueueTaskFuture<FreeStyleBuild> f = p.scheduleBuild2(0);
        try {
            f.get(3, TimeUnit.SECONDS);
            fail("Should time out (as the slave is offline).");
        } catch (TimeoutException e) {
        }
        
        Queue.Item item = Queue.getInstance().getItem(p);
        assertNotNull(item);
        Queue.getInstance().doCancelItem(item.id);
        assertNull(Queue.getInstance().getItem(p));
        
        try {
            f.get(10, TimeUnit.SECONDS);
            fail("Should not get (as it is cancelled).");
        } catch (CancellationException e) {
        }
    }

    public void testQueueApiOutputShouldBeFilteredByUserPermission() throws Exception {

        jenkins.setSecurityRealm(createDummySecurityRealm());
        ProjectMatrixAuthorizationStrategy str = new ProjectMatrixAuthorizationStrategy();
        str.add(Jenkins.READ, "bob");
        str.add(Jenkins.READ, "alice");
        str.add(Jenkins.READ, "james");
        jenkins.setAuthorizationStrategy(str);

        FreeStyleProject project = createFreeStyleProject("project");

        Map<Permission, Set<String>> permissions = new HashMap<Permission, Set<String>>();
        permissions.put(Item.READ, Collections.singleton("bob"));
        permissions.put(Item.DISCOVER, Collections.singleton("james"));
        AuthorizationMatrixProperty prop1 = new AuthorizationMatrixProperty(permissions);
        project.addProperty(prop1);
        project.getBuildersList().add(new SleepBuilder(10));
        project.scheduleBuild2(0);

        WebClient webClient = new WebClient();
        webClient.login("bob", "bob");
        XmlPage p = webClient.goToXml("/queue/api/xml");

        //bob has permission on the project and will be able to see it in the queue together with information such as the URL and the name.
        for (DomNode element: p.getFirstChild().getFirstChild().getChildNodes()){
            if(element.getNodeName().equals("task")){
                assertEquals(((DomElement)element).getElementsByTagName("name").size(),1);
                assertEquals(((DomElement) element).getElementsByTagName("name").item(0).getFirstChild().toString(), "project");
                assertEquals(((DomElement)element).getElementsByTagName("url").size(),1);
            }
        }
        WebClient webClient2 = new WebClient();
        webClient2.login("alice");
        XmlPage p2 = webClient2.goToXml("/queue/api/xml");
        //alice does not have permission on the project and will not see it in the queue.
        assertEquals("<queue></queue>", p2.getContent());

        WebClient webClient3 = new WebClient();
        webClient3.login("james");
        XmlPage p3 = webClient3.goToXml("/queue/api/xml");
        //james has DISCOVER permission on the project and will only be able to see the task name.
        assertEquals("<queue><discoverableItem><task><name>project</name></task></discoverableItem></queue>",
                p3.getContent());

    }

    //we force the project not to be executed so that it stays in the queue
    @TestExtension("testQueueApiOutputShouldBeFilteredByUserPermission")
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
}
