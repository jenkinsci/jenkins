package hudson.security;

import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.WithoutJenkins;
import org.jvnet.hudson.test.recipes.LocalData;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import hudson.model.User;
import hudson.security.AuthorizationStrategy;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import static hudson.security.HudsonPrivateSecurityRealm.*;

import jenkins.security.CommitNamesProperty;

/**
 * @author Kohsuke Kawaguchi, Daniel Khodaparast
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

    @WithoutJenkins
    public void testHashCompatibility() {
        String old = CLASSIC.encodePassword("hello world", null);
        assertTrue(PASSWORD_ENCODER.isPasswordValid(old,"hello world",null));

        String secure = PASSWORD_ENCODER.encodePassword("hello world", null);
        assertTrue(PASSWORD_ENCODER.isPasswordValid(old,"hello world",null));

        assertTrue(!secure.equals(old));
    }

    @Test
    public void testUserMappedError() throws Exception {
        hudson.setAuthorizationStrategy(new FullControlOnceLoggedInAuthorizationStrategy());
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false);
        hudson.setSecurityRealm(realm);
        hudson.setUseCommitNames(true);

        User u = realm.createAccount("test1", "test1");
        u.addProperty(new CommitNamesProperty("test2,    test3"));
        u.save();

        WebClient wc = createWebClient();
        wc.login("test1", "test1");

        HtmlPage page1 = wc.goTo("securityRealm/addUser");
        HtmlForm form = page1.getFirstByXPath("//form[@action='/securityRealm/createAccountByAdmin']");

        form.getInputByName("username").setValueAttribute("test3");
        form.getInputByName("password1").setValueAttribute("password3");
        form.getInputByName("password2").setValueAttribute("password3");
        form.getInputByName("fullname").setValueAttribute("Test User3");
        form.getInputByName("email").setValueAttribute("test.user3@example.com");

        HtmlPage page2 = submit(form);
        String pageText = page2.asText();
        assertTrue(pageText.contains("User name is already mapped to test1"));
    }
}