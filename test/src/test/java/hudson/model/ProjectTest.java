/*
 * The MIT License
 *
 * Copyright 2013 Red Hat, Inc.
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
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import hudson.model.queue.QueueTaskFuture;
import hudson.security.AccessDeniedException2;
import org.acegisecurity.context.SecurityContextHolder;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.GlobalMatrixAuthorizationStrategy;

import java.net.URL;
import java.util.Collections;

import org.jvnet.hudson.reactor.ReactorException;
import org.jvnet.hudson.test.FakeChangeLogSCM;
import hudson.scm.SCMRevisionState;
import hudson.scm.PollingResult;
import hudson.Launcher;
import hudson.Launcher.RemoteLauncher;
import hudson.Util;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.model.queue.SubTaskContributor;
import hudson.model.queue.AbstractSubTask;
import hudson.model.Queue.Executable;
import hudson.model.Queue.Task;
import hudson.model.queue.SubTask;
import hudson.model.AbstractProject.BecauseOfUpstreamBuildInProgress;
import hudson.model.AbstractProject.BecauseOfDownstreamBuildInProgress;
import jenkins.model.WorkspaceWriter;
import jenkins.model.Jenkins;
import antlr.ANTLRException;
import hudson.triggers.SCMTrigger;
import hudson.model.Cause.LegacyCodeCause;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import java.io.Serializable;
import jenkins.scm.DefaultSCMCheckoutStrategyImpl;
import jenkins.scm.SCMCheckoutStrategy;
import java.io.File;
import hudson.FilePath;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.EnvVars;
import hudson.model.labels.LabelAtom;
import hudson.scm.SCMDescriptor;
import hudson.slaves.Cloud;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeProvisioner;
import hudson.tasks.Shell;
import org.jvnet.hudson.test.TestExtension;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static org.junit.Assert.*;
import hudson.tasks.Fingerprinter;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BuildTrigger;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import jenkins.model.BlockedBecauseOfBuildInProgress;

import org.junit.Ignore;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.TestBuilder;

/**
 *
 * @author Lucie Votypkova
 */
public class ProjectTest {
    
    @Rule public JenkinsRule j = new JenkinsRule();
    public static boolean createAction = false;
    public static boolean getFilePath = false;
    public static boolean createSubTask = false;
    
    @Test
    public void testSave() throws IOException, InterruptedException, ReactorException {
        FreeStyleProject p = j.createFreeStyleProject("project");
        p.disabled = true;
        p.nextBuildNumber = 5;
        p.description = "description";
        p.save();
        j.jenkins.reload();
        assertEquals("All persistent data should be saved.", "description", p.description);
        assertEquals("All persistent data should be saved.", 5, p.nextBuildNumber);
        assertEquals("All persistent data should be saved", true, p.disabled);
    }
    
    @Test
    public void testOnCreateFromScratch() throws IOException, Exception{
        FreeStyleProject p = j.createFreeStyleProject("project");
        j.buildAndAssertSuccess(p);
        p.removeRun(p.getLastBuild());
        createAction = true;
        p.onCreatedFromScratch();
        assertNotNull("Project should have last build.", p.getLastBuild());
        assertNotNull("Project should have transient action TransientAction.", p.getAction(TransientAction.class));
        createAction = false;
    }
    
    @Test
    public void testOnLoad() throws IOException, Exception{
        FreeStyleProject p = j.createFreeStyleProject("project");
        j.buildAndAssertSuccess(p);
        p.removeRun(p.getLastBuild());
        createAction = true;
        p.onLoad(j.jenkins, "project");
        assertTrue("Project should have a build.", p.getLastBuild()!=null);
        assertTrue("Project should have a scm.", p.getScm()!=null);
        assertTrue("Project should have Transient Action TransientAction.", p.getAction(TransientAction.class)!=null);
        createAction = false;
    }
    
    @Test
    public void testGetEnvironment() throws Exception{
        FreeStyleProject p = j.createFreeStyleProject("project");
        Slave slave = j.createOnlineSlave();
        EnvironmentVariablesNodeProperty.Entry entry = new EnvironmentVariablesNodeProperty.Entry("jdk","some_java");
        slave.getNodeProperties().add(new EnvironmentVariablesNodeProperty(entry));
        EnvVars var = p.getEnvironment(slave, TaskListener.NULL);
        assertEquals("Environment should have set jdk.", "some_java", var.get("jdk"));
    }
    
    @Test
    public void testPerformDelete() throws IOException, Exception{
        FreeStyleProject p = j.createFreeStyleProject("project");
        p.performDelete();
        assertFalse("Project should be deleted from disk.", p.getConfigFile().exists());
        assertTrue("Project should be disabled when deleting start.", p.isDisabled());
    }
    
