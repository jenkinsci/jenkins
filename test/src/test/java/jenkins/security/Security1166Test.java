package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlFormUtil;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.NameValuePair;
import org.htmlunit.xml.XmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@Issue("SECURITY-1166")
@WithJenkins
class Security1166Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void loginRequestFailWithNoCrumb() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        try (JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false)) {
            wc.goTo(""); // to trigger the creation of a session

            WebRequest request = new WebRequest(new URL(j.getURL(), "j_spring_security_check"), HttpMethod.POST);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new NameValuePair("j_username", "alice"));
            params.add(new NameValuePair("j_password", "alice"));

            request.setRequestParameters(params);

            WebResponse response = wc.getPage(request).getWebResponse();
            assertEquals(401, response.getStatusCode());
            assertUserNotConnected(wc, "alice");
        }
    }

    @Test
    void loginRequestFailWithInvalidCrumb() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        try (JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false)) {
            wc.goTo(""); // to trigger the creation of a session

            WebRequest request = new WebRequest(new URL(j.getURL(), "j_spring_security_check"), HttpMethod.POST);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new NameValuePair("j_username", "alice"));
            params.add(new NameValuePair("j_password", "alice"));

            String crumbField = j.jenkins.getCrumbIssuer().getCrumbRequestField();
            params.add(new NameValuePair(crumbField, "invalid-crumb"));

            request.setRequestParameters(params);

            WebResponse response = wc.getPage(request).getWebResponse();
            assertEquals(401, response.getStatusCode());
            assertUserNotConnected(wc, "alice");
        }
    }

    @Test
    void loginRequestSucceedWithValidCrumb() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        try (JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false)) {
            wc.goTo(""); // to trigger the creation of a session

            WebRequest request = new WebRequest(new URL(j.getURL(), "j_spring_security_check"), HttpMethod.POST);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new NameValuePair("j_username", "alice"));
            params.add(new NameValuePair("j_password", "alice"));

            String crumbField = j.jenkins.getCrumbIssuer().getCrumbRequestField();
            String crumbValue = j.jenkins.getCrumbIssuer().getCrumb();
            params.add(new NameValuePair(crumbField, crumbValue));

            request.setRequestParameters(params);

            WebResponse response = wc.getPage(request).getWebResponse();
            assertEquals(200, response.getStatusCode());
            assertUserConnected(wc, "alice");
        }
    }

    @Test
    void loginRequestSucceedWithCrumbInHeader() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        try (JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false)) {
            wc.goTo(""); // to trigger the creation of a session

            WebRequest request = new WebRequest(new URL(j.getURL(), "j_spring_security_check"), HttpMethod.POST);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new NameValuePair("j_username", "alice"));
            params.add(new NameValuePair("j_password", "alice"));

            String crumbField = j.jenkins.getCrumbIssuer().getCrumbRequestField();
            String crumbValue = j.jenkins.getCrumbIssuer().getCrumb();
            request.setAdditionalHeader(crumbField, crumbValue);

            request.setRequestParameters(params);

            WebResponse response = wc.getPage(request).getWebResponse();
            assertEquals(200, response.getStatusCode());
            assertUserConnected(wc, "alice");
        }
    }

    @Test
    void loginRequestSucceedWithNoCrumbIssuer() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        j.jenkins.setCrumbIssuer(null);

        try (JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false)) {
            wc.goTo(""); // to trigger the creation of a session

            WebRequest request = new WebRequest(new URL(j.getURL(), "j_spring_security_check"), HttpMethod.POST);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new NameValuePair("j_username", "alice"));
            params.add(new NameValuePair("j_password", "alice"));

            request.setRequestParameters(params);

            WebResponse response = wc.getPage(request).getWebResponse();
            assertEquals(200, response.getStatusCode());
            assertUserConnected(wc, "alice");
        }
    }

    @Test
    void loginRequestFailWithGET() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        try (JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false)) {
            wc.goTo(""); // to trigger the creation of a session
            wc.setRedirectEnabled(false); // disabling redirection to demonstrates that Spring did not handle the request

            WebRequest request = new WebRequest(new URL(j.getURL(), "j_spring_security_check"), HttpMethod.GET);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new NameValuePair("j_username", "alice"));
            params.add(new NameValuePair("j_password", "alice"));

            String crumbField = j.jenkins.getCrumbIssuer().getCrumbRequestField();
            String crumbValue = j.jenkins.getCrumbIssuer().getCrumb();
            params.add(new NameValuePair(crumbField, crumbValue));

            request.setRequestParameters(params);

            WebResponse response = wc.getPage(request).getWebResponse();
            assertEquals(404, response.getStatusCode());
            assertThat(response.getResponseHeaders(), hasItem(hasProperty("name", startsWith("Stapler-Trace"))));
            assertUserNotConnected(wc, "alice");
        }
    }

    @Test
    void loginThroughSetupWizard() throws Exception {
        j.jenkins.setInstallState(jenkins.install.InstallState.INITIAL_SECURITY_SETUP);

        String initialAdminPassword = j.jenkins.getSetupWizard().getInitialAdminPasswordFile().readToString().trim();

        try (JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false)) {
            HtmlPage page = wc.goTo("login");
            List<HtmlForm> forms = page.getForms();
            HtmlForm form = forms.get(0);

            assertEquals(1, forms.size()); // It's the only form, which doesn't have a name or an id.

            form.getInputByName("j_password").setValue(initialAdminPassword);

            HtmlFormUtil.submit(form, null);

            assertUserConnected(wc, "admin");
        }
    }

    private void assertUserConnected(JenkinsRule.WebClient wc, String expectedUsername) throws Exception {
        XmlPage page = (XmlPage) wc.goTo("whoAmI/api/xml", "application/xml");
        assertThat(page, hasXPath("//name", is(expectedUsername)));
    }

    private void assertUserNotConnected(JenkinsRule.WebClient wc, String notExpectedUsername) throws Exception {
        XmlPage page = (XmlPage) wc.goTo("whoAmI/api/xml", "application/xml");
        assertThat(page, hasXPath("//name", not(is(notExpectedUsername))));
    }
}
