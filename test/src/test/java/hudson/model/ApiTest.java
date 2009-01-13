package hudson.model;

import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Bug;

/**
 * @author Kohsuke Kawaguchi
 */
public class ApiTest extends HudsonTestCase {
    @Bug(2828)
    public void testXPath() throws Exception {
        new WebClient().goTo("api/xml?xpath=/*[1]","application/xml");
    }
}
