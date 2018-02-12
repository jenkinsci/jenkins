package jenkins.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.Util;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.Scrambler;
import java.net.URL;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import java.util.concurrent.Callable;
import javax.annotation.Nonnull;
import org.jvnet.hudson.test.Issue;

/**
 * @author Kohsuke Kawaguchi
 */
public class ApiTokenPropertyTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Tests the UI interaction and authentication.
     */
    @Test
    public void basics() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User u = User.get("foo");
        final ApiTokenProperty t = u.getProperty(ApiTokenProperty.class);
        final String token = t.getApiToken();

        // Make sure that user is able to get the token via the interface
        try (ACLContext _ = ACL.as(u)) {
            assertEquals("User is unable to get its own token", token, t.getApiToken());
        }

        // test the authentication via Token
        WebClient wc = createClientForUser("foo");
        assertEquals(u, wc.executeOnServer(new Callable<User>() {
            public User call() throws Exception {
                return User.current();
            }
        }));
        
        // Make sure the UI shows the token to the user
        HtmlPage config = wc.goTo(u.getUrl() + "/configure");
        HtmlForm form = config.getFormByName("config");
        assertEquals(token, form.getInputByName("_.apiToken").getValueAttribute());

        // round-trip shouldn't change the API token
        j.submit(form);
        assertSame(t, u.getProperty(ApiTokenProperty.class));
    }

    @Test
    public void security49Upgrade() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
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
    @Test
    public void adminsShouldBeUnableToSeeTokensByDefault() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
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
    @Test
    public void adminsShouldBeUnableToChangeTokensByDefault() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User foo = User.get("foo");
        User bar = User.get("bar");
        final ApiTokenProperty t = foo.getProperty(ApiTokenProperty.class);
        final ApiTokenProperty.DescriptorImpl descriptor = (ApiTokenProperty.DescriptorImpl) t.getDescriptor();
        
        // Make sure that Admin can reset a token of another user
        WebClient wc = createClientForUser("bar");
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        HtmlPage requirePOST = wc.goTo(foo.getUrl() + "/" + descriptor.getDescriptorUrl()+ "/changeToken");
        assertEquals("method should not be allowed", 405, requirePOST.getWebResponse().getStatusCode());

        wc.getOptions().setThrowExceptionOnFailingStatusCode(true);
        WebRequest request = new WebRequest(new URL(j.getURL().toString() + foo.getUrl() + "/" + descriptor.getDescriptorUrl()+ "/changeToken"), HttpMethod.POST);
        HtmlPage res = wc.getPage(request);

        // TODO This nicer alternative requires https://github.com/jenkinsci/jenkins/pull/2268 or similar to work
//        HtmlPage res = requirePOST.getPage().getForms().get(0).getElementsByAttribute("input", "type", "submit").get(0).click();
        assertEquals("Update token response is incorrect", 
                Messages.ApiTokenProperty_ChangeToken_SuccessHidden(), "<div>" + res.getBody().asText() + "</div>");
    }
    
    @Nonnull
    private WebClient createClientForUser(final String username) throws Exception {
        User u = User.get(username);
        final ApiTokenProperty t = u.getProperty(ApiTokenProperty.class);
        // Yes, we use the insecure call in the test stuff
        final String token = t.getApiTokenInsecure();
        
        WebClient wc = j.createWebClient();
        wc.addRequestHeader("Authorization", "Basic " + Scrambler.scramble(username + ":" + token));
        return wc;
    }

}