    @Test
    public void testGetAssignedLabel() throws Exception{
        FreeStyleProject p = j.createFreeStyleProject("project");
        p.setAssignedLabel(j.jenkins.getSelfLabel());
        Slave slave = j.createOnlineSlave();
        assertEquals("Project should have Jenkins's self label.", j.jenkins.getSelfLabel(), p.getAssignedLabel());
        p.setAssignedLabel(null);
        assertNull("Project should not have any label.", p.getAssignedLabel());
        p.setAssignedLabel(slave.getSelfLabel());
        assertEquals("Project should have self label of slave", slave.getSelfLabel(), p.getAssignedLabel());
    }
    
    @Test
    public void testGetAssignedLabelString() throws Exception{
        FreeStyleProject p = j.createFreeStyleProject("project");
        Slave slave = j.createOnlineSlave();
        assertNull("Project should not have any label.", p.getAssignedLabelString());
        p.setAssignedLabel(j.jenkins.getSelfLabel());
        assertNull("Project should return null, because assigned label is Jenkins.", p.getAssignedLabelString());
        p.setAssignedLabel(slave.getSelfLabel());
        assertEquals("Project should return name of slave.", slave.getSelfLabel().name, p.getAssignedLabelString());
    }

    
    @Test
    public void testGetSomeWorkspace() throws Exception{
        FreeStyleProject p = j.createFreeStyleProject("project");
        assertNull("Project which has never run should not have any workspace.", p.getSomeWorkspace());
        getFilePath = true;
        assertNotNull("Project should have any workspace because WorkspaceBrowser find some.", p.getSomeWorkspace());
        getFilePath = false;
        p.getBuildersList().add(new Shell("echo ahoj > some.log"));
        j.buildAndAssertSuccess(p);
        assertNotNull("Project should has any workspace.", p.getSomeWorkspace());
    }
    
    @Test
    public void testGetSomeBuildWithWorkspace() throws Exception{
        FreeStyleProject p = j.createFreeStyleProject("project");
        p.getBuildersList().add(new Shell("echo ahoj > some.log"));
        assertNull("Project which has never run should not have any build with workspace.", p.getSomeBuildWithWorkspace());
        j.buildAndAssertSuccess(p);
        assertEquals("Last build should have workspace.", p.getLastBuild(), p.getSomeBuildWithWorkspace());
        p.getLastBuild().delete();
        assertNull("Project should not have build with some workspace.", p.getSomeBuildWithWorkspace());
    }

