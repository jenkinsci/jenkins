package jenkins.security.csp.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.jvnet.hudson.test.LoggerRule.recorded;

import hudson.security.csrf.CrumbExclusion;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.util.HttpServletFilter;
import org.hamcrest.Matcher;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class CspFilterTest {
    @Test
    void testFilterHandlesRequestWithoutRootUrl(JenkinsRule j) throws Exception {
        // Reset URL so we do not fall back to configured #getRootUrl
        JenkinsLocationConfiguration.get().setUrl(null);
        assertCspHeadersForUrl(j, HttpMethod.GET, "test-filter/some-path",
                equalTo("This is a test filter response."),
                equalTo("base-uri 'none'; default-src 'self'; form-action 'self'; frame-ancestors 'self'; img-src 'self' data:; script-src 'report-sample' 'self'; style-src 'report-sample' 'self' 'unsafe-inline';"),
                nullValue(String.class));
    }

    @Test
    void testFilterHandlesRequestWithRootUrl(JenkinsRule j) throws Exception {
        assertCspHeadersForUrl(j, HttpMethod.GET, "test-filter/some-path",
                equalTo("This is a test filter response."),
                startsWith(
                        "base-uri 'none'; default-src 'self'; form-action 'self'; frame-ancestors 'self'; img-src 'self' data:; " +
                                "script-src 'report-sample' 'self'; style-src 'report-sample' 'self' 'unsafe-inline'; report-to content-security-policy; report-uri http://localhost"),
                startsWith("content-security-policy: http://localhost"));
    }

    @Test
    void testCrumbExclusionHandlesRequestWithoutRootUrl(JenkinsRule j) throws Exception {
        // Reset URL so we do not fall back to configured #getRootUrl
        JenkinsLocationConfiguration.get().setUrl(null);

        assertCspHeadersForUrl(j, HttpMethod.POST, "test-crumb-exclusion/some-path",
                equalTo("This is a test crumb exclusion response."),
                equalTo("base-uri 'none'; default-src 'self'; form-action 'self'; frame-ancestors 'self'; img-src 'self' data:; script-src 'report-sample' 'self'; style-src 'report-sample' 'self' 'unsafe-inline';"),
                nullValue(String.class));
    }

    @Test
    void testCrumbExclusionHandlesRequestWithRootUrl(JenkinsRule j) throws Exception {
        assertCspHeadersForUrl(j, HttpMethod.POST, "test-crumb-exclusion/some-path",
                equalTo("This is a test crumb exclusion response."),
                startsWith(
                        "base-uri 'none'; default-src 'self'; form-action 'self'; frame-ancestors 'self'; img-src 'self' data:; " +
                        "script-src 'report-sample' 'self'; style-src 'report-sample' 'self' 'unsafe-inline'; report-to content-security-policy; report-uri http://localhost"),
                startsWith("content-security-policy: http://localhost"));
    }

    @Test
    void testFilterWithCustomCsp(JenkinsRule j) throws Exception {
        LoggerRule loggerRule = new LoggerRule().record(CspFilter.class, Level.FINE).capture(100);
        assertCspHeadersForUrl(j, HttpMethod.GET, "test-filter-with-csp/some-path",
                equalTo("This is a test filter response with custom CSP."),
                equalTo("default-src 'self';"),
                startsWith("content-security-policy: http://localhost"));
        assertThat(loggerRule, recorded(Level.FINE, allOf(
                startsWith(
                        "CSP header has unexpected differences: Expected 'base-uri 'none'; default-src 'self'; form-action 'self'; frame-ancestors 'self'; img-src 'self' data:; " +
                                "script-src 'report-sample' 'self'; style-src 'report-sample' 'self' 'unsafe-inline'; report-to content-security-policy; report-uri http://localhost:"),
                endsWith(":YW5vbnltb3Vz::L3Rlc3QtZmlsdGVyLXdpdGgtY3NwL3NvbWUtcGF0aA==' but got 'default-src 'self';'"))));
    }

    @Test
    void testFilterWithoutCsp(JenkinsRule j) throws Exception {
        LoggerRule loggerRule = new LoggerRule().record(CspFilter.class, Level.FINE).capture(100);
        assertCspHeadersForUrl(j, HttpMethod.GET, "test-filter-without-csp/some-path",
                equalTo("This is a test filter response without CSP."),
                nullValue(String.class),
                startsWith("content-security-policy: http://localhost"));
        assertThat(loggerRule, recorded(Level.FINE, allOf(
                startsWith(
                        "CSP header has unexpected differences: Expected 'base-uri 'none'; default-src 'self'; form-action 'self'; frame-ancestors 'self'; img-src 'self' data:; " +
                                "script-src 'report-sample' 'self'; style-src 'report-sample' 'self' 'unsafe-inline'; report-to content-security-policy; report-uri http://localhost:"),
                endsWith(":YW5vbnltb3Vz::L3Rlc3QtZmlsdGVyLXdpdGhvdXQtY3NwL3NvbWUtcGF0aA==' but got 'null'"))));
    }

    @TestExtension
    public static class TestFilter implements HttpServletFilter {
        @Override
        public boolean handle(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
            if (req.getRequestURI().substring(req.getContextPath().length()).startsWith("/test-filter/")) {
                rsp.setStatus(200);
                rsp.setContentType("text/plain");
                rsp.getWriter().write("This is a test filter response.");
                return true;
            }
            return false;
        }
    }

    @TestExtension
    public static class TestCrumbExclusion extends CrumbExclusion {
        @Override
        public boolean process(HttpServletRequest req, HttpServletResponse rsp, FilterChain chain) throws IOException {
            if (req.getRequestURI().substring(req.getContextPath().length()).startsWith("/test-crumb-exclusion/")) {
                rsp.setStatus(200);
                rsp.setContentType("text/plain");
                rsp.getWriter().write("This is a test crumb exclusion response.");
                return true;
            }
            return false;
        }
    }

    @TestExtension
    public static class TestFilterWithCustomCsp implements HttpServletFilter {
        @Override
        public boolean handle(HttpServletRequest req, HttpServletResponse rsp) throws IOException, ServletException {
            if (req.getRequestURI().substring(req.getContextPath().length()).startsWith("/test-filter-with-csp/")) {
                rsp.setHeader("Content-Security-Policy", "default-src 'self';");
                rsp.setStatus(200);
                rsp.setContentType("text/plain");
                rsp.getWriter().write("This is a test filter response with custom CSP.");
                return true;
            }
            return false;
        }
    }

    @TestExtension
    public static class TestFilterWithoutCsp implements HttpServletFilter {
        @Override
        public boolean handle(HttpServletRequest req, HttpServletResponse rsp) throws IOException, ServletException {
            if (req.getRequestURI().substring(req.getContextPath().length()).startsWith("/test-filter-without-csp/")) {
                rsp.setHeader("Content-Security-Policy", null);
                rsp.setStatus(200);
                rsp.setContentType("text/plain");
                rsp.getWriter().write("This is a test filter response without CSP.");
                return true;
            }
            return false;
        }
    }

    private static void assertCspHeadersForUrl(JenkinsRule jenkinsRule, HttpMethod method, String path,
            Matcher<String> responseBodyMatcher, Matcher<String> cspHeaderMatcher, Matcher<String> reportingEndpointsHeaderMatcher) throws IOException {
        try (JenkinsRule.WebClient wc = jenkinsRule.createWebClient()) {
            WebRequest webRequest = new WebRequest(new URL(jenkinsRule.getURL() + path), method);
            WebResponse response = wc.getPage(webRequest).getWebResponse();
            assertThat(response.getStatusCode(), equalTo(200));
            assertThat(response.getContentAsString().trim(), responseBodyMatcher);

            final String cspHeader = response.getResponseHeaderValue("Content-Security-Policy");
            assertThat(cspHeader, cspHeaderMatcher);
            assertThat(response.getResponseHeaderValue("Reporting-Endpoints"), reportingEndpointsHeaderMatcher);
        }
    }
}
