package hudson.model;

import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Bug;

/**
 * @author Kohsuke Kawaguchi
 */
public class ComputerSetTest extends HudsonTestCase {
    @Bug(2821)
    public void testPageRendering() throws Exception {
        HudsonTestCase.WebClient client = new WebClient();
        createSlave();
        client.goTo("computer");
    }
}
