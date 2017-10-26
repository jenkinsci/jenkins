package jenkins.security;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import hudson.ExtensionList;
import hudson.model.UnprotectedRootAction;
import hudson.model.User;
import hudson.util.HttpResponses;
import hudson.util.Scrambler;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.HttpResponse;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URL;

import static org.junit.Assert.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class BasicHeaderProcessorTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private WebClient wc;

    private SpySecurityListener spySecurityListener;

    @Before
    public void prepareListeners(){
        //TODO simplify using #3021 into ExtensionList.lookupSingleton(SpySecurityListener.class)
        this.spySecurityListener = ExtensionList.lookup(SecurityListener.class).get(SpySecurityListenerImpl.class);
    }

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
        spySecurityListener.authenticatedCalls.assertNoNewEvents();
        spySecurityListener.failedToAuthenticateCalls.assertNoNewEvents();

        // call with API token
        ApiTokenProperty t = foo.getProperty(ApiTokenProperty.class);
        final String token = t.getApiToken();
        makeRequestWithAuthAndVerify("foo:"+token, "foo");
        //TODO verify why there are two events "authenticated" that are triggered
        // the whole authentication process seems to be done twice
        spySecurityListener.authenticatedCalls.assertLastEventIsAndThenRemoveIt(u -> u.getUsername().equals("foo"));

        // call with invalid API token
        makeRequestAndFail("foo:abcd"+token);
        spySecurityListener.failedToAuthenticateCalls.assertLastEventIsAndThenRemoveIt("foo");

        // call with password
        makeRequestWithAuthAndVerify("foo:foo", "foo");
        spySecurityListener.authenticatedCalls.assertLastEventIsAndThenRemoveIt(u -> u.getUsername().equals("foo"));

        // call with incorrect password
        makeRequestAndFail("foo:bar");
        spySecurityListener.failedToAuthenticateCalls.assertLastEventIsAndThenRemoveIt("foo");


        wc.login("bar");
        spySecurityListener.authenticatedCalls.assertLastEventIsAndThenRemoveIt(u -> u.getUsername().equals("bar"));
        spySecurityListener.loggedInCalls.assertLastEventIsAndThenRemoveIt("bar");

        // if the session cookie is valid, then basic header won't be needed
        makeRequestWithAuthAndVerify(null, "bar");
        spySecurityListener.authenticatedCalls.assertNoNewEvents();
        spySecurityListener.failedToAuthenticateCalls.assertNoNewEvents();

        // if the session cookie is valid, and basic header is set anyway login should not fail either
        makeRequestWithAuthAndVerify("bar:bar", "bar");
        spySecurityListener.authenticatedCalls.assertNoNewEvents();
        spySecurityListener.failedToAuthenticateCalls.assertNoNewEvents();

        // but if the password is incorrect, it should fail, instead of silently logging in as the user indicated by session
        makeRequestAndFail("foo:bar");
        spySecurityListener.failedToAuthenticateCalls.assertLastEventIsAndThenRemoveIt("foo");
    }

    private void makeRequestAndFail(String userAndPass) throws IOException, SAXException {
        makeRequestWithAuthCodeAndFail(encode("Basic", userAndPass));
    }
    
    private String encode(String prefix, String userAndPass) {
        if (userAndPass==null) {
            return null;
        }
        return prefix+" "+Scrambler.scramble(userAndPass);
    }

    private void makeRequestWithAuthCodeAndFail(String authCode) throws IOException, SAXException {
        try {
            makeRequestWithAuthCodeAndVerify(authCode, "-");
            fail();
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(401, e.getStatusCode());
        }
    }

    private void makeRequestWithAuthAndVerify(String userAndPass, String username) throws IOException, SAXException {
        makeRequestWithAuthCodeAndVerify(encode("Basic", userAndPass), username);
    }

    private void makeRequestWithAuthCodeAndVerify(String authCode, String expected) throws IOException, SAXException {
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
        User foo = User.get("foo");
        wc = j.createWebClient();

        String[] basicCandidates = {"Basic", "BASIC", "basic", "bASIC"};
        
        for (String prefix : basicCandidates) {
            // call with API token
            ApiTokenProperty t = foo.getProperty(ApiTokenProperty.class);
            final String token = t.getApiToken();
            String authCode1 = encode(prefix,"foo:"+token);
            makeRequestWithAuthCodeAndVerify(authCode1, "foo");
            spySecurityListener.authenticatedCalls.assertLastEventIsAndThenRemoveIt(u -> u.getUsername().equals("foo"));
            
            // call with invalid API token
            String authCode2 = encode(prefix,"foo:abcd"+token);
            makeRequestWithAuthCodeAndFail(authCode2);
            spySecurityListener.failedToAuthenticateCalls.assertLastEventIsAndThenRemoveIt("foo");

            // call with password
            String authCode3 = encode(prefix,"foo:foo");
            makeRequestWithAuthCodeAndVerify(authCode3, "foo");
            spySecurityListener.authenticatedCalls.assertLastEventIsAndThenRemoveIt(u -> u.getUsername().equals("foo"));

            // call with incorrect password
            String authCode4 = encode(prefix,"foo:bar");
            makeRequestWithAuthCodeAndFail(authCode4);
            spySecurityListener.failedToAuthenticateCalls.assertLastEventIsAndThenRemoveIt("foo");
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

    @TestExtension
    public static class SpySecurityListenerImpl extends SpySecurityListener {}
}
