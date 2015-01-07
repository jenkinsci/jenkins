/**
 * Copyright (c) 2008-2010 Yahoo! Inc.
 * All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.security.csrf;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import java.net.HttpURLConnection;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.recipes.PresetData;

/**
 *
 * @author dty
 */
public class DefaultCrumbIssuerTest {
    
    @Rule public JenkinsRule r = new JenkinsRule();

    @Before public void setIssuer() {
        r.jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));
    }

    private static final String[] testData = {
        "10.2.3.1",
        "10.2.3.1,10.20.30.40",
        "10.2.3.1,10.20.30.41",
        "10.2.3.3,10.20.30.40,10.20.30.41"
    };
    private static final String HEADER_NAME = "X-Forwarded-For";

    @Issue("JENKINS-3854")
    @Test public void clientIPFromHeader() throws Exception {
        WebClient wc = r.createWebClient();

        wc.addRequestHeader(HEADER_NAME, testData[0]);
        HtmlPage p = wc.goTo("configure");
        r.submit(p.getFormByName("config"));
    }

    @Issue("JENKINS-3854")
    @Test public void headerChange() throws Exception {
        WebClient wc = r.createWebClient();

        wc.addRequestHeader(HEADER_NAME, testData[0]);
        HtmlPage p = wc.goTo("configure");

        wc.removeRequestHeader(HEADER_NAME);
        try {
            // The crumb should no longer match if we remove the proxy info
            r.submit(p.getFormByName("config"));
        }
        catch (FailingHttpStatusCodeException e) {
            assertEquals(403,e.getStatusCode());
        }
    }

    @Issue("JENKINS-3854")
    @Test public void proxyIPChanged() throws Exception {
        WebClient wc = r.createWebClient();

        wc.addRequestHeader(HEADER_NAME, testData[1]);
        HtmlPage p = wc.goTo("configure");

        wc.removeRequestHeader(HEADER_NAME);
        wc.addRequestHeader(HEADER_NAME, testData[2]);

        // The crumb should be the same even if the proxy IP changes
        r.submit(p.getFormByName("config"));
    }

    @Issue("JENKINS-3854")
    @Test public void proxyIPChain() throws Exception {
        WebClient wc = r.createWebClient();

        wc.addRequestHeader(HEADER_NAME, testData[3]);
        HtmlPage p = wc.goTo("configure");
        r.submit(p.getFormByName("config"));
    }

    @Issue("JENKINS-7518")
    @Test public void proxyCompatibilityMode() throws Exception {
        CrumbIssuer issuer = new DefaultCrumbIssuer(true);
        assertNotNull(issuer);
        r.jenkins.setCrumbIssuer(issuer);

        WebClient wc = r.createWebClient();
        wc.addRequestHeader(HEADER_NAME, testData[0]);
        HtmlPage p = wc.goTo("configure");

        wc.removeRequestHeader(HEADER_NAME);
        // The crumb should still match if we remove the proxy info
        r.submit(p.getFormByName("config"));
   }

    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY)
    @Test public void apiXml() throws Exception {
        WebClient wc = r.createWebClient();
        r.assertXPathValue(wc.goToXml("crumbIssuer/api/xml"), "//crumbRequestField", r.jenkins.getCrumbIssuer().getCrumbRequestField());
        String text = wc.goTo("crumbIssuer/api/xml?xpath=concat(//crumbRequestField,'=',//crumb)", "text/plain").getWebResponse().getContentAsString();
        assertTrue(text, text.matches("\\Q" + r.jenkins.getCrumbIssuer().getCrumbRequestField() + "\\E=[0-9a-f]+"));
        text = wc.goTo("crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\":\",//crumb)", "text/plain").getWebResponse().getContentAsString();
        assertTrue(text, text.matches("\\Q" + r.jenkins.getCrumbIssuer().getCrumbRequestField() + "\\E:[0-9a-f]+"));
        text = wc.goTo("crumbIssuer/api/xml?xpath=/*/crumbRequestField/text()", "text/plain").getWebResponse().getContentAsString();
        assertEquals(r.jenkins.getCrumbIssuer().getCrumbRequestField(), text);
        text = wc.goTo("crumbIssuer/api/xml?xpath=/*/crumb/text()", "text/plain").getWebResponse().getContentAsString();
        assertTrue(text, text.matches("[0-9a-f]+"));
        wc.assertFails("crumbIssuer/api/xml?xpath=concat('hack=\"',//crumb,'\"')", HttpURLConnection.HTTP_FORBIDDEN);
        wc.assertFails("crumbIssuer/api/xml?xpath=concat(\"hack='\",//crumb,\"'\")", HttpURLConnection.HTTP_FORBIDDEN);
        wc.assertFails("crumbIssuer/api/xml?xpath=concat('{',//crumb,':1}')", HttpURLConnection.HTTP_FORBIDDEN); // 37.5% chance that crumb ~ /[a-f].+/
        wc.assertFails("crumbIssuer/api/xml?xpath=concat('hack.',//crumb,'=1')", HttpURLConnection.HTTP_FORBIDDEN); // ditto
        r.jenkins.getCrumbIssuer().getDescriptor().setCrumbRequestField("_crumb");
        wc.assertFails("crumbIssuer/api/xml?xpath=concat(//crumbRequestField,'=',//crumb)", HttpURLConnection.HTTP_FORBIDDEN); // perhaps interpretable as JS number
    }

    @PresetData(PresetData.DataSet.ANONYMOUS_READONLY)
    @Test public void apiJson() throws Exception {
        WebClient wc = r.createWebClient();
        String json = wc.goTo("crumbIssuer/api/json", "application/json").getWebResponse().getContentAsString();
        assertTrue(json, json.matches("\\Q{\"crumb\":\"\\E[0-9a-f]+\\Q\",\"crumbRequestField\":\"" + r.jenkins.getCrumbIssuer().getCrumbRequestField() + "\"}\\E"));
        wc.assertFails("crumbIssuer/api/json?jsonp=hack", HttpURLConnection.HTTP_FORBIDDEN);
    }

}
