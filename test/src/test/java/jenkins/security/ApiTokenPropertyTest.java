package jenkins.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.Util;
import hudson.model.User;
import jenkins.model.Jenkins;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import java.util.concurrent.Callable;

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
        ApiTokenProperty t = u.getProperty(ApiTokenProperty.class);
        final String token = t.getApiToken();

        // make sure the UI shows the token
        HtmlPage config = j.createWebClient().goTo(u.getUrl() + "/configure");
        HtmlForm form = config.getFormByName("config");
        assertEquals(token, form.getInputByName("_.apiToken").getValueAttribute());

        // round-trip shouldn't change the API token
        j.submit(form);
        assertSame(t, u.getProperty(ApiTokenProperty.class));

        WebClient wc = j.createWebClient();
        wc.getCredentialsProvider().setCredentials(
                new AuthScope("localhost", AuthScope.ANY_PORT),
                new UsernamePasswordCredentials("foo", token)
        );
// TODO: Find a way to do this.
// May need to upgrade to httpclient 4.3+ by adding a org.apache.httpcomponents:httpclient exclusion on the maven-plugin
// See http://stackoverflow.com/questions/2014700/preemptive-basic-authentication-with-apache-httpclient-4 etc
//        wc.setWebConnection(new HttpWebConnection(wc) {
//            @Override
//            protected HttpClient getHttpClient() {
//                HttpClient c = super.getHttpClient();
//                c.getParams().setAuthenticationPreemptive(true);
//                c.getState().setCredentials(new AuthScope("localhost", AuthScope.ANY_PORT, AuthScope.ANY_REALM), new UsernamePasswordCredentials("foo", token));
//                return c;
//            }
//        });

        // test the authentication
        assertEquals(u,wc.executeOnServer(new Callable<User>() {
            public User call() throws Exception {
                return User.current();
            }
        }));
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
}
