/*
 * Copyright (c) 2008-2010 Yahoo! Inc.
 * All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.security.csrf;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import hudson.model.User;
import jakarta.servlet.http.HttpServletResponse;
import java.net.HttpURLConnection;
import java.net.URI;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlHiddenInput;
import org.htmlunit.html.HtmlPage;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.recipes.WithTimeout;

/**
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
        "10.2.3.3,10.20.30.40,10.20.30.41",
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

        wc.setThrowExceptionOnFailingStatusCode(false);
        // The crumb should no longer match if we remove the proxy info
        Page page = r.submit(p.getFormByName("config"));
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, page.getWebResponse().getStatusCode());
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

    @Test public void apiXml() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().toEveryone()
        );
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

    @Test public void apiJson() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().toEveryone()
        );
        WebClient wc = r.createWebClient();
        String json = wc.goTo("crumbIssuer/api/json", "application/json").getWebResponse().getContentAsString();
        JSONObject jsonObject = JSONObject.fromObject(json);
        assertEquals(r.jenkins.getCrumbIssuer().getCrumbRequestField(), jsonObject.getString("crumbRequestField"));
        assertTrue(jsonObject.getString("crumb").matches("[0-9a-f]+"));
        wc.assertFails("crumbIssuer/api/json?jsonp=hack", HttpURLConnection.HTTP_FORBIDDEN);
    }

    @Issue("JENKINS-34254")
    @Test public void testRequirePostErrorPageCrumb() throws Exception {
        r.jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));
        WebClient wc = r.createWebClient()
                .withThrowExceptionOnFailingStatusCode(false);

        Page page = wc.goTo("quietDown");
        assertEquals("expect HTTP 405 method not allowed",
                HttpURLConnection.HTTP_BAD_METHOD,
                page.getWebResponse().getStatusCode());

        HtmlPage retry = (HtmlPage) wc.getCurrentWindow().getEnclosedPage();
        HtmlPage success = r.submit(retry.getFormByName("retry"));
        assertEquals(HttpURLConnection.HTTP_OK, success.getWebResponse().getStatusCode());
        assertTrue("quieting down", r.jenkins.isQuietingDown());
    }

    @Test
    @Issue("SECURITY-626")
    @WithTimeout(300)
    public void crumbOnlyValidForOneSession() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        DefaultCrumbIssuer issuer = new DefaultCrumbIssuer(false);
        r.jenkins.setCrumbIssuer(issuer);

        User.getById("foo", true);

        DefaultCrumbIssuer.EXCLUDE_SESSION_ID = true;
        compareDifferentSessions_tokenAreEqual(true);

        DefaultCrumbIssuer.EXCLUDE_SESSION_ID = false;
        compareDifferentSessions_tokenAreEqual(false);
    }

    private void compareDifferentSessions_tokenAreEqual(boolean areEqual) throws Exception {
        WebClient wc = r.createWebClient();
        wc.login("foo");

        HtmlPage p = wc.goTo("configure");
        String crumb1 = p.getElementByName("Jenkins-Crumb").getAttribute("value");
        r.submit(p.getFormByName("config"));

        wc.goTo("logout");
        wc.login("foo");

        p = wc.goTo("configure");
        String crumb2 = p.getElementByName("Jenkins-Crumb").getAttribute("value");
        r.submit(p.getFormByName("config"));

        assertEquals(crumb1.equals(crumb2), areEqual);

        HtmlForm config = p.getFormByName("config");
        if (areEqual) {
            r.submit(config);
        } else {
            replaceAllCrumbInPageBy(p, crumb1);
            // submit the form with previous session crumb
            FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> r.submit(config));
            assertEquals(HttpServletResponse.SC_FORBIDDEN, e.getStatusCode());
            assertThat(e.getResponse().getContentAsString(), containsString("No valid crumb"));
        }
    }

    private void replaceAllCrumbInPageBy(HtmlPage page, String newCrumb) {
        for (DomElement el : page.getElementsByName("Jenkins-Crumb")) {
            ((HtmlHiddenInput) el).setValue(newCrumb);
        }
    }


    @Test
    @Issue("SECURITY-1491")
    public void sessionIncludedEvenForAnonymousCall() throws Exception {
        boolean previousValue = DefaultCrumbIssuer.EXCLUDE_SESSION_ID;

        try {
            r.jenkins.setSecurityRealm(r.createDummySecurityRealm());

            // let anonymous user have read access
            MockAuthorizationStrategy authorizationStrategy = new MockAuthorizationStrategy();
            authorizationStrategy.grant(Jenkins.ADMINISTER).everywhere().toEveryone();
            r.jenkins.setAuthorizationStrategy(authorizationStrategy);

            DefaultCrumbIssuer issuer = new DefaultCrumbIssuer(true);
            r.jenkins.setCrumbIssuer(issuer);

            DefaultCrumbIssuer.EXCLUDE_SESSION_ID = true;
            sameCrumbUsedOnDifferentAnonymousRequest_tokenAreEqual(true, "job_noSession");

            DefaultCrumbIssuer.EXCLUDE_SESSION_ID = false;
            sameCrumbUsedOnDifferentAnonymousRequest_tokenAreEqual(false, "job_session");
        } finally {
            DefaultCrumbIssuer.EXCLUDE_SESSION_ID = previousValue;
        }
    }

    private void sameCrumbUsedOnDifferentAnonymousRequest_tokenAreEqual(boolean areEqual, String namePrefix) throws Exception {
        String responseForCrumb = r.createWebClient().goTo("crumbIssuer/api/xml?xpath=concat(//crumbRequestField,'=',//crumb)", "text/plain")
                .getWebResponse().getContentAsString();
        // responseForCrumb = Jenkins-Crumb=xxxx
        String crumb1 = responseForCrumb.substring(CrumbIssuer.DEFAULT_CRUMB_NAME.length() + "=".length());

        String jobName1 = namePrefix + "-test1";
        String jobName2 = namePrefix + "-test2";

        WebRequest request1 = createRequestForJobCreation(jobName1);
        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> r.createWebClient().getPage(request1));
        assertEquals(HttpServletResponse.SC_FORBIDDEN, e.getStatusCode());
        assertThat(e.getResponse().getContentAsString(), containsString("No valid crumb"));
        // cannot create new job due to missing crumb
        assertNull(r.jenkins.getItem(jobName1));

        WebRequest request2 = createRequestForJobCreation(jobName2);
        request2.setAdditionalHeader(CrumbIssuer.DEFAULT_CRUMB_NAME, crumb1);
        if (areEqual) {
            r.createWebClient().getPage(request2);

            assertNotNull(r.jenkins.getItem(jobName2));
        } else {
            e = assertThrows(
                    "Should have failed due to invalid crumb",
                    FailingHttpStatusCodeException.class,
                    () -> r.createWebClient().getPage(request2));
            assertEquals(HttpURLConnection.HTTP_FORBIDDEN, e.getStatusCode());
            // cannot create new job due to invalid crumb
            assertNull(r.jenkins.getItem(jobName2));
        }
    }

    @Test
    @Issue("SECURITY-1491")
    public void twoRequestsWithoutSessionGetDifferentCrumbs() throws Exception {
        String responseForCrumb = r.createWebClient().goTo("crumbIssuer/api/xml?xpath=concat(//crumbRequestField,'=',//crumb)", "text/plain")
                .getWebResponse().getContentAsString();
        // responseForCrumb = Jenkins-Crumb=xxxx
        String crumb1 = responseForCrumb.substring(CrumbIssuer.DEFAULT_CRUMB_NAME.length() + "=".length());

        responseForCrumb = r.createWebClient().goTo("crumbIssuer/api/xml?xpath=concat(//crumbRequestField,'=',//crumb)", "text/plain")
                .getWebResponse().getContentAsString();
        // responseForCrumb = Jenkins-Crumb=xxxx
        String crumb2 = responseForCrumb.substring(CrumbIssuer.DEFAULT_CRUMB_NAME.length() + "=".length());

        Assert.assertNotEquals("should be different crumbs", crumb1, crumb2);
    }

    private WebRequest createRequestForJobCreation(String jobName) throws Exception {
        WebRequest req = new WebRequest(new URI(r.getURL() + "createItem?name=" + jobName).toURL(), HttpMethod.POST);
        req.setAdditionalHeader("Content-Type", "application/xml");
        req.setRequestBody("<project/>");
        return req;
    }

    @Test
    public void anonCanStillPostRequestUsingBrowsers() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());

        MockAuthorizationStrategy authorizationStrategy = new MockAuthorizationStrategy();
        authorizationStrategy.grant(Jenkins.ADMINISTER).everywhere().toEveryone();
        r.jenkins.setAuthorizationStrategy(authorizationStrategy);

        DefaultCrumbIssuer issuer = new DefaultCrumbIssuer(true);
        r.jenkins.setCrumbIssuer(issuer);

        HtmlPage p = r.createWebClient().goTo("configure");
        r.submit(p.getFormByName("config"));
    }

    @Test
    @Issue("SECURITY-1704")
    public void custom_notExposedToIFrame() throws Exception {
        ensureXmlIsNotExposedToIFrame("crumbIssuer/");
        ensureJsonIsNotExposedToIFrame("crumbIssuer/");
        ensurePythonIsNotExposedToIFrame("crumbIssuer/");
    }

    private void ensureXmlIsNotExposedToIFrame(String itemUrl) throws Exception {
        WebResponse response = r.createWebClient().goTo(itemUrl + "api/xml", "application/xml").getWebResponse();
        assertThat(response.getResponseHeaderValue("X-Frame-Options"), equalTo("deny"));
    }

    private void ensureJsonIsNotExposedToIFrame(String itemUrl) throws Exception {
        WebResponse response = r.createWebClient().goTo(itemUrl + "api/json", "application/json").getWebResponse();
        assertThat(response.getResponseHeaderValue("X-Frame-Options"), equalTo("deny"));
    }

    private void ensurePythonIsNotExposedToIFrame(String itemUrl) throws Exception {
        WebResponse response = r.createWebClient().goTo(itemUrl + "api/python", "text/x-python").getWebResponse();
        assertThat(response.getResponseHeaderValue("X-Frame-Options"), equalTo("deny"));
    }
}
