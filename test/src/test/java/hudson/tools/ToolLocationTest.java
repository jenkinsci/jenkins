package hudson.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.JDK;
import hudson.tasks.Ant;
import hudson.tasks.Maven;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * @author huybrechts
 */
@WithJenkins
class ToolLocationTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Test xml compatibility since 'extends ToolInstallation'
     */
    @Test
    @LocalData
    void toolCompatibility() {
        Maven.MavenInstallation[] maven = j.jenkins.getDescriptorByType(Maven.DescriptorImpl.class).getInstallations();
        assertEquals(1, maven.length);
        assertEquals("bar", maven[0].getHome());
        assertEquals("Maven 1", maven[0].getName());

        Ant.AntInstallation[] ant = j.jenkins.getDescriptorByType(Ant.DescriptorImpl.class).getInstallations();
        assertEquals(1, ant.length);
        assertEquals("foo", ant[0].getHome());
        assertEquals("Ant 1", ant[0].getName());

        JDK[] jdk = j.jenkins.getDescriptorByType(JDK.DescriptorImpl.class).getInstallations();
        assertEquals(Arrays.asList(jdk), j.jenkins.getJDKs());
        assertEquals(2, jdk.length); // JenkinsRule adds a 'default' JDK
        assertEquals("default", jdk[1].getName()); // make sure it's really that we're seeing
        assertEquals("FOOBAR", jdk[0].getHome());
        assertEquals("FOOBAR", jdk[0].getJavaHome());
        assertEquals("1.6", jdk[0].getName());
    }
}
