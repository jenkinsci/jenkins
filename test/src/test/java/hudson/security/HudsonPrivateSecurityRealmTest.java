package hudson.security;

import static hudson.security.HudsonPrivateSecurityRealm.CLASSIC;
import static hudson.security.HudsonPrivateSecurityRealm.PASSWORD_ENCODER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.WithoutJenkins;
import org.jvnet.hudson.test.recipes.LocalData;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

/**
 * @author Kohsuke Kawaguchi
 */
public class HudsonPrivateSecurityRealmTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Tests the data compatibility with Hudson before 1.283.
     * Starting 1.283, passwords are now stored hashed.
     */
    @Test
    @Issue("JENKINS-2381")
    @LocalData
    public void dataCompatibilityWith1_282() throws Exception {
        // make sure we can login with the same password as before
        WebClient wc = j.createWebClient().login("alice", "alice");

        try {
            // verify the sanity that the password is really used
            // this should fail
            j.createWebClient().login("bob", "bob");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(401,e.getStatusCode());
        }

        // resubmit the config and this should force the data store to be rewritten
        HtmlPage p = wc.goTo("user/alice/configure");
        j.submit(p.getFormByName("config"));

        // verify that we can still login
        j.createWebClient().login("alice", "alice");
    }

    @Test
    @WithoutJenkins
    public void hashCompatibility() {
        String old = CLASSIC.encodePassword("hello world", null);
        assertTrue(PASSWORD_ENCODER.isPasswordValid(old,"hello world",null));

        String secure = PASSWORD_ENCODER.encodePassword("hello world", null);
        assertTrue(PASSWORD_ENCODER.isPasswordValid(old,"hello world",null));

        assertFalse(secure.equals(old));
    }
}
