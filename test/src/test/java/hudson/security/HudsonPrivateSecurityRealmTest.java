package hudson.security;

import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

/**
 * @author Kohsuke Kawaguchi
 */
public class HudsonPrivateSecurityRealmTest extends HudsonTestCase {
    /**
     * Tests the data compatibility with Hudson before 1.283.
     * Starting 1.283, passwords are now stored hashed.
     */
    @Bug(2381)
    @LocalData
    public void testDataCompatibilityWith1_282() throws Exception {
        // make sure we can login with the same password as before
        WebClient wc = new WebClient().login("alice", "alice");

        try {
            // verify the sanity that the password is really used
            // this should fail
            new WebClient().login("bob","bob");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(401,e.getStatusCode());
        }

        // resubmit the config and this should force the data store to be rewritten
        HtmlPage p = wc.goTo("user/alice/configure");
        submit(p.getFormByName("config"));

        // verify that we can still login
        new WebClient().login("alice", "alice");
    }
}
