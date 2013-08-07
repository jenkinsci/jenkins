package hudson.maven;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.tasks.Maven.MavenInstallation;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.AggregatedTestResultAction;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.SingleFileSCM;

/**
 * @author Kohsuke Kawaguchi
 */
public class MavenBuildTest extends HudsonTestCase {
    
    /**
     * NPE in {@code build.getProject().getWorkspace()} for {@link MavenBuild}.
     */
    @Bug(4192)
    public void testMavenWorkspaceExists() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("HUDSON-4192.zip")));
        buildAndAssertSuccess(m);
    }
    
    /**
     * {@link Result} getting set to SUCCESS even if there's a test failure, when the test failure
     * does not happen in the final task segment.
     */
    @Bug(4177)
    public void testTestFailureInEarlyTaskSegment() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.setGoals("clean install findbugs:findbugs");
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-test-failure-findbugs.zip")));
        assertBuildStatus(Result.UNSTABLE, m.scheduleBuild2(0).get());
    }
    
    /**
     * Verify that a compilation error properly shows up as a failure.
     */
    public void testCompilationFailure() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.setGoals("clean install");
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-compilation-failure.zip")));
        assertBuildStatus(Result.FAILURE, m.scheduleBuild2(0).get());
    }
    
    /**
     * Workspace determination problem on non-aggregator style build.
     */
    @Bug(4226)
    public void testParallelModuleBuild() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.setScm(new ExtractResourceSCM(getClass().getResource("multimodule-maven.zip")));
        
        buildAndAssertSuccess(m);

        m.setAggregatorStyleBuild(false);

        // run module builds
        buildAndAssertSuccess(m.getModule("test$module1"));
        buildAndAssertSuccess(m.getModule("test$module1"));
    }
    
    @Bug(value=8395)
    public void testMaven2BuildWrongScope() throws Exception {
        
        File pom = new File(this.getClass().getResource("test-pom-8395.xml").toURI());
        MavenModuleSet m = createMavenProject();
        MavenInstallation mavenInstallation = configureDefaultMaven();
        m.setMaven( mavenInstallation.getName() );
        m.getReporters().add(new TestReporter());
        m.setRootPOM(pom.getAbsolutePath());
        m.setGoals( "clean validate" );
        MavenModuleSetBuild mmsb =  buildAndAssertSuccess(m);
        assertFalse( mmsb.getProject().getModules().isEmpty());
    }    
    
    @Bug(value=8390)
    public void testMaven2BuildWrongInheritence() throws Exception {
        
        MavenModuleSet m = createMavenProject();
        MavenInstallation mavenInstallation = configureDefaultMaven();
        m.setMaven( mavenInstallation.getName() );
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("incorrect-inheritence-testcase.zip")));
        m.setGoals( "clean validate" );
        MavenModuleSetBuild mmsb =  buildAndAssertSuccess(m);
        assertFalse( mmsb.getProject().getModules().isEmpty());
    }   

    @Bug(value=8445)
    public void testMaven2SeveralModulesInDirectory() throws Exception {
        
        MavenModuleSet m = createMavenProject();
        MavenInstallation mavenInstallation = configureDefaultMaven();
        m.setMaven( mavenInstallation.getName() );
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("several-modules-in-directory.zip")));
        m.setGoals( "clean validate" );
        MavenModuleSetBuild mmsb =  buildAndAssertSuccess(m);
        assertFalse( mmsb.getProject().getModules().isEmpty());
    }    

    @Email("https://groups.google.com/d/msg/hudson-users/Xhw00UopVN0/FA9YqDAIsSYJ")
    public void testMavenWithDependencyVersionInEnvVar() throws Exception {
        
        MavenModuleSet m = createMavenProject();
        MavenInstallation mavenInstallation = configureDefaultMaven();
        ParametersDefinitionProperty parametersDefinitionProperty = 
            new ParametersDefinitionProperty(new StringParameterDefinition( "JUNITVERSION", "3.8.2" ));
        
        m.addProperty( parametersDefinitionProperty );
        m.setMaven( mavenInstallation.getName() );
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("envars-maven-project.zip")));
        m.setGoals( "clean test-compile" );
        MavenModuleSetBuild mmsb =  buildAndAssertSuccess(m);
        assertFalse( mmsb.getProject().getModules().isEmpty());
    }     
    
    @Bug(8573)
    public void testBuildTimeStampProperty() throws Exception {
        MavenInstallation mavenInstallation = configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.setMaven( mavenInstallation.getName() );
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("JENKINS-8573.zip")));
        m.setGoals( "process-resources" );
        buildAndAssertSuccess(m);
        String content = m.getLastBuild().getWorkspace().child( "target/classes/test.txt" ).readToString();
        assertFalse( content.contains( "${maven.build.timestamp}") );
        assertFalse( content.contains( "${maven.build.timestamp}") );

        System.out.println( "content " + content );
    }
    
    @Bug(value=15865)
    public void testMavenFailsafePluginTestResultsAreRecorded() throws Exception {
        
        // GIVEN: a Maven project with maven-failsafe-plugin and Maven 2.2.1
        MavenModuleSet mavenProject = createMavenProject();
        MavenInstallation mavenInstallation = configureDefaultMaven();
        mavenProject.setMaven(mavenInstallation.getName());
        mavenProject.getReporters().add(new TestReporter());
        mavenProject.setScm(new ExtractResourceSCM(getClass().getResource("JENKINS-15865.zip")));
        mavenProject.setGoals( "clean install" );
        
        // WHEN project is build
        MavenModuleSetBuild mmsb =  buildAndAssertSuccess(mavenProject);
        
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
    public void testExtensionsConflictingWithCore() throws Exception {
        MavenModuleSet m = createMavenProject();
        m.setMaven(configureDefaultMaven().getName());
        m.setScm(new SingleFileSCM("pom.xml",
                "<project><modelVersion>4.0.0</modelVersion>" +
                "<groupId>g</groupId><artifactId>a</artifactId><version>0</version>" +
                "<build><extensions>" +
                "<extension><groupId>org.springframework.build.aws</groupId><artifactId>org.springframework.build.aws.maven</artifactId><version>3.0.0.RELEASE</version></extension>" +
                "</extensions></build></project>"));
        buildAndAssertSuccess(m);
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
