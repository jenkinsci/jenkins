package hudson.model

import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.JenkinsRule

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class AbstractItemTest {
    @Rule
    public JenkinsRule j = new JenkinsRule()

    /**
     * Tests the reload functionality
     */
    @Test
    void reload() {
        def jenkins = j.jenkins;
        def p = jenkins.createProject(FreeStyleProject.class,"foo");
        p.description = "Hello World";

        def b = j.assertBuildStatusSuccess(p.scheduleBuild2(0))
        b.description = "This is my build"

        // update on disk representation
        def f = p.configFile.file
        f.text = f.text.replaceAll("Hello World","Good Evening");

        // reload away
        p.doReload()

        assert p.description == "Good Evening";

        def b2 = p.getBuildByNumber(1)

        assert !b.is(b2);   // should be different object
        assert b.description == b2.description   // but should have the same properties
    }
}
