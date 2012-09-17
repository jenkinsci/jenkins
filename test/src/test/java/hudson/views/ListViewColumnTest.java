package hudson.views;

import hudson.model.ListView;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class ListViewColumnTest extends HudsonTestCase {
    public void testCreateView() throws Exception {
        jenkins.addView(new ListView("test"));
        submit(createWebClient().goTo("view/test/configure").getFormByName("viewConfig"));
    }
}
