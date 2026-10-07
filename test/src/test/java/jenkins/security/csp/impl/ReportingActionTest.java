package jenkins.security.csp.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import hudson.ExtensionList;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import java.net.URL;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;
import jenkins.security.csp.CspReceiver;
import jenkins.security.csp.ReportingContext;
import net.sf.json.JSONObject;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.core.Authentication;

@For(ReportingAction.class)
@WithJenkins
class ReportingActionTest {

    private LoggerRule logger;

    @BeforeEach
    void setup() {
        logger = new LoggerRule();
    }

    @Test
    void validReports(JenkinsRule j) throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        // Test with authenticated user
        User alice = User.getById("alice", true);
        String encodedAuth;
        try (ACLContext ctx = ACL.as2(alice.impersonate2())) {
            Authentication auth = Jenkins.getAuthentication2();
            encodedAuth = ReportingContext.encodeContext(Jenkins.class, auth, "/");
        }

        TestReceiver receiverAuth = new TestReceiver();
        ExtensionList.lookup(CspReceiver.class).add(receiverAuth);

        JSONObject reportAuth = new JSONObject();
        reportAuth.put("csp-report", new JSONObject().put("violated-directive", "script-src"));

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            WebRequest request = new WebRequest(new URL(j.getURL(), ReportingAction.URL + "/" + encodedAuth), HttpMethod.POST);
            request.setRequestBody(reportAuth.toString());
            request.setAdditionalHeader("Content-Type", "application/csp-report");

