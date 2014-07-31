package hudson.maven;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.remoting.Which;
import hudson.slaves.DumbSlave;
import hudson.tasks.Maven.MavenInstallation;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;
import org.jvnet.hudson.test.TestExtension;
import test.BogusPlexusComponent;

import java.io.File;
import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class PlexusModuleContributorTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();


    /**
     * Tests the effect of PlexusModuleContributor by trying to parse a POM that uses a custom packaging
     * that only exists inside our custom jar.
     */
    @Test
    public void testCustomPlexusComponent() throws Exception {
        j.configureDefaultMaven("apache-maven-2.2.1", MavenInstallation.MAVEN_21);
        MavenModuleSet p = j.createMavenProject();
        p.setScm(new SingleFileSCM("pom.xml",getClass().getResource("custom-plexus-component.pom")));
        p.setGoals("clean");
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    }

    @Test
    public void testCustomPlexusComponent_Maven3() throws Exception {
        j.configureDefaultMaven("apache-maven-3.0.1", MavenInstallation.MAVEN_30);
        MavenModuleSet p = j.createMavenProject();
        p.setScm(new SingleFileSCM("pom.xml",getClass().getResource("custom-plexus-component.pom")));
        p.setGoals("clean");
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    }

    @Test
    public void testCustomPlexusComponent_Maven3_slave() throws Exception {
        j.configureDefaultMaven("apache-maven-3.0.1", MavenInstallation.MAVEN_30);
        DumbSlave s = j.createSlave();
        s.toComputer().connect(false).get();

        MavenModuleSet p = j.createMavenProject();
        p.setAssignedLabel(s.getSelfLabel());

        p.setScm(new SingleFileSCM("pom.xml",getClass().getResource("custom-plexus-component.pom")));
        p.setGoals("clean");
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    }

    @TestExtension
    public static class PlexusLoader extends PlexusModuleContributorFactory {
        @Override
        public PlexusModuleContributor createFor(AbstractBuild<?, ?> context) throws IOException, InterruptedException {
            File bogusPlexusJar = Which.jarFile(BogusPlexusComponent.class);
            final FilePath localJar = context.getBuiltOn().getRootPath().child("cache/bogusPlexus.jar");
            localJar.copyFrom(new FilePath(bogusPlexusJar));

            return PlexusModuleContributor.of(localJar);
        }
    }
}
