package hudson.security;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.PresetData;
import org.jvnet.hudson.test.recipes.PresetData.DataSet;
import org.xml.sax.SAXException;

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
}
