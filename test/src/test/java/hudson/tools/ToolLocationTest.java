package hudson.tools;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import hudson.tasks.Maven;
import hudson.tasks.Ant;
import hudson.model.JDK;

import java.util.Arrays;

/**
 * @author huybrechts
 */
public class ToolLocationTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Test xml compatibility since 'extends ToolInstallation'
     */
    @Test
    @LocalData
    public void toolCompatibility() {
        Maven.MavenInstallation[] maven = j.jenkins.getDescriptorByType(Maven.DescriptorImpl.class).getInstallations();
        assertEquals(maven.length, 1);
        assertEquals(maven[0].getHome(), "bar");
        assertEquals(maven[0].getName(), "Maven 1");

        Ant.AntInstallation[] ant = j.jenkins.getDescriptorByType(Ant.DescriptorImpl.class).getInstallations();
        assertEquals(ant.length, 1);
        assertEquals(ant[0].getHome(), "foo");
        assertEquals(ant[0].getName(), "Ant 1");

        JDK[] jdk = j.jenkins.getDescriptorByType(JDK.DescriptorImpl.class).getInstallations();
        assertEquals(Arrays.asList(jdk), j.jenkins.getJDKs());
        assertEquals(2, jdk.length); // HudsonTestCase adds a 'default' JDK
        assertEquals("default", jdk[1].getName()); // make sure it's really that we're seeing
        assertEquals("FOOBAR", jdk[0].getHome());
        assertEquals("FOOBAR", jdk[0].getJavaHome());
        assertEquals("1.6", jdk[0].getName());
    }
}
