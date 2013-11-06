package hudson.security;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput;

import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.PresetData;
import org.jvnet.hudson.test.recipes.PresetData.DataSet;
import org.xml.sax.SAXException;

import static org.acegisecurity.ui.rememberme.TokenBasedRememberMeServices.ACEGI_SECURITY_HASHED_REMEMBER_ME_COOKIE_KEY;

import java.io.IOException;
import java.net.URL;

import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

/**
 * @author Kohsuke Kawaguchi
 */
public class LoginTest extends HudsonTestCase {
    /**
     * Requesting a loginError page directly should result in a redirect,
     * on a non-secured Hudson.
     */
    public void testLoginErrorRedirect() throws Exception {
        verifyNotError(createWebClient());
    }

    private void verifyNotError(WebClient wc) throws IOException, SAXException {
        HtmlPage p = wc.goTo("loginError");
        URL url = p.getWebResponse().getUrl();
        System.out.println(url);
        assertFalse(url.toExternalForm().contains("login"));
    }

    /**
     * Same as {@link #testLoginErrorRedirect()} if the user has already successfully authenticated.
     */
    @PresetData(DataSet.ANONYMOUS_READONLY)
    public void testLoginErrorRedirect2() throws Exception {
        // in a secured Hudson, the error page should render.
        WebClient wc = createWebClient();
        wc.assertFails("loginError", SC_UNAUTHORIZED);
        // but not once the user logs in.
        verifyNotError(wc.login("alice"));
    }

    private HtmlForm prepareLoginFormWithRememberMeChecked(WebClient wc) throws IOException, org.xml.sax.SAXException {
        wc.getCookieManager().setCookiesEnabled(true);
        HtmlPage page = wc.goTo("login");

        HtmlForm form = page.getFormByName("login");
        form.getInputByName("j_username").setValueAttribute("alice");
        form.getInputByName("j_password").setValueAttribute("alice");
        ((HtmlCheckBoxInput)form.getInputByName("remember_me")).setChecked(true);
        return form;
    }

    /**
     * Returns the 'remember me' cookie if set, otherwise return null. We don't care about the type, only whether it's null
     */
    private Object getRememberMeCookie(WebClient wc) {
        return wc.getCookieManager().getCookie(ACEGI_SECURITY_HASHED_REMEMBER_ME_COOKIE_KEY);
    }

    /**
     * Test 'remember me' cookie
     */
    @PresetData(DataSet.SECURED_ACEGI)
    public void testLoginRememberMe() throws Exception {
        WebClient wc = createWebClient();

        prepareLoginFormWithRememberMeChecked(wc).submit(null);

        assertNotNull(getRememberMeCookie(wc));
    }


    /**
     * Test that 'remember me' cookie will not be set if disabled even if requested by user.
     * This models the case when the feature is disabled between another user loading and submitting the login page.
     */
    @PresetData(DataSet.SECURED_ACEGI)
    public void testLoginDisabledRememberMe() throws Exception {
        WebClient wc = createWebClient();

        HtmlForm form = prepareLoginFormWithRememberMeChecked(wc);
        jenkins.setDisableRememberMe(true);
        form.submit(null);

        assertNull(getRememberMeCookie(wc));
    }
}
