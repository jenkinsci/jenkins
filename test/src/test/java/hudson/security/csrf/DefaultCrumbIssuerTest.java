/**
 * Copyright (c) 2008-2010 Yahoo! Inc.
 * All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.security.csrf;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import java.net.HttpURLConnection;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 *
 * @author dty
 */
public class DefaultCrumbIssuerTest extends HudsonTestCase {
    protected void setUp() throws Exception {
        super.setUp();
        assertNotNull(jenkins);
        CrumbIssuer issuer = new DefaultCrumbIssuer(false);
        assertNotNull(issuer);
        jenkins.setCrumbIssuer(issuer);
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

    @Bug(7518)
    public void testProxyCompatibilityMode() throws Exception {
        CrumbIssuer issuer = new DefaultCrumbIssuer(true);
        assertNotNull(issuer);
        jenkins.setCrumbIssuer(issuer);

        WebClient wc = new WebClient();
        wc.addRequestHeader(HEADER_NAME, testData[0]);
        HtmlPage p = wc.goTo("configure");

        wc.removeRequestHeader(HEADER_NAME);
        // The crumb should still match if we remove the proxy info
        submit(p.getFormByName("config"));
   }

    public void testApiXml() throws Exception {
        WebClient wc = new WebClient();
        assertXPathValue(wc.goToXml("crumbIssuer/api/xml"), "//crumbRequestField", jenkins.getCrumbIssuer().getCrumbRequestField());
        String text = wc.goTo("crumbIssuer/api/xml?xpath=concat(//crumbRequestField,'=',//crumb)", "text/plain").getWebResponse().getContentAsString();
        assertTrue(text, text.matches("\\Q" + jenkins.getCrumbIssuer().getCrumbRequestField() + "\\E=[0-9a-f]+"));
        text = wc.goTo("crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\":\",//crumb)", "text/plain").getWebResponse().getContentAsString();
        assertTrue(text, text.matches("\\Q" + jenkins.getCrumbIssuer().getCrumbRequestField() + "\\E:[0-9a-f]+"));
        text = wc.goTo("crumbIssuer/api/xml?xpath=/*/crumbRequestField/text()", "text/plain").getWebResponse().getContentAsString();
        assertEquals(jenkins.getCrumbIssuer().getCrumbRequestField(), text);
        text = wc.goTo("crumbIssuer/api/xml?xpath=/*/crumb/text()", "text/plain").getWebResponse().getContentAsString();
        assertTrue(text, text.matches("[0-9a-f]+"));
        wc.assertFails("crumbIssuer/api/xml?xpath=concat('hack=\"',//crumb,'\"')", HttpURLConnection.HTTP_FORBIDDEN);
        wc.assertFails("crumbIssuer/api/xml?xpath=concat(\"hack='\",//crumb,\"'\")", HttpURLConnection.HTTP_FORBIDDEN);
        wc.assertFails("crumbIssuer/api/xml?xpath=concat('{',//crumb,':1}')", HttpURLConnection.HTTP_FORBIDDEN); // 37.5% chance that crumb ~ /[a-f].+/
        wc.assertFails("crumbIssuer/api/xml?xpath=concat('hack.',//crumb,'=1')", HttpURLConnection.HTTP_FORBIDDEN); // ditto
        jenkins.getCrumbIssuer().getDescriptor().setCrumbRequestField("_crumb");
        wc.assertFails("crumbIssuer/api/xml?xpath=concat(//crumbRequestField,'=',//crumb)", HttpURLConnection.HTTP_FORBIDDEN); // perhaps interpretable as JS number
    }

    public void testApiJson() throws Exception {
        WebClient wc = new WebClient();
        String json = wc.goTo("crumbIssuer/api/json", "application/json").getWebResponse().getContentAsString();
        assertTrue(json, json.matches("\\Q{\"crumb\":\"\\E[0-9a-f]+\\Q\",\"crumbRequestField\":\"" + jenkins.getCrumbIssuer().getCrumbRequestField() + "\"}\\E"));
        wc.assertFails("crumbIssuer/api/json?jsonp=hack", HttpURLConnection.HTTP_FORBIDDEN);
    }

}
