package hudson.maven;

import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.ExtractResourceSCM;
import hudson.tasks.Maven.MavenInstallation;
import hudson.model.Slave;

import java.net.URLClassLoader;
import java.util.Arrays;

/**
 * @author huybrechts
 */
public class MavenProjectTest extends HudsonTestCase {

    public void testOnMaster() throws Exception {
        MavenModuleSet project = createMavenProject();
        MavenInstallation mi = configureDefaultMaven();
        project.setScm(new ExtractResourceSCM(getClass().getResource(
                "/simple-projects.zip")));
        project.setGoals("validate");
        project.setMaven(mi.getName());

        assertBuildStatusSuccess(project.scheduleBuild2(0).get());
    }

    public void testOnSlave() throws Exception {
        MavenModuleSet project = createMavenProject();
        MavenInstallation mi = configureDefaultMaven();
        project.setScm(new ExtractResourceSCM(getClass().getResource(
                "/simple-projects.zip")));
        project.setGoals("validate");
        project.setMaven(mi.getName());
        project.setAssignedLabel(createSlave().getSelfLabel());

        assertBuildStatusSuccess(project.scheduleBuild2(0).get());
    }


}
