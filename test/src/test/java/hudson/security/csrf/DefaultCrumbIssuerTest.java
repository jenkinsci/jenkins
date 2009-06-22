/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package hudson.security.csrf;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 *
 * @author dty
 */
public class DefaultCrumbIssuerTest extends HudsonTestCase {
    protected void setUp() throws Exception {
        super.setUp();
        assertNotNull(hudson);
        CrumbIssuerDescriptor<CrumbIssuer> descriptor = (CrumbIssuerDescriptor<CrumbIssuer>)hudson.getDescriptor(DefaultCrumbIssuer.class);
        assertNotNull(descriptor);
        CrumbIssuer issuer = descriptor.newInstance(null,null);
        assertNotNull(issuer);
        hudson.setCrumbIssuer(issuer);
    }

    private static final String[] testData = {
        "10.2.3.1",
        "10.2.3.1,10.20.30.40",
        "10.2.3.1,10.20.30.41",
        "10.2.3.3,10.20.30.40,10.20.30.41"
    };
    private static final String HEADER_NAME = "X-Forwarded-For";

    @Bug(3854)
    public void testClientIPFromHeader() throws Exception {
        WebClient wc = new WebClient();

        wc.addRequestHeader(HEADER_NAME, testData[0]);
        HtmlPage p = wc.goTo("configure");
        submit(p.getFormByName("config"));
    }

    @Bug(3854)
    public void testHeaderChange() throws Exception {
        WebClient wc = new WebClient();

        wc.addRequestHeader(HEADER_NAME, testData[0]);
        HtmlPage p = wc.goTo("configure");

        wc.removeRequestHeader(HEADER_NAME);
        try {
            // The crumb should no longer match if we remove the proxy info
            submit(p.getFormByName("config"));
        }
        catch (FailingHttpStatusCodeException e) {
            assertEquals(403,e.getStatusCode());
        }
    }

    @Bug(3854)
    public void testProxyIPChanged() throws Exception {
        WebClient wc = new WebClient();

        wc.addRequestHeader(HEADER_NAME, testData[1]);
        HtmlPage p = wc.goTo("configure");

        wc.removeRequestHeader(HEADER_NAME);
        wc.addRequestHeader(HEADER_NAME, testData[2]);

        // The crumb should be the same even if the proxy IP changes
        submit(p.getFormByName("config"));
    }

    @Bug(3854)
    public void testProxyIPChain() throws Exception {
        WebClient wc = new WebClient();

        wc.addRequestHeader(HEADER_NAME, testData[3]);
        HtmlPage p = wc.goTo("configure");
        submit(p.getFormByName("config"));
    }
}