    @Issue("JENKINS-10450")
    @Test public void workspaceBrowsing() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("project");
        p.getBuildersList().add(new Shell("echo ahoj > some.log"));
        j.buildAndAssertSuccess(p);
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.goTo("job/project/ws/some.log", "text/plain");
        wc.assertFails("job/project/ws/other.log", 404);
        p.doDoWipeOutWorkspace();
        wc.assertFails("job/project/ws/some.log", 404);
    }
    
    @Test
    public void testGetQuietPeriod() throws IOException{
        FreeStyleProject p = j.createFreeStyleProject("project");
        assertEquals("Quiet period should be default.", j.jenkins.getQuietPeriod(), p.getQuietPeriod());
        j.jenkins.setQuietPeriod(0);
        assertEquals("Quiet period is not set so it should be the same as global quiet period.", 0, p.getQuietPeriod());
        p.setQuietPeriod(10);
        assertEquals("Quiet period was set.",p.getQuietPeriod(),10);
    }
    
    @Test
    public void testGetScmCheckoutStrategy() throws IOException{
        FreeStyleProject p = j.createFreeStyleProject("project");
        p.setScmCheckoutStrategy(null);
        assertTrue("Project should return default checkout strategy if scm checkout strategy is not set.", p.getScmCheckoutStrategy() instanceof DefaultSCMCheckoutStrategyImpl);
        SCMCheckoutStrategy strategy = new SCMCheckoutStrategyImpl();
        p.setScmCheckoutStrategy(strategy);
        assertEquals("Project should return its scm checkout strategy if this strategy is not null", strategy, p.getScmCheckoutStrategy());
    }
    
    @Test
    public void testGetScmCheckoutRetryCount() throws Exception{
        FreeStyleProject p = j.createFreeStyleProject("project");
        assertEquals("Scm retry count should be default.", j.jenkins.getScmCheckoutRetryCount(), p.getScmCheckoutRetryCount());
        j.jenkins.setScmCheckoutRetryCount(6);
        assertEquals("Scm retry count should be the same as global scm retry count.", 6, p.getScmCheckoutRetryCount());
        HtmlForm form = j.createWebClient().goTo(p.getUrl() + "/configure").getFormByName("config");
        ((HtmlElement)form.getByXPath("//div[@class='advancedLink']//button").get(0)).click();
        form.getInputByName("hasCustomScmCheckoutRetryCount").click();
        form.getInputByName("scmCheckoutRetryCount").setValueAttribute("7");
        j.submit(form);
        assertEquals("Scm retry count was set.", 7, p.getScmCheckoutRetryCount());
    }
    
    @Test
    public void isBuildable() throws IOException{
        FreeStyleProject p = j.createFreeStyleProject("project");
        assertTrue("Project should be buildable.", p.isBuildable());
        p.disable();
        assertFalse("Project should not be buildable if it is disabled.", p.isBuildable());
        p.enable();
        AbstractProject p2 = (AbstractProject) j.jenkins.copy(j.jenkins.getItem("project"), "project2");
        assertFalse("Project should not be buildable until is saved.", p2.isBuildable());
        p2.save();
        assertTrue("Project should be buildable after save.", p2.isBuildable());
    }
    
    @Test
    public void testMakeDisabled() throws IOException{
        FreeStyleProject p = j.createFreeStyleProject("project");
        p.makeDisabled(false);
        assertFalse("Project should be enabled.", p.isDisabled());
        p.makeDisabled(true);
        assertTrue("Project should be disabled.", p.isDisabled());
        p.makeDisabled(false);
        p.setAssignedLabel(j.jenkins.getLabel("nonExist"));
        p.scheduleBuild2(0);
        p.makeDisabled(true);
        assertNull("Project should be canceled.", Queue.getInstance().getItem(p));
    }
    
    @Test
    public void testAddProperty() throws IOException{
        FreeStyleProject p = j.createFreeStyleProject("project");
        JobProperty prop = new JobPropertyImp();
        createAction = true;
        p.addProperty(prop);
        assertNotNull("Project does not contain added property.", p.getProperty(prop.getClass()));
        assertNotNull("Project did not update transient actions.", p.getAction(TransientAction.class));
    }
    
    @Test
    public void testScheduleBuild2() throws IOException, InterruptedException{
        FreeStyleProject p = j.createFreeStyleProject("project");
        p.setAssignedLabel(j.jenkins.getLabel("nonExist"));
        p.scheduleBuild(0, new LegacyCodeCause(), new Action[0]);
        assertNotNull("Project should be in queue.", Queue.getInstance().getItem(p));
        p.setAssignedLabel(null);
        int count = 0;
        while(count<5 && p.getLastBuild()==null){
            Thread.sleep(1000); //give some time to start build
            count++;
        }
        assertNotNull("Build should be done or in progress.", p.getLastBuild());
    }

    
    @Test
    public void testSchedulePolling() throws IOException, ANTLRException{
        FreeStyleProject p = j.createFreeStyleProject("project");
        assertFalse("Project should not schedule polling because no scm trigger is set.",p.schedulePolling());
        SCMTrigger trigger = new SCMTrigger("0 0 * * *");
        p.addTrigger(trigger);
        trigger.start(p, true);
        assertTrue("Project should schedule polling.", p.schedulePolling());
        p.disable();
        assertFalse("Project should not schedule polling because project is disabled.", p.schedulePolling());
    }
    
    @Test
    public void testSaveAfterSet() throws Exception, ReactorException {
        FreeStyleProject p = j.createFreeStyleProject("project");
        p.setScm(new NullSCM());
        p.setScmCheckoutStrategy(new SCMCheckoutStrategyImpl());
        p.setQuietPeriod(15);
        p.setBlockBuildWhenDownstreamBuilding(true);
        p.setBlockBuildWhenUpstreamBuilding(true);
        j.jenkins.getJDKs().add(new JDK("jdk", "path"));
        j.jenkins.save();
        p.setJDK(j.jenkins.getJDK("jdk"));
        p.setCustomWorkspace("/some/path");
        j.jenkins.reload();
        assertNotNull("Project did not save scm.", p.getScm());
        assertTrue("Project did not save scm checkout strategy.", p.getScmCheckoutStrategy() instanceof SCMCheckoutStrategyImpl);
        assertEquals("Project did not save quiet period.", 15, p.getQuietPeriod());
        assertTrue("Project did not save block if downstream is building.", p.blockBuildWhenDownstreamBuilding());
        assertTrue("Project did not save block if upstream is buildidng.", p.blockBuildWhenUpstreamBuilding());
        assertNotNull("Project did not save jdk", p.getJDK());
        assertEquals("Project did not save custom workspace.", "/some/path", p.getCustomWorkspace());
    }
    
    @Test
    public void testGetActions() throws IOException{
        FreeStyleProject p = j.createFreeStyleProject("project");
        createAction = true;
        p.updateTransientActions();
        assertNotNull("Action should contain transient actions too.", p.getAction(TransientAction.class));
        createAction = false;
    }

