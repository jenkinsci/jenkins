package hudson.security;

import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;

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
        new WebClient().login("alice","alice");

        try {
            // this should fail
            new WebClient().login("bob","bob");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(401,e.getStatusCode());
        }
    }
}
