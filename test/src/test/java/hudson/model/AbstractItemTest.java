package hudson.model;

import java.io.File;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class AbstractItemTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Tests the reload functionality
     */
    @Test
    public void reload() throws Exception {
        Jenkins jenkins = j.jenkins;
        FreeStyleProject p = jenkins.createProject(FreeStyleProject.class, "foo");
        p.setDescription("Hello World");

        FreeStyleBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        b.setDescription("This is my build");

        // update on disk representation
        File f = p.getConfigFile().getFile();
        FileUtils.writeStringToFile(f, FileUtils.readFileToString(f).replaceAll("Hello World", "Good Evening"));

        // reload away
        p.doReload();

        assertEquals("Good Evening", p.getDescription());

        FreeStyleBuild b2 = p.getBuildByNumber(1);

        assertNotEquals(b, b2); // should be different object
        assertEquals(b.getDescription(), b2.getDescription()); // but should have the same properties
    }

}
