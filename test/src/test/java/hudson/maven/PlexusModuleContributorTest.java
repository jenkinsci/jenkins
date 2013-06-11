package hudson.maven;

import hudson.remoting.Which;
import hudson.tasks.Maven.MavenInstallation;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;
import org.jvnet.hudson.test.TestExtension;
import test.BogusPlexusComponent;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

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

    @TestExtension
    public static class PlexusLoader extends PlexusModuleContributor {
        private URL bogusPlexusJar;

        public PlexusLoader() {
            try {
                this.bogusPlexusJar = Which.jarFile(BogusPlexusComponent.class).toURL();
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public List<URL> getPlexusComponentJars() {
            return Collections.singletonList(bogusPlexusJar);
        }
    }
}
