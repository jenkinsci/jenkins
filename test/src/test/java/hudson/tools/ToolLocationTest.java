package hudson.tools;

import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;
import hudson.tasks.Maven;
import hudson.tasks.Ant;
import hudson.model.JDK;
import hudson.model.Hudson;

import static junit.framework.Assert.*;

import java.util.Arrays;

/**
 * @author huybrechts
 */
public class ToolLocationTest extends HudsonTestCase {

    /**
     * Test xml compatibility since 'extends ToolInstallation'
     */
    @LocalData
    public void testToolCompatibility() {
        Maven.MavenInstallation[] maven = Hudson.getInstance().getDescriptorByType(Maven.DescriptorImpl.class).getInstallations();
        assertEquals(maven.length, 1);
        assertEquals(maven[0].getHome(), "bar");
        assertEquals(maven[0].getName(), "Maven 1");


        Ant.AntInstallation[] ant = Hudson.getInstance().getDescriptorByType(Ant.DescriptorImpl.class).getInstallations();
        assertEquals(ant.length, 1);
        assertEquals(ant[0].getHome(), "foo");
        assertEquals(ant[0].getName(), "Ant 1");

        JDK[] jdk = Hudson.getInstance().getDescriptorByType(JDK.DescriptorImpl.class).getInstallations();
        assertEquals(Arrays.asList(jdk), Hudson.getInstance().getJDKs());
        assertEquals(2, jdk.length); // HudsonTestCase adds a 'default' JDK
        assertEquals("default", jdk[1].getName()); // make sure it's really that we're seeing
        assertEquals("FOOBAR", jdk[0].getHome());
        assertEquals("FOOBAR", jdk[0].getJavaHome());
        assertEquals("1.6", jdk[0].getName());

    }
}
