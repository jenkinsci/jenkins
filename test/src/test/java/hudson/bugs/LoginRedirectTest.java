package hudson.bugs;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.PresetData;
import org.jvnet.hudson.test.recipes.PresetData.DataSet;

/**
 * Login redirection ignores the context path
 *
 * @author Kohsuke Kawaguchi
 */
@Bug(2290)
public class LoginRedirectTest extends HudsonTestCase {
    protected void setUp() throws Exception {
        contextPath = "/hudson";
        super.setUp();
    }

    @PresetData(DataSet.NO_ANONYMOUS_READACCESS)
    public void testRedirect() throws Exception {
        WebClient wc = new WebClient();
        // Hudson first causes 403 FORBIDDEN error, then redirect the browser to the page
        wc.setThrowExceptionOnFailingStatusCode(false);

        HtmlPage p = wc.goTo("/");
        System.out.println(p.getDocumentURI());
        assertEquals(200, p.getWebResponse().getStatusCode());
        HtmlForm form = p.getFormByName("login");
        form.getInputByName("j_username").setValueAttribute("alice");
        form.getInputByName("j_password").setValueAttribute("alice");
        p = (HtmlPage) form.submit(null);

        System.out.println(p);
    }

    /**
     * Verifies that Hudson is sending 403 first. This is important for machine agents.
     */
    @PresetData(DataSet.NO_ANONYMOUS_READACCESS)
    public void testRedirect2() throws Exception {
        try {
            new WebClient().goTo("/");
            fail();
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(403,e.getStatusCode());
        }
    }
}
