package hudson.tools;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import hudson.model.JDK;

import java.util.Arrays;
import org.jenkinsci.plugins.jdk_tool.JDKs;

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
        JDK[] jdk = JDKs.getDescriptor().getInstallations();
        assertEquals(Arrays.asList(jdk), j.jenkins.getJDKs());
        assertEquals(2, jdk.length); // HudsonTestCase adds a 'default' JDK
        assertEquals("default", jdk[1].getName()); // make sure it's really that we're seeing
        assertEquals("FOOBAR", jdk[0].getHome());
        assertEquals("FOOBAR", jdk[0].getJavaHome());
        assertEquals("1.6", jdk[0].getName());
    }
}
