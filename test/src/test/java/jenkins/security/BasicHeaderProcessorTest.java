package jenkins.security;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import hudson.ExtensionList;
import hudson.model.UnprotectedRootAction;
import hudson.model.User;
import hudson.util.HttpResponses;
import hudson.util.Scrambler;
import org.acegisecurity.userdetails.UserDetails;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.HttpResponse;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.Assert.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class BasicHeaderProcessorTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private WebClient wc;

    private DummySecurityListener dummyListener;
    
    @Before
    public void prepareListeners(){
        //TODO simplify using #3021 into ExtensionList.lookupSingleton(DummySecurityListener.class)
        this.dummyListener = ExtensionList.lookup(SecurityListener.class).get(DummySecurityListener.class);
        dummyListener.clearPreviousCalls();
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
        dummyListener.authenticatedCalls.assertNoNewEvents();
        dummyListener.failedToAuthenticateCalls.assertNoNewEvents();

        // call with API token
        ApiTokenProperty t = foo.getProperty(ApiTokenProperty.class);
        final String token = t.getApiToken();
        makeRequestWithAuthAndVerify("foo:"+token, "foo");
        //TODO verify why there are two events "authenticated" that are triggered
        // the whole authentication process seems to be done twice
        dummyListener.authenticatedCalls.assertLastEventIs(u -> u.getUsername().equals("foo"));

        // call with invalid API token
        makeRequestAndFail("foo:abcd"+token);
        dummyListener.failedToAuthenticateCalls.assertLastEventIs("foo");

        // call with password
        makeRequestWithAuthAndVerify("foo:foo", "foo");
        dummyListener.authenticatedCalls.assertLastEventIs(u -> u.getUsername().equals("foo"));

        // call with incorrect password
        makeRequestAndFail("foo:bar");
        dummyListener.failedToAuthenticateCalls.assertLastEventIs("foo");


        wc.login("bar");
        dummyListener.authenticatedCalls.assertLastEventIs(u -> u.getUsername().equals("bar"));
        dummyListener.loggedInCalls.assertLastEventIs("bar");

        // if the session cookie is valid, then basic header won't be needed
        makeRequestWithAuthAndVerify(null, "bar");
        dummyListener.authenticatedCalls.assertNoNewEvents();
        dummyListener.failedToAuthenticateCalls.assertNoNewEvents();

        // if the session cookie is valid, and basic header is set anyway login should not fail either
        makeRequestWithAuthAndVerify("bar:bar", "bar");
        dummyListener.authenticatedCalls.assertNoNewEvents();
        dummyListener.failedToAuthenticateCalls.assertNoNewEvents();

        // but if the password is incorrect, it should fail, instead of silently logging in as the user indicated by session
        makeRequestAndFail("foo:bar");
        dummyListener.failedToAuthenticateCalls.assertLastEventIs("foo");
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
            dummyListener.authenticatedCalls.assertLastEventIs(u -> u.getUsername().equals("foo"));
            
            // call with invalid API token
            String authCode2 = encode(prefix,"foo:abcd"+token);
            makeRequestWithAuthCodeAndFail(authCode2);
            dummyListener.failedToAuthenticateCalls.assertLastEventIs("foo");

            // call with password
            String authCode3 = encode(prefix,"foo:foo");
            makeRequestWithAuthCodeAndVerify(authCode3, "foo");
            dummyListener.authenticatedCalls.assertLastEventIs(u -> u.getUsername().equals("foo"));

            // call with incorrect password
            String authCode4 = encode(prefix,"foo:bar");
            makeRequestWithAuthCodeAndFail(authCode4);
            dummyListener.failedToAuthenticateCalls.assertLastEventIs("foo");
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
    public static class DummySecurityListener extends SecurityListener {
        final EventQueue<UserDetails> authenticatedCalls = new EventQueue<>();
        final EventQueue<String> failedToAuthenticateCalls = new EventQueue<>();
        final EventQueue<String> loggedInCalls = new EventQueue<>();

        void clearPreviousCalls(){
            this.authenticatedCalls.clear();
            this.failedToAuthenticateCalls.clear();
            this.loggedInCalls.clear();
        }
        
        @Override
        protected void authenticated(@Nonnull UserDetails details) {
            this.authenticatedCalls.add(details);
        }

        @Override
        protected void failedToAuthenticate(@Nonnull String username) {
            this.failedToAuthenticateCalls.add(username);
        }

        @Override
        protected void loggedIn(@Nonnull String username) {
            this.loggedInCalls.add(username);
        }

        @Override
        protected void failedToLogIn(@Nonnull String username) {
        
        }
    
        @Override
        protected void loggedOut(@Nonnull String username) {
        
        }
    }
    
    private static class EventQueue<T> {
        private final List<T> eventList = new ArrayList<>();
    
        void assertLastEventIs(T expected){
            assertLastEventIs(actual -> actual.equals(expected));
        }

        void assertLastEventIs(Predicate<T> predicate){
            if(eventList.isEmpty()){
                fail("event list is empty");
            }
        
            T t = eventList.remove(eventList.size() - 1);
            assertTrue(predicate.test(t));
            eventList.clear();
        }
        
        void assertNoNewEvents(){
            assertEquals("list of event should be empty", eventList.size(), 0);
        }
    
        EventQueue add(T t){
            eventList.add(t);
            return this;
        }
        
        void clear(){
            eventList.clear();
        }
    }
}
