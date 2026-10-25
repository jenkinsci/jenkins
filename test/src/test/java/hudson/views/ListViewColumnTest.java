package hudson.views;

import hudson.model.ListView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class ListViewColumnTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void createView() throws Exception {
        j.jenkins.addView(new ListView("test"));
        j.submit(j.createWebClient().goTo("view/test/configure").getFormByName("viewConfig"));
    }
}
