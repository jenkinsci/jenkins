package jenkins.security;

import com.gargoylesoftware.htmlunit.HttpWebConnection;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.Util;
import hudson.model.User;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScheme;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.auth.CredentialsNotAvailableException;
import org.apache.commons.httpclient.auth.CredentialsProvider;
import org.jvnet.hudson.test.HudsonTestCase;

import java.util.concurrent.Callable;
import javax.annotation.Nonnull;
import org.jvnet.hudson.test.Issue;

/**
 * @author Kohsuke Kawaguchi
 */
public class ApiTokenPropertyTest extends HudsonTestCase {
    /**
     * Tests the UI interaction and authentication.
     */
    public void testBasics() throws Exception {
        jenkins.setSecurityRealm(createDummySecurityRealm());
        User u = User.get("foo");
        final ApiTokenProperty t = u.getProperty(ApiTokenProperty.class);
        final String token = t.getApiToken();

        // Make sure that user is able to get the token via the interface
        ACL.impersonate(u.impersonate(), new Runnable() {
            @Override
            public void run() {
                assertEquals("User is unable to get its own token", token, t.getApiToken());
            }
        });

        // test the authentication via Token
        WebClient wc = createClientForUser("foo");
        assertEquals(u,wc.executeOnServer(new Callable<User>() {
            public User call() throws Exception {
                return User.current();
            }
        }));
        
        // Make sure the UI shows the token to the user
        HtmlPage config = wc.goTo(u.getUrl() + "/configure");
        HtmlForm form = config.getFormByName("config");
        assertEquals(token, form.getInputByName("_.apiToken").getValueAttribute());

        // round-trip shouldn't change the API token
        submit(form);
        assertSame(t, u.getProperty(ApiTokenProperty.class));
    }

    public void testSecurity49Upgrade() throws Exception {
        jenkins.setSecurityRealm(createDummySecurityRealm());
        User u = User.get("foo");
        String historicalInitialValue = Util.getDigestOf(Jenkins.getInstance().getSecretKey() + ":" + u.getId());

        // we won't accept historically used initial value as it may be compromised
        ApiTokenProperty t = new ApiTokenProperty(historicalInitialValue);
        u.addProperty(t);
        String apiToken1 = t.getApiToken();
        assertFalse(apiToken1.equals(Util.getDigestOf(historicalInitialValue)));

        // the replacement for the compromised value must be consistent and cannot be random
        ApiTokenProperty t2 = new ApiTokenProperty(historicalInitialValue);
        u.addProperty(t2);
        assertEquals(apiToken1,t2.getApiToken());

        // any other value is OK. those are changed values
        t = new ApiTokenProperty(historicalInitialValue+"somethingElse");
        u.addProperty(t);
        assertTrue(t.getApiToken().equals(Util.getDigestOf(historicalInitialValue+"somethingElse")));

    }
    
    @Issue("SECURITY-200")
    public void testAdminsShouldBeUnableToSeeTokensByDefault() throws Exception {
        jenkins.setSecurityRealm(createDummySecurityRealm());
        User u = User.get("foo");
        final ApiTokenProperty t = u.getProperty(ApiTokenProperty.class);
        final String token = t.getApiToken();
        
        // Make sure the UI does not show the token to another user
        WebClient wc = createClientForUser("bar");
        HtmlPage config = wc.goTo(u.getUrl() + "/configure");
        HtmlForm form = config.getFormByName("config");
        assertEquals(Messages.ApiTokenProperty_ChangeToken_TokenIsHidden(), form.getInputByName("_.apiToken").getValueAttribute());
    }
    
    @Issue("SECURITY-200")
    public void testAdminsShouldBeUnableToChangeTokensByDefault() throws Exception {
        jenkins.setSecurityRealm(createDummySecurityRealm());
        User foo = User.get("foo");
        User bar = User.get("bar");
        final ApiTokenProperty t = foo.getProperty(ApiTokenProperty.class);
        final ApiTokenProperty.DescriptorImpl descriptor = (ApiTokenProperty.DescriptorImpl) t.getDescriptor();
        
        // Make sure that Admin can reset a token of another user
        WebClient wc = createClientForUser("bar");
        HtmlPage res = wc.goTo(foo.getUrl() + "/" + descriptor.getDescriptorUrl()+ "/changeToken");
        assertEquals("Update token response is incorrect", 
                Messages.ApiTokenProperty_ChangeToken_SuccessHidden(), "<div>" + res.getBody().asText() + "</div>");
    }
    
    @Nonnull
    private WebClient createClientForUser(final String username) {
        User u = User.get(username);
        final ApiTokenProperty t = u.getProperty(ApiTokenProperty.class);
        // Yes, we use the insecure call in the test stuff
        final String token = t.getApiTokenInsecure();
        
        WebClient wc = createWebClient();
        wc.setCredentialsProvider(new CredentialsProvider() {
            @Override
            public Credentials getCredentials(AuthScheme scheme, String host, int port, boolean proxy) 
                    throws CredentialsNotAvailableException {
                return new UsernamePasswordCredentials(username, token);
            }
        });
        wc.setWebConnection(new HttpWebConnection(wc) {
            @Override
            protected HttpClient getHttpClient() {
                HttpClient c = super.getHttpClient();
                c.getParams().setAuthenticationPreemptive(true);
                c.getState().setCredentials(new AuthScope("localhost", localPort, AuthScope.ANY_REALM), 
                        new UsernamePasswordCredentials(username, token));
                return c;
            }
        });
        return wc;
    }
}
