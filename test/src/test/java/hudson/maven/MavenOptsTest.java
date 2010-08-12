package hudson.maven;

import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.ExtractResourceSCM;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.util.NullStream;
import hudson.tasks.Maven.MavenInstallation;

import java.io.IOException;
import java.io.PrintWriter;


/**
 * @author Andrew Bayer
 */
public class MavenOptsTest extends HudsonTestCase {

    public void testEnvMavenOptsNoneInProject() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-opts-echo.zip")));
        m.setGoals("validate");
        m.setAssignedLabel(createSlave(new EnvVars("MAVEN_OPTS", "-Dhudson.mavenOpt.test=foo")).getSelfLabel());
        
        buildAndAssertSuccess(m);

        assertLogContains("[hudson.mavenOpt.test=foo]", m.getLastBuild());
    }

    
    public void testEnvMavenOptsOverriddenByProject() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-opts-echo.zip")));
        m.setGoals("validate");
        m.setMavenOpts("-Dhudson.mavenOpt.test=bar");
        m.setAssignedLabel(createSlave(new EnvVars("MAVEN_OPTS", "-Dhudson.mavenOpt.test=foo")).getSelfLabel());
        
        buildAndAssertSuccess(m);

        assertLogContains("[hudson.mavenOpt.test=bar]", m.getLastBuild());
    }

    public void testEnvAndGlobalMavenOptsOverriddenByProject() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-opts-echo.zip")));
        m.setGoals("validate");
        m.DESCRIPTOR.setGlobalMavenOpts("-Dhudson.mavenOpt.test=bar");
        m.setAssignedLabel(createSlave(new EnvVars("MAVEN_OPTS", "-Dhudson.mavenOpt.test=foo")).getSelfLabel());
        m.setMavenOpts("-Dhudson.mavenOpt.test=baz");
        
        buildAndAssertSuccess(m);

        assertLogContains("[hudson.mavenOpt.test=baz]", m.getLastBuild());
    }


    public void testGlobalMavenOpts() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-opts-echo.zip")));
        m.setGoals("validate");
        m.DESCRIPTOR.setGlobalMavenOpts("-Dhudson.mavenOpt.test=bar");
        
        buildAndAssertSuccess(m);

        assertLogContains("[hudson.mavenOpt.test=bar]", m.getLastBuild());
    }

    public void testGlobalMavenOptsOverridenByProject() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-opts-echo.zip")));
        m.setGoals("validate");
        m.DESCRIPTOR.setGlobalMavenOpts("-Dhudson.mavenOpt.test=bar");
        m.setMavenOpts("-Dhudson.mavenOpt.test=foo");
       
        buildAndAssertSuccess(m);

        assertLogContains("[hudson.mavenOpt.test=foo]", m.getLastBuild());
    }
    
    @Bug(5651)
    public void testNewlinesInOptsRemoved() throws Exception {
        configureDefaultMaven("apache-maven-2.2.1", MavenInstallation.MAVEN_21);
        MavenModuleSet m = createMavenProject();
	m.setScm(new ExtractResourceSCM(getClass().getResource("maven-surefire-unstable.zip")));
        m.setMavenOpts("-XX:MaxPermSize=512m\r\n-Xms128m\r\n-Xmx512m");
        m.setGoals("install");
        
	assertBuildStatus(Result.UNSTABLE, m.scheduleBuild2(0).get());
	MavenModuleSetBuild pBuild = m.getLastBuild();

	assertEquals("Parent build should have Result.UNSTABLE", Result.UNSTABLE, pBuild.getResult());
	
    }

}