// for debugging
//    static {
//        Logger.getLogger("").getHandlers()[0].setFormatter(new MilliSecLogFormatter());
//    }

    @Test
    public void testGetCauseOfBlockage() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("project");
        p.getBuildersList().add(new Shell("sleep 10"));
        QueueTaskFuture<FreeStyleBuild> b1 = waitForStart(p);
        assertInstanceOf("Build can not start because previous build has not finished: " + p.getCauseOfBlockage(), p.getCauseOfBlockage(), BlockedBecauseOfBuildInProgress.class);
        p.getLastBuild().getExecutor().interrupt();
        b1.get();   // wait for it to finish

        FreeStyleProject downstream = j.createFreeStyleProject("project-downstream");
        downstream.getBuildersList().add(new Shell("sleep 10"));
        p.getPublishersList().add(new BuildTrigger(Collections.singleton(downstream), Result.SUCCESS));
        Jenkins.getInstance().rebuildDependencyGraph();
        p.setBlockBuildWhenDownstreamBuilding(true);
        QueueTaskFuture<FreeStyleBuild> b2 = waitForStart(downstream);
        assertInstanceOf("Build can not start because build of downstream project has not finished.", p.getCauseOfBlockage(), BecauseOfDownstreamBuildInProgress.class);
        downstream.getLastBuild().getExecutor().interrupt();
        b2.get();

        downstream.setBlockBuildWhenUpstreamBuilding(true);
        waitForStart(p);
        assertInstanceOf("Build can not start because build of upstream project has not finished.", downstream.getCauseOfBlockage(), BecauseOfUpstreamBuildInProgress.class);
    }

    private static final Logger LOGGER = Logger.getLogger(ProjectTest.class.getName());

    private QueueTaskFuture<FreeStyleBuild> waitForStart(FreeStyleProject p) throws InterruptedException, ExecutionException {
        long start = System.nanoTime();
        LOGGER.info("Scheduling "+p);
        QueueTaskFuture<FreeStyleBuild> f = p.scheduleBuild2(0);
        f.waitForStart();
        LOGGER.info("Wait:"+ TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));
        return f;
    }

    private void assertInstanceOf(String msg, Object o, Class t) {
        if (t.isInstance(o))
            return;
        fail(msg + ": " + o);
    }
    
    @Test
    public void testGetSubTasks() throws IOException{
        FreeStyleProject p = j.createFreeStyleProject("project");
        p.addProperty(new JobPropertyImp());
        createSubTask = true;
        List<SubTask> subtasks = p.getSubTasks();
        boolean containsSubTaskImpl = false;
        boolean containsSubTaskImpl2 = false;
        for(SubTask sub: subtasks){
            if(sub instanceof SubTaskImpl)
                containsSubTaskImpl = true;
            if(sub instanceof SubTaskImpl2)
                containsSubTaskImpl2 = true;
        }
        createSubTask = false;
        assertTrue("Project should return subtasks provided by SubTaskContributor.", containsSubTaskImpl2);
        assertTrue("Project should return subtasks provided by JobProperty.", containsSubTaskImpl);
        
    }
    
    @Test
    public void testCreateExecutable() throws IOException{
        FreeStyleProject p = j.createFreeStyleProject("project");
        Build build = p.createExecutable();
        assertNotNull("Project should create executable.", build);
        assertEquals("CreatedExecutable should be the last build.", build, p.getLastBuild());
        assertEquals("Next build number should be increased.", 2, p.nextBuildNumber);
        p.disable();
        build = p.createExecutable();
        assertNull("Disabled project should not create executable.", build);
        assertEquals("Next build number should not be increased.", 2, p.nextBuildNumber);
        
    }
    
    @Test
    public void testCheckout() throws IOException, Exception{
        SCM scm = new NullSCM();
        FreeStyleProject p = j.createFreeStyleProject("project");
        Slave slave = j.createOnlineSlave();
        AbstractBuild build = p.createExecutable();
        FilePath ws = slave.getWorkspaceFor(p);
        assertNotNull(ws);
        FilePath path = slave.toComputer().getWorkspaceList().allocate(ws, build).path;
        build.setWorkspace(path);
        BuildListener listener = new StreamBuildListener(BuildListener.NULL.getLogger(), Charset.defaultCharset());
        assertTrue("Project with null smc should perform checkout without problems.", p.checkout(build, new RemoteLauncher(listener, slave.getChannel(), true), listener, new File(build.getRootDir(),"changelog.xml")));
        p.setScm(scm);
        assertTrue("Project should perform checkout without problems.",p.checkout(build, new RemoteLauncher(listener, slave.getChannel(), true), listener, new File(build.getRootDir(),"changelog.xml")));
    }

    @Ignore("randomly failed: Project should have polling result no change expected:<NONE> but was:<INCOMPARABLE>")
    @Test
    public void testPoll() throws Exception{
        FreeStyleProject p = j.createFreeStyleProject("project");
        SCM scm = new NullSCM();
        p.setScm(null);
        SCM alwaysChange = new AlwaysChangedSCM();
        assertEquals("Project with null scm should have have polling result no change.", PollingResult.Change.NONE, p.poll(TaskListener.NULL).change);
        p.setScm(scm);
        p.disable();
        assertEquals("Project which is disabled should have have polling result no change.", PollingResult.Change.NONE, p.poll(TaskListener.NULL).change);
        p.enable();
        assertEquals("Project which has no builds should have have polling result incomparable.", PollingResult.Change.INCOMPARABLE, p.poll(TaskListener.NULL).change);
        p.setAssignedLabel(j.jenkins.getLabel("nonExist"));
        p.scheduleBuild2(0);
        assertEquals("Project which build is building should have polling result result no change.", PollingResult.Change.NONE, p.poll(TaskListener.NULL).change);
        p.setAssignedLabel(null);
        while(p.getLastBuild()==null)
            Thread.sleep(100); //wait until build start
        assertEquals("Project should have polling result no change", PollingResult.Change.NONE, p.poll(TaskListener.NULL).change);
        p.setScm(alwaysChange);
        j.buildAndAssertSuccess(p);
        assertEquals("Project should have polling result significant", PollingResult.Change.SIGNIFICANT, p.poll(TaskListener.NULL).change);
    }
    
    @Test
    public void testHasParticipant() throws Exception{
        User user = User.get("John Smith", true, Collections.emptyMap());
        FreeStyleProject project = j.createFreeStyleProject("project");
        FreeStyleProject project2 = j.createFreeStyleProject("project2");
        FakeChangeLogSCM scm = new FakeChangeLogSCM();
        project2.setScm(scm);
        j.buildAndAssertSuccess(project2);
        assertFalse("Project should not have any participant.", project2.hasParticipant(user));
        scm.addChange().withAuthor(user.getId());
        project.setScm(scm); 
        j.buildAndAssertSuccess(project);
        assertTrue("Project should have participant.", project.hasParticipant(user));
    }
    
    @Test
    public void testGetRelationship() throws Exception{
        final FreeStyleProject upstream = j.createFreeStyleProject("upstream");
        FreeStyleProject downstream = j.createFreeStyleProject("downstream");
        j.buildAndAssertSuccess(upstream);
        j.buildAndAssertSuccess(upstream);
        j.buildAndAssertSuccess(downstream);
        assertTrue("Project upstream should not have any relationship with downstream", upstream.getRelationship(downstream).isEmpty());

        upstream.getPublishersList().add(new Fingerprinter("change.log", true));
        upstream.getBuildersList().add(new WorkspaceWriter("change.log", "hello"));
        upstream.getPublishersList().add(new ArtifactArchiver("change.log"));
        downstream.getPublishersList().add(new Fingerprinter("change.log", false));
        downstream.getBuildersList().add(new TestBuilder() {
            @Override public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                for (Run<?, ?>.Artifact a: upstream.getLastBuild().getArtifacts()) {
                    Util.copyFile(a.getFile(), new File(build.getWorkspace().child(a.getFileName()).getRemote()));
                }
                return true;
            }
        });

        j.buildAndAssertSuccess(upstream);
        j.buildAndAssertSuccess(downstream);
        j.buildAndAssertSuccess(upstream);
        j.buildAndAssertSuccess(downstream);
        upstream.getBuildersList().add(new WorkspaceWriter("change.log", "helloWorld"));
        j.buildAndAssertSuccess(upstream);
        j.buildAndAssertSuccess(downstream);

        Map<Integer,Fingerprint.RangeSet> relationship = upstream.getRelationship(downstream);
        assertFalse("Project upstream should have relationship with downstream", relationship.isEmpty());
        assertTrue("Relationship should contain upstream #3", relationship.keySet().contains(3));
        assertFalse("Relationship should not contain upstream #4 because previous fingerprinted file was not changed since #3", relationship.keySet().contains(4));
        assertEquals("downstream #2 should be the first build which depends on upstream #3", 2, relationship.get(3).min());
        assertEquals("downstream #3 should be the last build which depends on upstream #3", 3, relationship.get(3).max()-1);
        assertEquals("downstream #4 should depend only on upstream #5", 4, relationship.get(5).min());
        assertEquals("downstream #4 should depend only on upstream #5", 4, relationship.get(5).max()-1);
    }
    
    @Test
    public void testDoCancelQueue() throws Exception{
        FreeStyleProject project = j.createFreeStyleProject("project");
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();   
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        j.jenkins.setSecurityRealm(realm); 
        User user = realm.createAccount("John Smith", "password");
        SecurityContextHolder.getContext().setAuthentication(user.impersonate());
        try{
            project.doCancelQueue(null, null);
            fail("User should not have permission to build project");
        }
        catch(Exception e){
            if(!(e.getClass().isAssignableFrom(AccessDeniedException2.class))){
               fail("AccessDeniedException should be thrown.");
            }
        } 
    }
    
    @Test
    public void testDoDoDelete() throws Exception{
        FreeStyleProject project = j.createFreeStyleProject("project");
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();   
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        j.jenkins.setSecurityRealm(realm); 
        User user = realm.createAccount("John Smith", "password");
        SecurityContextHolder.getContext().setAuthentication(user.impersonate()); 
        try{
            project.doDoDelete(null, null);
            fail("User should not have permission to build project");
        }
        catch(Exception e){
            if(!(e.getClass().isAssignableFrom(AccessDeniedException2.class))){
               fail("AccessDeniedException should be thrown.");
            }
        } 
        auth.add(Jenkins.READ, user.getId());
        auth.add(Job.READ, user.getId());
        auth.add(Job.DELETE, user.getId());
        List<HtmlForm> forms = j.createWebClient().login(user.getId(), "password").goTo(project.getUrl() + "delete").getForms();
        for(HtmlForm form:forms){
            if("doDelete".equals(form.getAttribute("action"))){
                j.submit(form);
            }
        }
        assertNull("Project should be deleted form memory.", j.jenkins.getItem(project.getDisplayName()));
        assertFalse("Project should be deleted form disk.", project.getRootDir().exists());
    }
    
    @Test
    public void testDoDoWipeOutWorkspace() throws Exception{
        FreeStyleProject project = j.createFreeStyleProject("project");
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();   
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        j.jenkins.setSecurityRealm(realm); 
        User user = realm.createAccount("John Smith", "password");
        SecurityContextHolder.getContext().setAuthentication(user.impersonate()); 
        try{
            project.doDoWipeOutWorkspace();
            fail("User should not have permission to build project");
        }
        catch(Exception e){
            if(!(e.getClass().isAssignableFrom(AccessDeniedException2.class))){
               fail("AccessDeniedException should be thrown.");
            }
        } 
        auth.add(Job.READ, user.getId());
        auth.add(Job.BUILD, user.getId());
        auth.add(Job.WIPEOUT, user.getId());
        auth.add(Jenkins.READ, user.getId());
        Slave slave = j.createOnlineSlave();
        project.setAssignedLabel(slave.getSelfLabel());
        project.getBuildersList().add(new Shell("echo hello > change.log"));
        j.buildAndAssertSuccess(project);
        JenkinsRule.WebClient wc = j.createWebClient().login(user.getId(), "password");
        WebRequestSettings request = new WebRequestSettings(new URL(wc.getContextPath() + project.getUrl() + "doWipeOutWorkspace"), HttpMethod.POST);
        HtmlPage p = wc.getPage(request);
        Thread.sleep(500);
        assertFalse("Workspace should not exist.", project.getSomeWorkspace().exists());
    }
    
    @Test
    public void testDoDisable() throws Exception{
        FreeStyleProject project = j.createFreeStyleProject("project");
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();   
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        j.jenkins.setSecurityRealm(realm); 
        User user = realm.createAccount("John Smith", "password");
        SecurityContextHolder.getContext().setAuthentication(user.impersonate()); 
        try{
            project.doDisable();
            fail("User should not have permission to build project");
        }
        catch(Exception e){
            if(!(e.getClass().isAssignableFrom(AccessDeniedException2.class))){
               fail("AccessDeniedException should be thrown.");
            }
        } 
        auth.add(Job.READ, user.getId());
        auth.add(Job.CONFIGURE, user.getId());
        auth.add(Jenkins.READ, user.getId());
        List<HtmlForm> forms = j.createWebClient().login(user.getId(), "password").goTo(project.getUrl()).getForms();
        for(HtmlForm form:forms){
            if("disable".equals(form.getAttribute("action"))){
                j.submit(form);
            }
        }
       assertTrue("Project should be disabled.", project.isDisabled());
    }
    
    @Test
    public void testDoEnable() throws Exception{
        FreeStyleProject project = j.createFreeStyleProject("project");
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();   
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        j.jenkins.setSecurityRealm(realm);
        User user = realm.createAccount("John Smith", "password");
        SecurityContextHolder.getContext().setAuthentication(user.impersonate()); 
        project.disable();
        try{
            project.doEnable();
            fail("User should not have permission to build project");
        }
        catch(Exception e){
            if(!(e.getClass().isAssignableFrom(AccessDeniedException2.class))){
               fail("AccessDeniedException should be thrown.");
            }
        } 
        auth.add(Job.READ, user.getId());
        auth.add(Job.CONFIGURE, user.getId());
        auth.add(Jenkins.READ, user.getId());
        List<HtmlForm> forms = j.createWebClient().login(user.getId(), "password").goTo(project.getUrl()).getForms();
        for(HtmlForm form:forms){
            if("enable".equals(form.getAttribute("action"))){
                j.submit(form);
            }
        }
       assertFalse("Project should be enabled.", project.isDisabled());
    }
    
    /**
     * Job is un-restricted (no nabel), this is submitted to queue, which spawns an on demand slave
     * @throws Exception 
     */
    @Test
    public void testJobSubmittedShouldSpawnCloud() throws Exception {
        /**
         * Setup a project with an SCM. Jenkins should have no executors in itself. 
         */
        FreeStyleProject proj = j.createFreeStyleProject("JENKINS-21394-spawn");        
        RequiresWorkspaceSCM requiresWorkspaceScm = new RequiresWorkspaceSCM(true);
        proj.setScm(requiresWorkspaceScm);        
        j.jenkins.setNumExecutors(0);        
        /*
         * We have a cloud
         */
        DummyCloudImpl2 c2 = new DummyCloudImpl2(j, 0);
        c2.label = new LabelAtom("test-cloud-label");        
        j.jenkins.clouds.add(c2);
        
        SCMTrigger t = new SCMTrigger("@daily", true);
        t.start(proj, true);
        proj.addTrigger(t);
        t.new Runner().run();
        
        Thread.sleep(1000);
        //Assert that the job IS submitted to Queue.
        assertEquals(1, j.jenkins.getQueue().getItems().length);        
    }
    
    /**
     * Job is restricted, but label can not be provided by any cloud, only normal slaves. Then job will not submit, because no slave is available.
     * @throws Exception
     */
    @Test
    public void testUnrestrictedJobNoLabelByCloudNoQueue() throws Exception {
        assertTrue(j.jenkins.clouds.isEmpty());
        //Create slave. (Online)
        Slave s1 = j.createOnlineSlave();
        
        //Create a project, and bind the job to the created slave
        FreeStyleProject proj = j.createFreeStyleProject("JENKINS-21394-noqueue");
        proj.setAssignedLabel(s1.getSelfLabel());
                        
        //Add an SCM to the project. We require a workspace for the poll
        RequiresWorkspaceSCM requiresWorkspaceScm = new RequiresWorkspaceSCM(true);
        proj.setScm(requiresWorkspaceScm);
 
        j.buildAndAssertSuccess(proj);        

        //Now create another slave. And restrict the job to that slave. The slave is offline, leaving the job with no assignable nodes.
        //We tell our mock SCM to return that it has got changes. But since there are no slaves, we get the desired result. 
        Slave s2 = j.createSlave();
        proj.setAssignedLabel(s2.getSelfLabel());
        requiresWorkspaceScm.hasChange = true;
        
        //Poll (We now should have NO online slaves, this should now return NO_CHANGES.
        PollingResult pr = proj.poll(j.createTaskListener());
        assertFalse(pr.hasChanges());
        
        SCMTrigger t = new SCMTrigger("@daily", true);
        t.start(proj, true);
        proj.addTrigger(t);
        
        t.new Runner().run();
        
        /**
         * Assert that the log contains the correct message.
         */
        HtmlPage log = j.createWebClient().getPage(proj, "scmPollLog");
        String logastext = log.asText();
        assertTrue(logastext.contains("(" + AbstractProject.WorkspaceOfflineReason.all_suitable_nodes_are_offline.name() + ")"));
        
    }
    
    /**
     * Job is restricted. Label is on slave that can be started in cloud. Job is submitted to queue, which spawns an on demand slave.
     * @throws Exception 
     */
    @Test
    public void testRestrictedLabelOnSlaveYesQueue() throws Exception {        
        FreeStyleProject proj = j.createFreeStyleProject("JENKINS-21394-yesqueue");
        RequiresWorkspaceSCM requiresWorkspaceScm = new RequiresWorkspaceSCM(true);
        proj.setScm(requiresWorkspaceScm);        
        j.jenkins.setNumExecutors(0);
        
        /*
         * We have a cloud
         */
        DummyCloudImpl2 c2 = new DummyCloudImpl2(j, 0);
        c2.label = new LabelAtom("test-cloud-label");        
        j.jenkins.clouds.add(c2);
        proj.setAssignedLabel(c2.label);
        
        SCMTrigger t = new SCMTrigger("@daily", true);
        t.start(proj, true);
        proj.addTrigger(t);
        t.new Runner().run();
        
        Thread.sleep(1000);
        //The job should be in queue
        assertEquals(1, j.jenkins.getQueue().getItems().length);    
    }

    @Issue("JENKINS-22750")
    @Test
    public void testMasterJobPutInQueue() throws Exception {
        FreeStyleProject proj = j.createFreeStyleProject("JENKINS-21394-yes-master-queue");
        RequiresWorkspaceSCM requiresWorkspaceScm = new RequiresWorkspaceSCM(true);
        proj.setAssignedLabel(null);        
        proj.setScm(requiresWorkspaceScm);        
        j.jenkins.setNumExecutors(1);    
        proj.setScm(requiresWorkspaceScm);
        
        //First build is not important
        j.buildAndAssertSuccess(proj);

        SCMTrigger t = new SCMTrigger("@daily", true);
        t.start(proj, true);
        proj.addTrigger(t);
        t.new Runner().run();


        assertFalse(j.jenkins.getQueue().isEmpty());
    }

    public static class TransientAction extends InvisibleAction{
        
    }
    
    @TestExtension
    public static class TransientActionFactoryImpl extends TransientProjectActionFactory{

        @Override
        public Collection<? extends Action> createFor(AbstractProject target) {
            List<Action> actions = new ArrayList<Action>();
            if(createAction)
                actions.add(new TransientAction());
            return actions;
        }
        
    }
    
    @TestExtension 
    public static class RequiresWorkspaceSCM extends NullSCM {
        
        public boolean hasChange = false;
        
        public RequiresWorkspaceSCM() { }
         
        public RequiresWorkspaceSCM(boolean hasChange) {
            this.hasChange = hasChange;
        }
        
        @Override
        public boolean pollChanges(AbstractProject<?, ?> project, Launcher launcher, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
            return hasChange;
        }
                       
        @Override
        public boolean requiresWorkspaceForPolling(){
            return true;
        }
        @Override public SCMDescriptor<?> getDescriptor() {
            return new SCMDescriptor<SCM>(null) {
                @Override public String getDisplayName() {
                    return "";
                }
            };
        }
        
        @Override
        protected PollingResult compareRemoteRevisionWith(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener, SCMRevisionState baseline) throws IOException, InterruptedException {            
            if(!hasChange) {
                return PollingResult.NO_CHANGES;
            }
            return PollingResult.SIGNIFICANT;
        }
    }
    
    @TestExtension
    public static class AlwaysChangedSCM extends NullSCM {

        @Override
        public boolean pollChanges(AbstractProject<?, ?> project, Launcher launcher, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
            return true;
        }
        
        @Override
        public boolean requiresWorkspaceForPolling(){
            return false;
        }

        @Override
        protected PollingResult compareRemoteRevisionWith(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener, SCMRevisionState baseline) throws IOException, InterruptedException {
            return PollingResult.SIGNIFICANT;
        }
        
    }
    
    @TestExtension
    public static class WorkspaceBrowserImpl extends WorkspaceBrowser{

        @Override
        public FilePath getWorkspace(Job job) {
            if(getFilePath)
                return new FilePath(new File("some_file_path"));
            return null;
        }
        
    }
    

    public static class SCMCheckoutStrategyImpl extends DefaultSCMCheckoutStrategyImpl implements Serializable{
        
        public SCMCheckoutStrategyImpl(){
            
        }

    }
    
    public static class JobPropertyImp extends JobProperty{

        @Override
        public Collection getSubTasks() {
            ArrayList<SubTask> list = new ArrayList<SubTask>();
            list.add(new SubTaskImpl());
            return list;
        }
        
        
    }
    
    @TestExtension
    public static class SubTaskContributorImpl extends SubTaskContributor{

        @Override
        public Collection<? extends SubTask> forProject(AbstractProject<?, ?> p) {
            ArrayList<SubTask> list = new ArrayList<SubTask>();
            if(createSubTask){
                list.add(new SubTaskImpl2());
            }
            return list;
        }
    }
    
    public static class SubTaskImpl2 extends SubTaskImpl{
        
    }
    
    public static class SubTaskImpl extends AbstractSubTask{
        
        public String projectName;

        @Override
        public Executable createExecutable() throws IOException {
            return null;
        }

        @Override
        public Task getOwnerTask() {
            return (Task) Jenkins.getInstance().getItem(projectName);
        }

        @Override
        public String getDisplayName() {
            return "some task";
        }

        
    }
    
    public class ActionImpl extends InvisibleAction{
        
    }
    
    @TestExtension
    public static class DummyCloudImpl2 extends Cloud {
        private final transient JenkinsRule caller;

        /**
         * Configurable delay between the {@link Cloud#provision(Label,int)} and the actual launch of a slave,
         * to emulate a real cloud that takes some time for provisioning a new system.
         *
         * <p>
         * Number of milliseconds.
         */
        private final int delay;

        // stats counter to perform assertions later
        public int numProvisioned;

        /**
         * Only reacts to provisioning for this label.
         */
        public Label label;
        
        public DummyCloudImpl2() { 
            super("test");
            this.delay = 0;
            this.caller = null;
        }

        public DummyCloudImpl2(JenkinsRule caller, int delay) {
            super("test");
            this.caller = caller;
            this.delay = delay;
        }

        @Override
        public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
            List<NodeProvisioner.PlannedNode> r = new ArrayList<NodeProvisioner.PlannedNode>();

            //Always provision...even if there is no workload.
            while(excessWorkload >= 0) {
                System.out.println("Provisioning");
                numProvisioned++;
                Future<Node> f = Computer.threadPoolForRemoting.submit(new ProjectTest.DummyCloudImpl2.Launcher(delay));
                r.add(new NodeProvisioner.PlannedNode(name+" #"+numProvisioned,f,1));
                excessWorkload-=1;
            }
            return r;
        }

        @Override
        public boolean canProvision(Label label) {
            //This cloud can ALWAYS provision 
           return true;
            /* return label==this.label; */
        }

        private final class Launcher implements Callable<Node> {
            private final long time;
            /**
             * This is so that we can find out the status of Callable from the debugger.
             */
            private volatile Computer computer;

            private Launcher(long time) {
                this.time = time;
            }

            @Override
            public Node call() throws Exception {
                // simulate the delay in provisioning a new slave,
                // since it's normally some async operation.
                Thread.sleep(time);

                System.out.println("launching slave");
                DumbSlave slave = caller.createSlave(label);
                computer = slave.toComputer();
                computer.connect(false).get();
                synchronized (ProjectTest.DummyCloudImpl2.this) {
                    System.out.println(computer.getName()+" launch"+(computer.isOnline()?"ed successfully":" failed"));
                    System.out.println(computer.getLog());
                }
                return slave;
            }
        }

        @Override
        public Descriptor<Cloud> getDescriptor() {
            throw new UnsupportedOperationException();
        }
    }
}
