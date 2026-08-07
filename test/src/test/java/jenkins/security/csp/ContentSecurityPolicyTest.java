package jenkins.security.csp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import hudson.model.DirectoryBrowserSupport;
import hudson.model.FreeStyleProject;
import hudson.tasks.ArtifactArchiver;
import java.net.URL;
import java.util.logging.Level;
import jenkins.security.ResourceDomainConfiguration;
import jenkins.security.csp.impl.LoggingReceiver;
import jenkins.security.csp.impl.ReportingAction;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.CreateFileBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ContentSecurityPolicyTest {

    private static final String RESOURCE_ROOT_URL_DOMAIN = "127.0.0.1";
    private static final String BASIC_HTML = "<html><body>test</body></html>";
    private static final String HTML_CONTENT_TYPE = "text/html";

    private final LoggerRule logger = new LoggerRule().record(LoggingReceiver.class, Level.FINEST).capture(100);

    @Test
    void anonymousUserCspReporting(JenkinsRule j) throws Exception {
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            submitCspReport(j, wc);
        }

        assertThat(String.join("\n", logger.getMessages()), containsString("Received anonymous report for context ViewContext[className=hudson.model.AllView, viewName=]: {\"csp-report\":{}}"));
    }

    @Test
    void authenticatedUserCspReporting(JenkinsRule j) throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        try (JenkinsRule.WebClient wc = j.createWebClient().login("alice")) {
            submitCspReport(j, wc);
        }

        String logMessages = String.join("\n", logger.getMessages());
        assertThat(logMessages, not(containsString("Received anonymous report for context ViewContext[className")));
    }

    private void submitCspReport(JenkinsRule j, JenkinsRule.WebClient wc) throws Exception {
        // Retrieve a Jenkins page and inspect the CSP header
        HtmlPage page = wc.goTo("");
        WebResponse response = page.getWebResponse();

        String cspHeader = getCspHeaderFromResponse(response);
        assertThat("CSP header should be present", cspHeader, notNullValue());

        String reportingEndpoint = extractReportingEndpoint(cspHeader);
        assertThat("Reporting endpoint should be present in CSP header", reportingEndpoint, notNullValue());

        JSONObject report = new JSONObject();
        report.put("csp-report", new JSONObject());

        wc.setThrowExceptionOnFailingStatusCode(false);

        WebRequest request = new WebRequest(
            new URL(reportingEndpoint),
            HttpMethod.POST
        );
        request.setRequestBody(report.toString());
        request.setAdditionalHeader("Content-Type", "application/csp-report");

        WebResponse reportResponse = wc.getPage(request).getWebResponse();

        // Verify the report was accepted (should return 200 OK)
        assertThat("Report submission should succeed", reportResponse.getStatusCode(), org.hamcrest.Matchers.is(200));
    }

    @Test
    void reportUriAndReportingEndpointMatch(JenkinsRule j) throws Exception {
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            HtmlPage page = wc.goTo("");
            WebResponse response = page.getWebResponse();

            String cspHeader = getCspHeaderFromResponse(response);
            String reportUriValue = extractReportingEndpoint(cspHeader);
            String reportingEndpointsHeader = getHeaderFromResponse(response, "Reporting-Endpoints");
            assertThat(reportingEndpointsHeader, notNullValue());
            String reportToValue = extractReportToEndpoint(reportingEndpointsHeader);
            assertThat(reportToValue, containsString(ReportingAction.URL));
            assertThat(reportUriValue, equalTo(reportToValue));
        }
    }

    @Test
    void reportUriEncodesContextCorrectly(JenkinsRule j) throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("test-job");

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            // Just a URL with context class and restOfPath
            HtmlPage page = wc.goTo("job/" + project.getName() + "/changes");
            WebResponse response = page.getWebResponse();

            String cspHeader = getCspHeaderFromResponse(response);
            String reportUri = extractReportingEndpoint(cspHeader);

            // Extract the context parameter from the report-uri URL
            // Format: http://.../csp-reports/<encoded-context>
            String[] uriParts = reportUri.split("/");
            String encodedContext = uriParts[uriParts.length - 1];

            ReportingContext.DecodedContext context = ReportingContext.decodeContext(encodedContext);
            assertThat(context.contextClassName(), equalTo(FreeStyleProject.class.getName()));
            assertThat(context.restOfPath(), equalTo("changes"));
        }
    }

    @Test
    void workspaceAndArtifactCspDiffersFromGlobalCsp(JenkinsRule j) throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("test.html", BASIC_HTML));
        project.getPublishersList().add(new ArtifactArchiver("*"));

        j.buildAndAssertSuccess(project);

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            HtmlPage regularPage = wc.goTo("job/" + project.getName() + "/ws/");
            String regularCsp = getCspHeaderFromResponse(regularPage.getWebResponse());
            assertThat(regularCsp, notNullValue());

            Page workspacePage = wc.goTo("job/" + project.getName() + "/ws/test.html", HTML_CONTENT_TYPE);
            String workspaceCsp = getCspHeaderFromResponse(workspacePage.getWebResponse());
            assertThat(workspaceCsp, equalTo(DirectoryBrowserSupport.DEFAULT_CSP_VALUE));

            Page artifactPage = wc.goTo("job/" + project.getName() + "/lastSuccessfulBuild/artifact/test.html", HTML_CONTENT_TYPE);
            String artifactCsp = getCspHeaderFromResponse(artifactPage.getWebResponse());
            assertThat(artifactCsp, equalTo(DirectoryBrowserSupport.DEFAULT_CSP_VALUE));

            assertThat(artifactCsp, not(equalTo(regularCsp)));
        }
    }

    @Test
    void workspaceCspCanBeDisabledIndependently(JenkinsRule j) throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("test.html", BASIC_HTML));
        j.buildAndAssertSuccess(project);

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            HtmlPage regularPage = wc.goTo("");
            String regularCsp = getCspHeaderFromResponse(regularPage.getWebResponse());
            assertThat(regularCsp, notNullValue());

            String originalValue = System.getProperty(DirectoryBrowserSupport.CSP_PROPERTY_NAME);
            try {
                System.setProperty(DirectoryBrowserSupport.CSP_PROPERTY_NAME, "");

                // Get workspace file (DirectoryBrowserSupport CSP should be absent)
                Page workspacePage = wc.goTo("job/" + project.getName() + "/ws/test.html", HTML_CONTENT_TYPE);
                String workspaceCsp = getCspHeaderFromResponse(workspacePage.getWebResponse());

                assertThat(workspaceCsp, nullValue());

                // Verify regular CSP is unaffected
                regularPage = wc.goTo("");
                String regularCspAfter = getCspHeaderFromResponse(regularPage.getWebResponse());
                assertThat(regularCspAfter, notNullValue());
                assertThat(regularCspAfter, equalTo(regularCsp));
            } finally {
                if (originalValue == null) {
                    System.clearProperty(DirectoryBrowserSupport.CSP_PROPERTY_NAME);
                } else {
                    System.setProperty(DirectoryBrowserSupport.CSP_PROPERTY_NAME, originalValue);
                }
            }
        }
    }

    @Test
    void workspaceCspTakesPrecedenceOverRegularCsp(JenkinsRule j) throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("test.html", BASIC_HTML));
        j.buildAndAssertSuccess(project);

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            Page workspacePage = wc.goTo("job/" + project.getName() + "/ws/test.html", HTML_CONTENT_TYPE);
            WebResponse response = workspacePage.getWebResponse();

            long cspHeaderCount = response.getResponseHeaders().stream().filter(header -> header.getName().equals("Content-Security-Policy")).count();
            assertThat(cspHeaderCount, equalTo(1L));

            String csp = getCspHeaderFromResponse(response);
            assertThat(csp, equalTo(DirectoryBrowserSupport.DEFAULT_CSP_VALUE));
        }
    }

    private static String getCspHeaderFromResponse(WebResponse response) {
        return getHeaderFromResponse(response, "Content-Security-Policy");
    }

    private static String getHeaderFromResponse(WebResponse response, String headerName) {
        for (NameValuePair pair : response.getResponseHeaders()) {
            if (pair.getName().equals(headerName)) {
                return pair.getValue();
            }
        }
        return null;
    }

    private static String extractReportingEndpoint(String cspHeader) {
        // CSP header format: "... report-uri <endpoint>"
        String[] parts = cspHeader.split("report-uri");
        if (parts.length < 2) {
            return null;
        }
        // Get the endpoint URL (everything after "report-uri" until semicolon or end)
        String endpointPart = parts[1].trim();
        int semicolonIndex = endpointPart.indexOf(';');
        if (semicolonIndex > 0) {
            endpointPart = endpointPart.substring(0, semicolonIndex).trim();
        }
        return endpointPart;
    }

    private static String extractReportToEndpoint(String reportingEndpointsHeader) {
        // Reporting-Endpoints header format: "content-security-policy: <url>"
        // See jenkins.security.Filter for where the header is set
        String[] parts = reportingEndpointsHeader.split(":", 2);
        if (parts.length < 2) {
            return null;
        }
        return parts[1].trim();
    }

    @Test
    void resourceDomainHasNoCspHeaders(JenkinsRule j) throws Exception {
        String resourceDomainUrl = j.getURL().toExternalForm().replace("localhost", RESOURCE_ROOT_URL_DOMAIN);
        ResourceDomainConfiguration.get().setUrl(resourceDomainUrl);

        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("test.html", BASIC_HTML));
        j.buildAndAssertSuccess(project);

        try (JenkinsRule.WebClient wc = j.createWebClient().withRedirectEnabled(true)) {
            // Access workspace file - should redirect to resource domain
            Page workspacePage = wc.goTo("job/" + project.getName() + "/ws/test.html", HTML_CONTENT_TYPE);
            WebResponse response = workspacePage.getWebResponse();
            assertThat(response.getWebRequest().getUrl().getHost(), equalTo(RESOURCE_ROOT_URL_DOMAIN));

            assertThat(response.getStatusCode(), is(200));
            assertThat(getCspHeaderFromResponse(response), nullValue());
            assertThat(getHeaderFromResponse(response, "Reporting-Endpoints"), nullValue());
        }
    }

}
