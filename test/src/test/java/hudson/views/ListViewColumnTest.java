package hudson.views;

import hudson.model.ListView;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Kohsuke Kawaguchi
 */
public class ListViewColumnTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void createView() throws Exception {
        j.jenkins.addView(new ListView("test"));
        j.submit(j.createWebClient().goTo("view/test/configure").getFormByName("viewConfig"));
    }
}
