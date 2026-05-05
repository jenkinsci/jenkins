package jenkins.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.mockito.Mockito;

/**
 * Tests for {@link ClientHttpRedirect} URL validation.
 */
class ClientHttpRedirectTest {

    @Test
    void testNullRedirectUrlBlockedAtConstruction() {
        assertThrows(NullPointerException.class, () -> new ClientHttpRedirect(null));
    }

    /**
     * Test that HTTP URLs pass validation and generate response.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "http://www.example.com/page",
        "https://www.jenkins.io/doc/",
        "HTTP://EXAMPLE.COM",
        "HTTPS://JENKINS.IO"
    })
    void testAllowedUrlSchemes(String url) throws Exception {
        assertUrlAllowed(url);
    }

    /**
     * Test that relative URLs are allowed.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "manage/configure",
        "/jenkins/manage",
        "/../config",
        "foo/bar"
    })
    void testRelativeUrlsAllowed(String url) throws Exception {
        ClientHttpRedirect redirect = new ClientHttpRedirect(url);
        StaplerRequest2 req = Mockito.mock(StaplerRequest2.class);
        StaplerResponse2 rsp = Mockito.mock(StaplerResponse2.class);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8), true);
        Mockito.when(rsp.getWriter()).thenReturn(writer);
        Mockito.when(req.getContextPath()).thenReturn("");

        redirect.generateResponse(req, rsp, null);

        writer.flush();
        String output = baos.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains(url));
    }

    /**
     * Test that javascript: URLs are blocked.
     */
    @Test
    void testJavaScriptUrlBlocked() throws Exception {
        assertUrlBlockedWithForbidden("javascript:alert('XSS')");
    }

    /**
     * Test that data: URLs are blocked.
     */
    @Test
    void testDataUrlBlocked() throws Exception {
        assertUrlBlockedWithForbidden("data:text/html,<script>alert('XSS')</script>");
    }

    /**
     * Test that file: URLs are blocked.
     */
    @Test
    void testFileUrlBlocked() throws Exception {
        assertUrlBlockedWithForbidden("file:///etc/passwd");
    }

    /**
     * Test that custom scheme URLs are blocked.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "custom-scheme://malicious",
        "ftp://server/file",
        "telnet://host:23"
    })
    void testCustomSchemesBlocked(String url) throws Exception {
        assertUrlBlockedWithForbidden(url);
    }

    /**
     * Test that scheme-relative and backslash-prefixed URLs are blocked.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "//attacker.example",
        "\\\\attacker.example"
    })
    void testSchemeRelativeUrlsBlocked(String url) throws Exception {
        assertUrlBlockedWithForbidden(url);
    }

    private static void assertUrlBlockedWithForbidden(String url) throws Exception {
        ClientHttpRedirect redirect = new ClientHttpRedirect(url);
        StaplerRequest2 req = Mockito.mock(StaplerRequest2.class);
        StaplerResponse2 rsp = Mockito.mock(StaplerResponse2.class);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8), true);
        Mockito.when(rsp.getWriter()).thenReturn(writer);

        HttpResponses.HttpResponseException exception = assertThrows(HttpResponses.HttpResponseException.class,
            () -> redirect.generateResponse(req, rsp, null));
        assertDoesNotThrow(() -> exception.generateResponse(req, rsp, null));
        Mockito.verify(rsp).sendError(Mockito.eq(403), Mockito.contains(url));
    }

    /**
     * Test that mixed case HTTP/HTTPS URLs are allowed.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "HtTp://www.example.com",
        "HtTpS://www.jenkins.io",
        "hTTp://test.com"
    })
    void testMixedCaseHttpAllowed(String url) throws Exception {
        assertUrlAllowed(url);
    }

    private static void assertUrlAllowed(String url) throws Exception {
        ClientHttpRedirect redirect = new ClientHttpRedirect(url);
        StaplerRequest2 req = Mockito.mock(StaplerRequest2.class);
        StaplerResponse2 rsp = Mockito.mock(StaplerResponse2.class);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8), true);
        Mockito.when(rsp.getWriter()).thenReturn(writer);
        Mockito.when(req.getContextPath()).thenReturn("");

        redirect.generateResponse(req, rsp, null);

        writer.flush();
        String output = baos.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains(url));
    }
}
