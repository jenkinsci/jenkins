package hudson.maven;

import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.ExtractResourceSCM;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.util.NullStream;

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
        m.setAssignedLabel(createSlave(null, new EnvVars("MAVEN_OPTS", "-Dhudson.mavenOpt.test=foo")).getSelfLabel());
        
        assertBuildStatusSuccess(m.scheduleBuild2(0).get());

        assertLogContains("[hudson.mavenOpt.test=foo]", m.getLastBuild());
    }

    
    public void testEnvMavenOptsOverriddenByProject() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-opts-echo.zip")));
        m.setGoals("validate");
        m.setMavenOpts("-Dhudson.mavenOpt.test=bar");
        m.setAssignedLabel(createSlave(null, new EnvVars("MAVEN_OPTS", "-Dhudson.mavenOpt.test=foo")).getSelfLabel());
        
        assertBuildStatusSuccess(m.scheduleBuild2(0).get());

        assertLogContains("[hudson.mavenOpt.test=bar]", m.getLastBuild());
    }

    public void testEnvAndGlobalMavenOptsOverriddenByProject() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-opts-echo.zip")));
        m.setGoals("validate");
        m.DESCRIPTOR.setGlobalMavenOpts("-Dhudson.mavenOpt.test=bar");
        m.setAssignedLabel(createSlave(null, new EnvVars("MAVEN_OPTS", "-Dhudson.mavenOpt.test=foo")).getSelfLabel());
        m.setMavenOpts("-Dhudson.mavenOpt.test=baz");
        
        assertBuildStatusSuccess(m.scheduleBuild2(0).get());

        assertLogContains("[hudson.mavenOpt.test=baz]", m.getLastBuild());
    }


    public void testGlobalMavenOpts() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-opts-echo.zip")));
        m.setGoals("validate");
        m.DESCRIPTOR.setGlobalMavenOpts("-Dhudson.mavenOpt.test=bar");
        
        assertBuildStatusSuccess(m.scheduleBuild2(0).get());

        assertLogContains("[hudson.mavenOpt.test=bar]", m.getLastBuild());
    }

    public void testGlobalMavenOptsOverridenByProject() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m = createMavenProject();
        m.setScm(new ExtractResourceSCM(getClass().getResource("maven-opts-echo.zip")));
        m.setGoals("validate");
        m.DESCRIPTOR.setGlobalMavenOpts("-Dhudson.mavenOpt.test=bar");
        m.setMavenOpts("-Dhudson.mavenOpt.test=foo");
       
        assertBuildStatusSuccess(m.scheduleBuild2(0).get());

        assertLogContains("[hudson.mavenOpt.test=foo]", m.getLastBuild());
    }
}