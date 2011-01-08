package hudson.maven;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.tasks.Maven.MavenInstallation;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Olivier Lamy
 */
public class Maven3BuildTest extends HudsonTestCase {
   
    public void testSimpleMaven3Build() throws Exception {
        
        MavenModuleSet m = createMavenProject();
        MavenInstallation mavenInstallation = configureMaven3();
        m.setMaven( mavenInstallation.getName() );
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven3-project.zip")));
        m.setGoals( "clean install" );
        MavenModuleSetBuild b = buildAndAssertSuccess(m);
        assertTrue( MavenUtil.maven3orLater( b.getMavenVersionUsed() ) );
    }
    
    public void testSimpleMaven3BuildRedeployPublisher() throws Exception {
        
        MavenModuleSet m = createMavenProject();
        MavenInstallation mavenInstallation = configureMaven3();
        m.setMaven( mavenInstallation.getName() );
        File repo = createTmpDir();
        FileUtils.cleanDirectory( repo );
        m.getReporters().add(new TestReporter());
        m.getPublishersList().add(new RedeployPublisher("",repo.toURI().toString(),true, false));
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven3-project.zip")));
        m.setGoals( "clean install" );
        MavenModuleSetBuild b = buildAndAssertSuccess(m);
        assertTrue( MavenUtil.maven3orLater( b.getMavenVersionUsed() ) );
        File artifactDir = new File(repo,"com/mycompany/app/my-app/1.7-SNAPSHOT/");
        String[] files = artifactDir.list( new FilenameFilter()
        {
            
            public boolean accept( File dir, String name )
            {
                System.out.println("file name : " +name );
                return name.endsWith( ".jar" );
            }
        });
        assertTrue("SNAPSHOT exist",!files[0].contains( "SNAPSHOT" ));
        assertTrue("file not ended with -1.jar", files[0].endsWith( "-1.jar" ));
    }    
    
    public void testSiteBuildWithForkedMojo() throws Exception {
        MavenModuleSet m = createMavenProject();
        MavenInstallation mavenInstallation = configureMaven3();
        m.setMaven( mavenInstallation.getName() );        
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven3-project.zip")));
        m.setGoals( "clean site" );
        MavenModuleSetBuild b = buildAndAssertSuccess(m);
        assertTrue( MavenUtil.maven3orLater( b.getMavenVersionUsed() ) );
    }    
    
    @Bug(value=8395)
    public void testMaven3BuildWrongScope() throws Exception {
        
        File pom = new File(this.getClass().getResource("test-pom-8395.xml").toURI());
        MavenModuleSet m = createMavenProject();
        MavenInstallation mavenInstallation = configureMaven3();
        m.setMaven( mavenInstallation.getName() );
        m.getReporters().add(new TestReporter());
        m.setRootPOM(pom.getAbsolutePath());
        m.setGoals( "clean validate" );
        MavenModuleSetBuild mmsb =  m.scheduleBuild2( 0 ).get();
        assertBuildStatus( Result.FAILURE, mmsb );
        System.out.println("mmsb.getProject().getModules " + mmsb.getProject().getModules() );
        assertTrue( mmsb.getProject().getModules().isEmpty());
    }
    
    @Bug(value=8390)
    public void testMaven3BuildWrongInheritence() throws Exception {
        
        MavenModuleSet m = createMavenProject();
        MavenInstallation mavenInstallation = configureMaven3();
        m.setMaven( mavenInstallation.getName() );
        m.getReporters().add(new TestReporter());
        m.setScm(new ExtractResourceSCM(getClass().getResource("incorrect-inheritence-testcase.zip")));
        m.setGoals( "clean validate" );
        MavenModuleSetBuild mmsb =  m.scheduleBuild2( 0 ).get();
        assertBuildStatus( Result.FAILURE, mmsb );
        System.out.println("mmsb.getProject().getModules " + mmsb.getProject().getModules() );
        assertTrue( mmsb.getProject().getModules().isEmpty());
    }    

    @Bug(value=8445)
    public void testMavenSeveralModulesInDirectory() throws Exception {
        
        MavenModuleSet m = createMavenProject();
        MavenInstallation mavenInstallation = configureMaven3();
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
        MavenInstallation mavenInstallation = configureMaven3();
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
    
    private static class TestReporter extends MavenReporter {
        @Override
        public boolean end(MavenBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            assertNotNull(build.getProject().getWorkspace());
            assertNotNull(build.getWorkspace());
            return true;
        }
    }
    
}
