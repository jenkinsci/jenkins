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

import hudson.security.AccessDeniedException2;
import org.acegisecurity.context.SecurityContextHolder;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import java.util.Collections;
import org.jvnet.hudson.test.FakeChangeLogSCM;
import hudson.scm.SCMRevisionState;
import hudson.scm.PollingResult;
import hudson.Launcher;
import hudson.Launcher.RemoteLauncher;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.model.queue.SubTaskContributor;
import hudson.model.queue.AbstractSubTask;
import hudson.model.Queue.Executable;
import hudson.model.Queue.Task;
import hudson.model.queue.SubTask;
import hudson.model.AbstractProject.BecauseOfUpstreamBuildInProgress;
import hudson.model.AbstractProject.BecauseOfDownstreamBuildInProgress;
import jenkins.model.Jenkins;
import java.util.HashSet;
import java.util.Set;
import hudson.model.AbstractProject.BecauseOfBuildInProgress;
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
import hudson.tasks.Shell;
import org.jvnet.hudson.test.TestExtension;
import java.util.List;
import java.util.ArrayList;
import hudson.util.HudsonIsLoading;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static org.junit.Assert.*;
import hudson.tasks.Fingerprinter;
import hudson.tasks.ArtifactArchiver;
import java.util.Map;
import hudson.Functions;
import org.junit.Ignore;

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
    public void testSave() throws IOException, InterruptedException {
        FreeStyleProject p = j.createFreeStyleProject("project");
        p.disabled = true;
        p.nextBuildNumber = 5;
        p.description = "description";
        p.save();
        j.jenkins.doReload();
        //wait until all configuration are reloaded
        reload(); 
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
    public void testSaveAfterSet() throws Exception{
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
        reload();
        assertNotNull("Project did not save scm.", p.getScm());
        assertTrue("Project did not save scm checkout strategy.", p.getScmCheckoutStrategy() instanceof SCMCheckoutStrategyImpl);
        assertEquals("Project did not save quiet period.", 15, p.getQuietPeriod());
        assertTrue("Project did not save block if downstream is building.", p.blockBuildWhenDownstreamBuilding());
        assertTrue("Project did not save block if upstream is buildidng.", p.blockBuildWhenUpstreamBuilding());
        assertNotNull("Project did not save jdk", p.getJDK());
        assertEquals("Project did not save custom workspace.", "/some/path", p.getCustomWorkspace());
    }
    
    private void reload() throws IOException, InterruptedException{
        j.jenkins.doReload();
        //wait until all configuration are reloaded
        if(j.jenkins.servletContext.getAttribute("app") instanceof HudsonIsLoading){
            Thread.sleep(500);
        } 
    }
    
    @Test
    public void testGetActions() throws IOException{
        FreeStyleProject p = j.createFreeStyleProject("project");
        createAction = true;
        p.updateTransientActions();
        assertNotNull("Action should contain transient actions too.", p.getAction(TransientAction.class));
        createAction = false;
    }
    
    @Test
    public void testGetCauseOfBlockage() throws IOException, InterruptedException{
        FreeStyleProject p = j.createFreeStyleProject("project");
        p.getBuildersList().add(new Shell("sleep 10"));
        p.scheduleBuild2(0);
        Thread.sleep(1000);//wait until it starts
        assertTrue("Build can not start because previous build has not finished.", p.getCauseOfBlockage() instanceof BecauseOfBuildInProgress);
        p.getLastBuild().getExecutor().interrupt();
        FreeStyleProject downstream = j.createFreeStyleProject("project-downstream");
        downstream.getBuildersList().add(new Shell("sleep 10"));
        Set<AbstractProject> upstream = new HashSet<AbstractProject>(Items.fromNameList(p.getParent(),"project",AbstractProject.class));
        downstream.convertUpstreamBuildTrigger(upstream);
        Jenkins.getInstance().rebuildDependencyGraph();
        p.setBlockBuildWhenDownstreamBuilding(true);
        downstream.scheduleBuild2(0);
        Thread.sleep(1000);//wait until it starts
        assertTrue("Build can not start because build of downstream project has not finished.", p.getCauseOfBlockage() instanceof BecauseOfDownstreamBuildInProgress);
        downstream.getLastBuild().getExecutor().interrupt();
        downstream.setBlockBuildWhenUpstreamBuilding(true);
        p.scheduleBuild2(0);
        Thread.sleep(1000);//wait until it starts
        assertTrue("Build can not start because build of upstream project has not finished.", downstream.getCauseOfBlockage() instanceof BecauseOfUpstreamBuildInProgress);
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
        FilePath path = slave.toComputer().getWorkspaceList().allocate(slave.getWorkspaceFor(p), build).path;
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
        FreeStyleProject project = j.createFreeStyleProject("project");
        FreeStyleProject project2 = j.createFreeStyleProject("project2");
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project2);
        assertTrue("Project " + project.getDisplayName()  + " should not have any relationship with " + project2.getDisplayName(), project.getRelationship(project2).isEmpty());       
        project.getPublishersList().add(new Fingerprinter("change.log", true));
        project.getBuildersList().add(new Shell("echo hello > change.log"));
        project.getPublishersList().add(new ArtifactArchiver("change.log","",true));
        project2.getPublishersList().add(new Fingerprinter("change.log", false));
        project2.getBuildersList().add(new Shell("cp " + project.getRootDir().getAbsolutePath() + "/builds/lastSuccessfulBuild/archive/change.log ."));
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project2);
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project2);
        project.getBuildersList().add(new Shell("echo helloWorld > change.log"));
        j.buildAndAssertSuccess(project);
        j.buildAndAssertSuccess(project2);
        Map<Integer,Fingerprint.RangeSet> ralationship = project.getRelationship(project2);
        assertFalse("Project " + project.getDisplayName() + " should have relationship with " + project2.getDisplayName(), ralationship.isEmpty());      
        assertTrue("Relationship should contains build 3 of project " + project.getDisplayName(), ralationship.keySet().contains(3));
        assertFalse("Relationship should not contains build 4 of project " + project.getDisplayName() + " because previous fingerprinted file was not change since build 3", ralationship.keySet().contains(4));
        assertEquals("Build 2 of project " + project2.getDisplayName() + " should be the first build which depends on build 3 of project " + project.getDisplayName(), 2, ralationship.get(3).min());
        assertEquals("Build 3 of project " + project2.getDisplayName() + " should be the last build which depends on build 3 of project " + project.getDisplayName(), 3, ralationship.get(3).max()-1);
        assertEquals("Build 4 of project " + project2.getDisplayName() + " should depend only on build 5 of project " + project.getDisplayName(), 4, ralationship.get(5).min());
        assertEquals("Build 4 of project " + project2.getDisplayName() + " should depend only on build 5 of project " + project.getDisplayName(), 4, ralationship.get(5).max()-1);
    }
    
    @Test
    public void testDoCancelQueue() throws Exception{
        User user = User.get("John Smith", true, Collections.emptyMap());
        FreeStyleProject project = j.createFreeStyleProject("project");
        GlobalMatrixAuthorizationStrategy auth = new GlobalMatrixAuthorizationStrategy();   
        j.jenkins.setAuthorizationStrategy(auth);
        j.jenkins.setCrumbIssuer(null);
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        j.jenkins.setSecurityRealm(realm); 
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
        HtmlPage page = j.createWebClient().login(user.getId(), "password").goTo(project.getUrl() + "doWipeOutWorkspace");
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
    public static class AlwaysChangedSCM extends NullSCM{

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
}