            WebResponse response = wc.getPage(request).getWebResponse();
            assertThat(response.getStatusCode(), is(200));
            assertThat(receiverAuth.invoked, is(true));
            assertThat(receiverAuth.userId, is("alice"));
        }

        {
            // Test with anonymous user
            String encodedAnon;
            try (ACLContext ctx = ACL.as2(Jenkins.ANONYMOUS2)) {
                Authentication auth = Jenkins.getAuthentication2();
                encodedAnon = ReportingContext.encodeContext(Jenkins.class, auth, "/");
            }

            TestReceiver receiverAnon = new TestReceiver();
            ExtensionList.lookup(CspReceiver.class).add(receiverAnon);

            JSONObject report = new JSONObject();
            report.put("csp-report", new JSONObject());

            try (JenkinsRule.WebClient wc = j.createWebClient()) {
                WebRequest request = new WebRequest(new URL(j.getURL(), ReportingAction.URL + "/" + encodedAnon), HttpMethod.POST);
                request.setRequestBody(report.toString());
                wc.getPage(request);

                assertThat(receiverAnon.invoked, is(true));
            }
        }

        {
            // Test with system user
            String encodedSystem;
            try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                Authentication auth = Jenkins.getAuthentication2();
                encodedSystem = ReportingContext.encodeContext(Jenkins.class, auth, "/");
            }

            JSONObject report = new JSONObject();
            report.put("csp-report", new JSONObject());

            try (JenkinsRule.WebClient wc = j.createWebClient()) {
                WebRequest request = new WebRequest(new URL(j.getURL(), ReportingAction.URL + "/" + encodedSystem), HttpMethod.POST);
                request.setRequestBody(report.toString());
                WebResponse response = wc.getPage(request).getWebResponse();

                assertThat(response.getStatusCode(), is(200));
            }
        }
    }

    @Test
    void multipleReceivers(JenkinsRule j) throws Exception {
        TestReceiver receiver1 = new TestReceiver();
        TestReceiver receiver2 = new TestReceiver();
        ExtensionList<CspReceiver> receivers = ExtensionList.lookup(CspReceiver.class);
        receivers.add(receiver1);
        receivers.add(receiver2);

        String encoded;
        try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
            Authentication auth = Jenkins.getAuthentication2();
            encoded = ReportingContext.encodeContext(Jenkins.class, auth, "/");
        }

        JSONObject report = new JSONObject();
        report.put("csp-report", new JSONObject());

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            WebRequest request = new WebRequest(new URL(j.getURL(), ReportingAction.URL + "/" + encoded), HttpMethod.POST);
            request.setRequestBody(report.toString());
            wc.getPage(request);
        }
        assertThat(receiver1.invoked, is(true));
        assertThat(receiver2.invoked, is(true));
    }

    @Test
    void invalidReportBody(JenkinsRule j) throws Exception {
        logger.record(ReportingAction.class, Level.FINE).capture(10);

        String encoded;
        try (ACLContext unused = ACL.as2(ACL.SYSTEM2)) {
            Authentication auth = Jenkins.getAuthentication2();
            encoded = ReportingContext.encodeContext(Jenkins.class, auth, "/");
        }

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wc.setThrowExceptionOnFailingStatusCode(false);

            {
                WebRequest req = new WebRequest(
                        new URL(j.getURL(), ReportingAction.URL + "/" + encoded), HttpMethod.POST);
                req.setRequestBody("{ invalid json ]");
                WebResponse rsp = wc.getPage(req).getWebResponse();
                assertThat(rsp.getStatusCode(), is(200)); // Graceful handling
                // IOException from reading/parsing should be logged
                assertThat(logger.getMessages(), hasItem(equalTo("Failed to parse JSON report for ViewContext[className=jenkins.model.Jenkins, viewName=/]: { invalid json ]")));
            }

            {
                WebRequest req = new WebRequest(
                        new URL(j.getURL(), ReportingAction.URL + "/" + encoded), HttpMethod.POST);
                // Don't set request body
                WebResponse rsp = wc.getPage(req).getWebResponse();
                assertThat(rsp.getStatusCode(), is(200)); // Graceful handling
                // Empty body causes JSON parsing error
                assertThat(logger.getMessages(), hasItem(equalTo("Failed to parse JSON report for ViewContext[className=jenkins.model.Jenkins, viewName=/]: ")));
            }
        }
    }

    @Test
    void testNoCharsetDoNotCare(JenkinsRule j) throws Exception {
        logger.record(ReportingAction.class, Level.FINE).capture(10);

        String encoded;
        try (ACLContext unused = ACL.as2(ACL.SYSTEM2)) {
            Authentication auth = Jenkins.getAuthentication2();
            encoded = ReportingContext.encodeContext(Jenkins.class, auth, "/");
        }

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            // Null character encoding (no Content-Type header) - should work without error
            {
                JSONObject report = new JSONObject();
                report.put("csp-report", new JSONObject());
                WebRequest request3 = new WebRequest(
                        new URL(j.getURL(), ReportingAction.URL + "/" + encoded), HttpMethod.POST);
                request3.setRequestBody(report.toString());
                request3.removeAdditionalHeader("Content-Type");
                WebResponse response3 = wc.getPage(request3).getWebResponse();
                assertThat(response3.getStatusCode(), is(200));
                // Valid JSON should parse successfully, no error logs expected
                assertThat(logger.getMessages(), empty());
            }
        }
    }

    @Test
    void reportBodyTooLong(JenkinsRule j) throws Exception {
        logger.record(ReportingAction.class, Level.FINER).capture(10);

        String encoded;
        try (ACLContext unused = ACL.as2(ACL.SYSTEM2)) {
            Authentication auth = Jenkins.getAuthentication2();
            encoded = ReportingContext.encodeContext(Jenkins.class, auth, "/");
        }

        // Create report larger than MAX_REPORT_LENGTH (20KB)
        JSONObject largeReport = new JSONObject();
        largeReport.put("data", "x".repeat(25 * 1024)); // 25KB

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            WebRequest request = new WebRequest(new URL(j.getURL(), ReportingAction.URL + "/" + encoded), HttpMethod.POST);
            request.setRequestBody(largeReport.toString());

            WebResponse response = wc.getPage(request).getWebResponse();
            assertThat(response.getStatusCode(), is(200));

            // Should log that max length was exceeded
            assertThat(logger.getMessages(), hasItem(containsString("exceeded max length")));
        }
    }

    @Test
    void invalidContext(JenkinsRule j) throws Exception {
        logger.record(ReportingAction.class, Level.FINE).capture(10);

        JSONObject report = new JSONObject();
        report.put("csp-report", new JSONObject());

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wc.setThrowExceptionOnFailingStatusCode(false);

            // Malformed context (completely invalid)
            String malformedContext = "not-a-valid-context";
            WebRequest request1 = new WebRequest(
                    new URL(j.getURL(), ReportingAction.URL + "/" + malformedContext), HttpMethod.POST);
            request1.setRequestBody(report.toString());
            WebResponse response1 = wc.getPage(request1).getWebResponse();
            assertThat(response1.getStatusCode(), is(200)); // Graceful handling
            assertThat(logger.getMessages(), hasItem(containsString("Unexpected rest of path failed to decode: not-a-valid-context")));

            // Tampered context (valid structure but tampered MAC)
            String encoded;
            try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
                Authentication auth = Jenkins.getAuthentication2();
                encoded = ReportingContext.encodeContext(Jenkins.class, auth, "/");
            }
            String[] parts = encoded.split(":");
            String tampered = "AAAA:" + parts[1] + ":" + parts[2] + ":" + parts[3];

            WebRequest request2 = new WebRequest(
                    new URL(j.getURL(), ReportingAction.URL + "/" + tampered), HttpMethod.POST);
            request2.setRequestBody(report.toString());
            WebResponse response2 = wc.getPage(request2).getWebResponse();
            assertThat(response2.getStatusCode(), is(200));
            assertThat(logger.getMessages(), hasItem(containsString("Unexpected rest of path failed to decode: AAAA:U1lTVEVN:amVua2lucy5tb2RlbC5KZW5raW5z:Lw==")));
        }
    }

    @Test
    void brokenReceiverImplementation(JenkinsRule j) throws Exception {
        logger.record(ReportingAction.class, Level.WARNING).capture(10);

        ThrowingReceiver throwingReceiver = new ThrowingReceiver();
        TestReceiver normalReceiver = new TestReceiver();

        ExtensionList<CspReceiver> receivers = ExtensionList.lookup(CspReceiver.class);
        receivers.add(0, throwingReceiver); // Add first
        receivers.add(1, normalReceiver);

        String encoded;
        try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
            Authentication auth = Jenkins.getAuthentication2();
            encoded = ReportingContext.encodeContext(Jenkins.class, auth, "/");
        }

        JSONObject report = new JSONObject();
        report.put("csp-report", new JSONObject());

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            WebRequest request = new WebRequest(
                    new URL(j.getURL(), ReportingAction.URL + "/" + encoded), HttpMethod.POST);
            request.setRequestBody(report.toString());

            wc.getPage(request);

            // Normal receiver should still be invoked
            assertThat(normalReceiver.invoked, is(true));
            // Exception should be logged
            assertThat(
                    logger.getMessages(),
                    hasItem(containsString("Error reporting CSP for ViewContext[className=jenkins.model.Jenkins, viewName=/] to jenkins.security.csp.impl.ReportingActionTest$ThrowingReceiver@")));
        }
    }

    @Test
    void wrongHttpMethods(JenkinsRule j) throws Exception {
        String encoded;
        try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
            Authentication auth = Jenkins.getAuthentication2();
            encoded = ReportingContext.encodeContext(Jenkins.class, auth, "/");
        }

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wc.setThrowExceptionOnFailingStatusCode(false);

            // GET should fail
            WebRequest requestGet = new WebRequest(
                    new URL(j.getURL(), ReportingAction.URL + "/" + encoded), HttpMethod.GET);
            WebResponse responseGet = wc.getPage(requestGet).getWebResponse();
            assertThat(responseGet.getStatusCode(), not(is(200)));

            // PUT should fail
            WebRequest requestPut = new WebRequest(
                    new URL(j.getURL(), ReportingAction.URL + "/" + encoded), HttpMethod.PUT);
            WebResponse responsePut = wc.getPage(requestPut).getWebResponse();
            assertThat(responsePut.getStatusCode(), not(is(200)));
        }
    }

    @Test
    void loggingReceiverWorks(JenkinsRule j) throws Exception {
        logger.record(LoggingReceiver.class, Level.FINEST).capture(100);

        // Navigate to a page and extract CSP reporting endpoint
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            HtmlPage page = wc.goTo("");
            String cspHeader = page.getWebResponse().getResponseHeaderValue("Content-Security-Policy");

            assertThat(cspHeader, notNullValue());

            String reportUri = extractReportUri(cspHeader);
            assertThat(reportUri, notNullValue());

            // Submit a CSP violation report
            JSONObject violation = new JSONObject();
            JSONObject cspReport = new JSONObject();
            cspReport.put("document-uri", j.getURL().toString());
            cspReport.put("violated-directive", "script-src 'self'");
            cspReport.put("effective-directive", "script-src");
            cspReport.put("original-policy", cspHeader);
            cspReport.put("blocked-uri", "https://evil.com/script.js");
            violation.put("csp-report", cspReport);

            WebRequest request = new WebRequest(new URL(reportUri), HttpMethod.POST);
            request.setRequestBody(violation.toString());
            request.setAdditionalHeader("Content-Type", "application/csp-report");

            WebResponse response = wc.getPage(request).getWebResponse();
            assertThat(response.getStatusCode(), is(200));

            // Verify it was logged
            assertThat(logger.getMessages(), hasItem(
                    allOf(
                            containsString("Received anonymous report for context ViewContext[className=hudson.model.AllView, viewName=]: "
                                    + "{\"csp-report\":{\"document-uri\":\"" + j.getURL().toExternalForm()
                                    + "\",\"violated-directive\":\"script-src 'self'\",\"effective-directive\":\"script-src\",\"original-policy\":\"base-uri 'none'; default-src 'self'; form-action 'self'; frame-ancestors 'self'; "
                                    + "img-src 'self' data:; script-src 'report-sample' 'self'; style-src 'report-sample' 'self' 'unsafe-inline'; report-to content-security-policy; report-uri " + j.getURL().toExternalForm()
                                    + "content-security-policy-reporting-endpoint/"),
                            containsString(":YW5vbnltb3Vz:aHVkc29uLm1vZGVsLkFsbFZpZXc=:\",\"blocked-uri\":\"https://evil.com/script.js\"}}"))));
        }
    }

    private String extractReportUri(String cspHeader) {
        // Extract report-uri from CSP header
        Pattern pattern = Pattern.compile("report-uri\\s+([^;]+)");
        Matcher matcher = pattern.matcher(cspHeader);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static class TestReceiver implements CspReceiver {
        boolean invoked;
        String userId;
        ViewContext viewContext;
        JSONObject report;

        @Override
        public void report(ViewContext viewContext, String userId, JSONObject report) {
            this.invoked = true;
            this.userId = userId;
            this.viewContext = viewContext;
            this.report = report;
        }
    }

    private static class ThrowingReceiver implements CspReceiver {
        @Override
        public void report(ViewContext viewContext, String userId, JSONObject report) {
            throw new RuntimeException("Test exception from receiver");
        }
    }
}
