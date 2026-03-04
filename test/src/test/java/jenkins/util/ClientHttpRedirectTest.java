package jenkins.util;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
        ClientHttpRedirect redirect = new ClientHttpRedirect(url);
        StaplerRequest2 req = Mockito.mock(StaplerRequest2.class);
        StaplerResponse2 rsp = Mockito.mock(StaplerResponse2.class);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(baos);

        Mockito.when(rsp.getWriter()).thenReturn(writer);
        Mockito.when(req.getContextPath()).thenReturn("");

        redirect.generateResponse(req, rsp, null);

        writer.flush();
        String output = baos.toString();
        assertTrue(output.length() > 0);
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
        PrintWriter writer = new PrintWriter(baos);

        Mockito.when(rsp.getWriter()).thenReturn(writer);
        Mockito.when(req.getContextPath()).thenReturn("");

        redirect.generateResponse(req, rsp, null);

        writer.flush();
        String output = baos.toString();
        assertTrue(output.length() > 0);
    }

    /**
     * Test that javascript: URLs are blocked.
     */
    @Test
    void testJavaScriptUrlBlocked() {
        ClientHttpRedirect redirect = new ClientHttpRedirect("javascript:alert('XSS')");
        StaplerRequest2 req = Mockito.mock(StaplerRequest2.class);
        StaplerResponse2 rsp = Mockito.mock(StaplerResponse2.class);

        Exception exception = assertThrows(Exception.class, () -> redirect.generateResponse(req, rsp, null));
        assertTrue(exception.getMessage().contains("Unsafe redirect blocked"));
    }

    /**
     * Test that data: URLs are blocked.
     */
    @Test
    void testDataUrlBlocked() {
        ClientHttpRedirect redirect = new ClientHttpRedirect("data:text/html,<script>alert('XSS')</script>");
        StaplerRequest2 req = Mockito.mock(StaplerRequest2.class);
        StaplerResponse2 rsp = Mockito.mock(StaplerResponse2.class);

        Exception exception = assertThrows(Exception.class, () -> redirect.generateResponse(req, rsp, null));
        assertTrue(exception.getMessage().contains("Unsafe redirect blocked"));
    }

    /**
     * Test that file: URLs are blocked.
     */
    @Test
    void testFileUrlBlocked() {
        ClientHttpRedirect redirect = new ClientHttpRedirect("file:///etc/passwd");
        StaplerRequest2 req = Mockito.mock(StaplerRequest2.class);
        StaplerResponse2 rsp = Mockito.mock(StaplerResponse2.class);

        Exception exception = assertThrows(Exception.class, () -> redirect.generateResponse(req, rsp, null));
        assertTrue(exception.getMessage().contains("Unsafe redirect blocked"));
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
    void testCustomSchemesBlocked(String url) {
        ClientHttpRedirect redirect = new ClientHttpRedirect(url);
        StaplerRequest2 req = Mockito.mock(StaplerRequest2.class);
        StaplerResponse2 rsp = Mockito.mock(StaplerResponse2.class);

        Exception exception = assertThrows(Exception.class, () -> redirect.generateResponse(req, rsp, null));
        assertTrue(exception.getMessage().contains("Unsafe redirect blocked"));
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
        ClientHttpRedirect redirect = new ClientHttpRedirect(url);
        StaplerRequest2 req = Mockito.mock(StaplerRequest2.class);
        StaplerResponse2 rsp = Mockito.mock(StaplerResponse2.class);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(baos);

        Mockito.when(rsp.getWriter()).thenReturn(writer);
        Mockito.when(req.getContextPath()).thenReturn("");

        redirect.generateResponse(req, rsp, null);

        writer.flush();
        String output = baos.toString();
        assertTrue(output.length() > 0);
    }
}
