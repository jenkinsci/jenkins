package hudson.security.csrf;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.servlet.http.HttpServletResponse;
import jenkins.model.Jenkins;
import static org.hamcrest.Matchers.*;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

//TODO merge back to DefaultCrumbIssuerTest
public class DefaultCrumbIssuerSEC1491Test {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Before public void setIssuer() {
        r.jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));
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
        try {
            r.createWebClient().getPage(request1);
            fail();
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(HttpServletResponse.SC_FORBIDDEN, e.getStatusCode());
            assertThat(e.getResponse().getContentAsString(), containsString("No valid crumb"));
        }
        // cannot create new job due to missing crumb
        assertNull(r.jenkins.getItem(jobName1));

        WebRequest request2 = createRequestForJobCreation(jobName2);
        request2.setAdditionalHeader(CrumbIssuer.DEFAULT_CRUMB_NAME, crumb1);
        if (areEqual) {
            r.createWebClient().getPage(request2);

            assertNotNull(r.jenkins.getItem(jobName2));
        } else {
            try {
                r.createWebClient().getPage(request2);
                fail("Should have failed due to invalid crumb");
            } catch (FailingHttpStatusCodeException e) {
                assertEquals(HttpURLConnection.HTTP_FORBIDDEN, e.getStatusCode());
                // cannot create new job due to invalid crumb
                assertNull(r.jenkins.getItem(jobName2));
            }
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
        WebRequest req = new WebRequest(new URL(r.getURL() + "createItem?name=" + jobName), HttpMethod.POST);
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
}
