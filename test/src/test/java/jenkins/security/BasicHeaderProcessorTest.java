package jenkins.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import hudson.model.UnprotectedRootAction;
import hudson.model.User;
import hudson.util.HttpResponses;
import hudson.util.Scrambler;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.HttpResponse;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URL;

/**
 * @author Kohsuke Kawaguchi
 */
public class BasicHeaderProcessorTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private WebClient wc;

    /**
     * Tests various ways to send the Basic auth.
     */
    @Test
    public void testVariousWaysToCall() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User foo = User.get("foo");
        User bar = User.get("bar");

        wc = j.createWebClient();

        // call without authentication
        makeRequestWithAuthAndVerify(null, "anonymous");

        // call with API token
        ApiTokenProperty t = foo.getProperty(ApiTokenProperty.class);
        final String token = t.getApiToken();
        makeRequestWithAuthAndVerify("foo:"+token, "foo");

        // call with invalid API token
        makeRequestAndFail("foo:abcd"+token);

        // call with password
        makeRequestWithAuthAndVerify("foo:foo", "foo");

        // call with incorrect password
        makeRequestAndFail("foo:bar");


        wc.login("bar");

        // if the session cookie is valid, then basic header won't be needed
        makeRequestWithAuthAndVerify(null, "bar");

        // if the session cookie is valid, and basic header is set anyway login should not fail either
        makeRequestWithAuthAndVerify("bar:bar", "bar");

        // but if the password is incorrect, it should fail, instead of silently logging in as the user indicated by session
        makeRequestAndFail("foo:bar");
    }

    private void makeRequestAndFail(String userAndPass) throws IOException, SAXException {
        try {
            makeRequestWithAuthAndVerify(userAndPass, "-");
            fail();
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(401, e.getStatusCode());
        }
    }

    private void makeRequestWithAuthAndVerify(String userAndPass, String username) throws IOException, SAXException {
        String authCode = "Basic "+Scrambler.scramble(userAndPass);
        doMakeRequestWithAuthAndVerify(authCode, username);
    }

    private void doMakeRequestWithAuthAndVerify(String authCode, String expected) throws IOException, SAXException {
        WebRequest req = new WebRequest(new URL(j.getURL(),"test"));
        req.setEncodingType(null);
        if (authCode!=null)
            req.setAdditionalHeader("Authorization", authCode);
        Page p = wc.getPage(req);
        assertEquals(expected, p.getWebResponse().getContentAsString().trim());
    }

    @Test
    public void testAuthHeaderCaseInSensitive() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        wc = j.createWebClient();

        String[][] data = new String[][]{
                {"Basic", "foo", "foo"},
                {"BASIC", "foo", "foo"},
                {"basic", "foo", "foo"},
                {"Basic", "bar", "foo"},
                {"BASIC", "bar", "foo"},
                {"basic", "bar", "foo"},
        };
        for (String[] testCase : data) {
            String basicVar = testCase[0];
            String username = testCase[1];
            String password = testCase[2];
            String authCode = basicVar+" "+Scrambler.scramble(username+":"+password);
            doMakeRequestWithAuthAndVerify(authCode, username);
        }
    }

    @TestExtension
    public static class WhoAmI implements UnprotectedRootAction {
        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return "test";
        }

        public HttpResponse doIndex() {
            User u = User.current();
            return HttpResponses.plainText(u!=null ? u.getId() : "anonymous");
        }
    }
}
