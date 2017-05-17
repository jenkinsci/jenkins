package jenkins.mvn;

import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.Result;
import hudson.tasks.Maven;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;
import org.jvnet.hudson.test.ToolInstallations;

import java.net.URL;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class SettingsProviderTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Before
    public void setup() throws Exception {
        // apache-maven version 3.0.1 is the version shipped with the test harness. 3.3.9 is not shipped in the test harness
        ToolInstallations.configureDefaultMaven("apache-maven-3.0.1", Maven.MavenInstallation.MAVEN_30);

        GlobalMavenConfig globalMavenConfig = jenkinsRule.get(GlobalMavenConfig.class);
        globalMavenConfig.setGlobalSettingsProvider(new DefaultGlobalSettingsProvider());
        globalMavenConfig.setSettingsProvider(new DefaultSettingsProvider());
    }

    @Test
    public void basic_maven_build_succeeds() throws Exception {

        MavenModuleSet project = jenkinsRule.createProject(MavenModuleSet.class, "maven-project");
        URL pomFile = Thread.currentThread().getContextClassLoader().getResource("jenkins/mvn/pom.xml");
        project.setScm(new SingleFileSCM("pom.xml", pomFile));
        project.setGoals("clean");

        MavenModuleSetBuild build = project.scheduleBuild2(0).get(1, TimeUnit.MINUTES);
        assertNotNull(build);
        jenkinsRule.assertBuildStatusSuccess(build);
    }

    @Test
    public void maven_build_with_settings_defined_globally_succeeds() throws Exception {

        GlobalMavenConfig globalMavenConfig = jenkinsRule.get(GlobalMavenConfig.class);
        URL settingsFile = Thread.currentThread().getContextClassLoader().getResource("jenkins/mvn/settings.xml");
        globalMavenConfig.setSettingsProvider(new FilePathSettingsProvider(settingsFile.getFile()));

        MavenModuleSet project = jenkinsRule.createProject(MavenModuleSet.class, "maven-build-project-with-settings-defined-globally");
        URL pomFile = Thread.currentThread().getContextClassLoader().getResource("jenkins/mvn/pom.xml");
        project.setScm(new SingleFileSCM("pom.xml", pomFile));
        project.setGoals("clean");

        MavenModuleSetBuild build = project.scheduleBuild2(0).get(1, TimeUnit.MINUTES);
        assertNotNull(build);
        jenkinsRule.assertBuildStatusSuccess(build);
    }

    @Test
    public void maven_build_with_missing_settings_defined_globally_fails() throws Exception {

        GlobalMavenConfig globalMavenConfig = jenkinsRule.get(GlobalMavenConfig.class);
        globalMavenConfig.setSettingsProvider(new FilePathSettingsProvider("/tmp/settings-do-not-exist.xml"));

        MavenModuleSet project = jenkinsRule.createProject(MavenModuleSet.class, "maven-build-project-with-missing-settings-defined-globally");
        URL pomFile = Thread.currentThread().getContextClassLoader().getResource("jenkins/mvn/pom.xml");
        project.setScm(new SingleFileSCM("pom.xml", pomFile));
        project.setGoals("clean");

        MavenModuleSetBuild build = project.scheduleBuild2(0).get(1, TimeUnit.MINUTES);
        assertNotNull(build);
        jenkinsRule.assertBuildStatus(Result.FAILURE, build);
    }


    @Test
    public void maven_build_with_settings_defined_globally_with_an_invalid_file_fails() throws Exception {

        SettingsProviderHelper.ABSOlUTE_PATH_BACKWARD_COMPATIBILITY = true;
        try {
            GlobalMavenConfig globalMavenConfig = jenkinsRule.get(GlobalMavenConfig.class);
            URL settingsFile = Thread.currentThread().getContextClassLoader().getResource("jenkins/mvn/random-text-file.txt");
            globalMavenConfig.setGlobalSettingsProvider(new FilePathGlobalSettingsProvider(settingsFile.getFile()));

            MavenModuleSet project = jenkinsRule.createProject(MavenModuleSet.class, "maven-build-project-with-settings-defined-globally-with-an-invalid-file");
            URL pomFile = Thread.currentThread().getContextClassLoader().getResource("jenkins/mvn/pom.xml");
            project.setScm(new SingleFileSCM("pom.xml", pomFile));
            project.setGoals("clean");

            MavenModuleSetBuild build = project.scheduleBuild2(0).get(1, TimeUnit.MINUTES);
            assertNotNull(build);
            jenkinsRule.assertBuildStatus(Result.FAILURE, build);
            jenkinsRule.assertLogContains("IllegalStateException: Failed to prepare Maven (global) settings.xml with path", build);
        } finally {
            SettingsProviderHelper.ABSOlUTE_PATH_BACKWARD_COMPATIBILITY = false;
        }
    }

    @Test
    public void maven_build_with_global_settings_defined_globally_succeeds() throws Exception {

        GlobalMavenConfig globalMavenConfig = jenkinsRule.get(GlobalMavenConfig.class);
        URL settingsFile = Thread.currentThread().getContextClassLoader().getResource("jenkins/mvn/settings.xml");
        globalMavenConfig.setGlobalSettingsProvider(new FilePathGlobalSettingsProvider(settingsFile.getFile()));

        MavenModuleSet project = jenkinsRule.createProject(MavenModuleSet.class, "maven-build-project-with-settings-defined-globally");
        URL pomFile = Thread.currentThread().getContextClassLoader().getResource("jenkins/mvn/pom.xml");
        project.setScm(new SingleFileSCM("pom.xml", pomFile));
        project.setGoals("clean");

        MavenModuleSetBuild build = project.scheduleBuild2(0).get(1, TimeUnit.MINUTES);
        assertNotNull(build);
        jenkinsRule.assertBuildStatusSuccess(build);
    }
}
