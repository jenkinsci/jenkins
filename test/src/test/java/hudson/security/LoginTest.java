package hudson.security;

import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import hudson.model.User;
import java.io.IOException;
import java.net.URL;
import jenkins.model.Jenkins;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlFormUtil;
import org.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.recipes.PresetData;
import org.jvnet.hudson.test.recipes.PresetData.DataSet;
import org.springframework.security.web.authentication.rememberme.AbstractRememberMeServices;
import org.xml.sax.SAXException;

/**
 * @author Kohsuke Kawaguchi
 */
public class LoginTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Requesting a loginError page directly should result in a redirect,
     * on a non-secured Hudson.
     */
    @Test
    public void loginErrorRedirect1() throws Exception {
        verifyNotError(j.createWebClient());
    }

    private void verifyNotError(WebClient wc) throws IOException, SAXException {
        HtmlPage p = wc.goTo("loginError");
        URL url = p.getUrl();
        System.out.println(url);
        assertFalse(url.toExternalForm().contains("login"));
    }

    /**
     * Same as {@link #loginErrorRedirect1()} if the user has already successfully authenticated.
     */
    @Test
    @PresetData(DataSet.ANONYMOUS_READONLY)
    public void loginErrorRedirect2() throws Exception {
        // in a secured Hudson, the error page should render.
        WebClient wc = j.createWebClient();
        wc.assertFails("loginError", SC_UNAUTHORIZED);
        // but not once the user logs in.
        verifyNotError(wc.withBasicApiToken(User.getById("alice", true)));
    }

    @Test
    public void loginError() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().toAuthenticated());
        WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo("login");
        HtmlForm form = page.getFormByName("login");
        form.getInputByName("j_username").setValue("alice");
        form.getInputByName("j_password").setValue("oops I forgot");
        wc.setThrowExceptionOnFailingStatusCode(false);
        page = (HtmlPage) HtmlFormUtil.submit(form, null);
        assertThat(page.asNormalizedText(), containsString("Invalid username or password"));
    }

    private HtmlForm prepareLoginFormWithRememberMeChecked(WebClient wc) throws IOException, org.xml.sax.SAXException {
        wc.getCookieManager().setCookiesEnabled(true);
        HtmlPage page = wc.goTo("login");

        HtmlForm form = page.getFormByName("login");
        form.getInputByName("j_username").setValue("alice");
        form.getInputByName("j_password").setValue("alice");
        form.getInputByName("remember_me").setChecked(true);
        return form;
    }

    /**
     * Returns the 'remember me' cookie if set, otherwise return null. We don't care about the type, only whether it's null
     */
    private Object getRememberMeCookie(WebClient wc) {
        return wc.getCookieManager().getCookie(AbstractRememberMeServices.SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY);
    }

    /**
     * Test 'remember me' cookie
     */
    @Test
    @PresetData(DataSet.SECURED_ACEGI)
    public void loginRememberMe() throws Exception {
        WebClient wc = j.createWebClient();

        HtmlFormUtil.submit(prepareLoginFormWithRememberMeChecked(wc), null);

        assertNotNull(getRememberMeCookie(wc));
    }

    /**
     * Test that 'remember me' cookie will not be set if disabled even if requested by user.
     * This models the case when the feature is disabled between another user loading and submitting the login page.
     */
    @Test
    @PresetData(DataSet.SECURED_ACEGI)
    public void loginDisabledRememberMe() throws Exception {
        WebClient wc = j.createWebClient();

        HtmlForm form = prepareLoginFormWithRememberMeChecked(wc);
        j.jenkins.setDisableRememberMe(true);
        HtmlFormUtil.submit(form, null);

        assertNull(getRememberMeCookie(wc));
    }
}
