package hudson.maven;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.queue.QueueTaskFuture;
import hudson.tasks.Maven.MavenInstallation;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.AggregatedTestResultAction;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;

/**
 * @author Kohsuke Kawaguchi
 */
public class MavenBuildTest {

    @Rule public JenkinsRule j = new JenkinsRule();
    
    /**
     * NPE in {@code build.getProject().getWorkspace()} for {@link MavenBuild}.
     */
    @Bug(4192)
    @Test
    public void testMavenWorkspaceExists() throws Exception {
        j.configureDefaultMaven();
        MavenModuleSet m = j.createMavenProject();
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("HUDSON-4192.zip")));
        j.buildAndAssertSuccess(m);
    }
    
    /**
     * {@link Result} getting set to SUCCESS even if there's a test failure, when the test failure
     * does not happen in the final task segment.
     */
    @Bug(4177)
    @Test
    public void testTestFailureInEarlyTaskSegment() throws Exception {
        j.configureDefaultMaven();
        MavenModuleSet m = j.createMavenProject();
        m.setGoals("clean install findbugs:findbugs");
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-test-failure-findbugs.zip")));
        j.assertBuildStatus(Result.UNSTABLE, m.scheduleBuild2(0).get());
    }
    
    /**
     * Verify that a compilation error properly shows up as a failure.
     */
    @Test
    public void testCompilationFailure() throws Exception {
        j.configureDefaultMaven();
        MavenModuleSet m = j.createMavenProject();
        m.setGoals("clean install");
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-compilation-failure.zip")));
        j.assertBuildStatus(Result.FAILURE, m.scheduleBuild2(0).get());
    }
    
    /**
     * Workspace determination problem on non-aggregator style build.
     */
    @Bug(4226)
    @Test
    public void testParallelModuleBuild() throws Exception {
        j.configureDefaultMaven();
        MavenModuleSet m = j.createMavenProject();
        m.setScm(new ExtractResourceSCM(getClass().getResource("multimodule-maven.zip")));
        
        j.buildAndAssertSuccess(m);

        m.setAggregatorStyleBuild(false);

        // run module builds
        j.buildAndAssertSuccess(m.getModule("test$module1"));
        j.buildAndAssertSuccess(m.getModule("test$module1"));
    }
    
    @Bug(value=8395)
    @Test
    public void testMaven2BuildWrongScope() throws Exception {
        
        File pom = new File(this.getClass().getResource("test-pom-8395.xml").toURI());
        MavenModuleSet m = j.createMavenProject();
        MavenInstallation mavenInstallation = j.configureDefaultMaven();
        m.setMaven( mavenInstallation.getName() );
        m.getReporters().add(new TestReporter());
        m.setRootPOM(pom.getAbsolutePath());
        m.setGoals( "clean validate" );
        MavenModuleSetBuild mmsb =  j.buildAndAssertSuccess(m);
        assertFalse( mmsb.getProject().getModules().isEmpty());
    }    
    
    @Bug(value=8390)
    @Test
    public void testMaven2BuildWrongInheritence() throws Exception {
        
        MavenModuleSet m = j.createMavenProject();
        MavenInstallation mavenInstallation = j.configureDefaultMaven();
        m.setMaven( mavenInstallation.getName() );
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("incorrect-inheritence-testcase.zip")));
        m.setGoals( "clean validate" );
        MavenModuleSetBuild mmsb =  j.buildAndAssertSuccess(m);
        assertFalse( mmsb.getProject().getModules().isEmpty());
    }   

    @Bug(value=8445)
    @Test
    public void testMaven2SeveralModulesInDirectory() throws Exception {
        
        MavenModuleSet m = j.createMavenProject();
        MavenInstallation mavenInstallation = j.configureDefaultMaven();
        m.setMaven( mavenInstallation.getName() );
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("several-modules-in-directory.zip")));
        m.setGoals( "clean validate" );
        MavenModuleSetBuild mmsb =  j.buildAndAssertSuccess(m);
        assertFalse( mmsb.getProject().getModules().isEmpty());
    }    

    @Email("https://groups.google.com/d/msg/hudson-users/Xhw00UopVN0/FA9YqDAIsSYJ")
    @Test
    public void testMavenWithDependencyVersionInEnvVar() throws Exception {
        
        MavenModuleSet m = j.createMavenProject();
        MavenInstallation mavenInstallation = j.configureDefaultMaven();
        ParametersDefinitionProperty parametersDefinitionProperty = 
            new ParametersDefinitionProperty(new StringParameterDefinition( "JUNITVERSION", "3.8.2" ));
        
        m.addProperty( parametersDefinitionProperty );
        m.setMaven( mavenInstallation.getName() );
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("envars-maven-project.zip")));
        m.setGoals( "clean test-compile" );
        MavenModuleSetBuild mmsb =  j.buildAndAssertSuccess(m);
        assertFalse( mmsb.getProject().getModules().isEmpty());
    }     
    
    @Bug(8573)
    @Test
    public void testBuildTimeStampProperty() throws Exception {
        MavenInstallation mavenInstallation = j.configureDefaultMaven();
        MavenModuleSet m = j.createMavenProject();
        m.setMaven( mavenInstallation.getName() );
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("JENKINS-8573.zip")));
        m.setGoals( "process-resources" );
        j.buildAndAssertSuccess(m);
        String content = m.getLastBuild().getWorkspace().child( "target/classes/test.txt" ).readToString();
        assertFalse( content.contains( "${maven.build.timestamp}") );
        assertFalse( content.contains( "${maven.build.timestamp}") );

        System.out.println( "content " + content );
    }
    
    @Bug(value=15865)
    @Test
    public void testMavenFailsafePluginTestResultsAreRecorded() throws Exception {
        
        // GIVEN: a Maven project with maven-failsafe-plugin and Maven 2.2.1
        MavenModuleSet mavenProject = j.createMavenProject();
        MavenInstallation mavenInstallation = j.configureDefaultMaven();
        mavenProject.setMaven(mavenInstallation.getName());
        mavenProject.getReporters().add(new TestReporter());
        mavenProject.setScm(new ExtractResourceSCM(getClass().getResource("JENKINS-15865.zip")));
        mavenProject.setGoals( "clean install" );
        
        // WHEN project is build
        MavenModuleSetBuild mmsb = j.buildAndAssertSuccess(mavenProject);
        
        // THEN we have a testresult recorded
        AggregatedTestResultAction aggregatedTestResultAction = mmsb.getAggregatedTestResultAction();
        assertNotNull(aggregatedTestResultAction);
        assertEquals(1, aggregatedTestResultAction.getTotalCount());
        
        Map<MavenModule, MavenBuild> moduleBuilds = mmsb.getModuleLastBuilds();
        assertEquals(1, moduleBuilds.size());
        MavenBuild moduleBuild = moduleBuilds.values().iterator().next();
         AbstractTestResultAction<?> testResultAction = moduleBuild.getTestResultAction();
        assertNotNull(testResultAction);
        assertEquals(1, testResultAction.getTotalCount());
    }

    @Bug(18178)
    @Test
    public void testExtensionsConflictingWithCore() throws Exception {
        MavenModuleSet m = j.createMavenProject();
        m.setMaven(j.configureDefaultMaven().getName());
        m.setScm(new SingleFileSCM("pom.xml",
                "<project><modelVersion>4.0.0</modelVersion>" +
                "<groupId>g</groupId><artifactId>a</artifactId><version>0</version>" +
                "<build><extensions>" +
                "<extension><groupId>org.springframework.build.aws</groupId><artifactId>org.springframework.build.aws.maven</artifactId><version>3.0.0.RELEASE</version></extension>" +
                "</extensions></build></project>"));
        j.buildAndAssertSuccess(m);
    }

    @Bug(19801)
    @Test
    public void stopBuildAndAllSubmoduleBuilds() throws Exception {
        j.configureDefaultMaven();
        MavenModuleSet project = j.createMavenProject();
        project.setGoals("clean package");
        project.setScm(new ExtractResourceSCM(
                getClass().getResource("/hudson/maven/maven-multimod.zip")
        ));

        MavenModuleSetBuild build = project.scheduleBuild2(0).waitForStart();

        ensureSubmoduleBuildsStarted(build);

        build.doStop();

        Thread.sleep(2000);
        j.assertBuildStatus(Result.ABORTED, build);
        assertFalse(build.isBuilding());
        for (MavenBuild mb: build.getModuleLastBuilds().values()) {
            final String moduleName = mb.getParent().getDisplayName();
            assertFalse("Module " + moduleName + " is still building", mb.isBuilding());
        }
    }

    private void ensureSubmoduleBuildsStarted(MavenModuleSetBuild build) throws InterruptedException {
        for (;;) {
            for (MavenBuild mb: build.getModuleLastBuilds().values()) {
                if (Result.SUCCESS.equals(mb.getResult())) return;
            }
            Thread.sleep(1000);
        }
    }

    private static class TestReporter extends MavenReporter {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean end(MavenBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            assertNotNull(build.getWorkspace());
            return true;
        }
    }    
    
}
