package jenkins.util;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.mockito.Mockito;

public class ClientHttpRedirectTest {

    @Test
    public void testSafeRedirects() throws Exception {
        assertValid("http://example.com");
        assertValid("https://example.com");
        assertValid("HTTP://EXAMPLE.COM");
        assertValid("HTTPS://EXAMPLE.COM");
        assertValid("/relative/path");
    }

    @Test
    public void testUnsafeRedirects() {
        assertInvalid("javascript:alert(1)");
        assertInvalid("data:text/html,<script>alert(1)</script>");
        assertInvalid("file:///etc/passwd");
        assertInvalid("ftp://example.com");
        assertInvalid("smb://example.com");
        assertInvalid("JAVAScript:alert(1)");
    }

    @Test
    public void testNullRedirect() {
        assertThrows(IllegalArgumentException.class, () -> new ClientHttpRedirect(null));
    }

    private void assertValid(String url) throws Exception {
        ClientHttpRedirect redirect = new ClientHttpRedirect(url);
        StaplerRequest2 req = Mockito.mock(StaplerRequest2.class);
        StaplerResponse2 rsp = Mockito.mock(StaplerResponse2.class);

        Mockito.when(req.getContextPath()).thenReturn("/jenkins");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(baos, false, StandardCharsets.UTF_8);
        Mockito.when(rsp.getWriter()).thenReturn(writer);

        redirect.generateResponse(req, rsp, null);
        writer.flush();
        String output = baos.toString(StandardCharsets.UTF_8);
        assertTrue(output.length() > 0);
        assertTrue(output.contains(hudson.Util.escape(url)));
    }

    private void assertInvalid(String url) {
        ClientHttpRedirect redirect = new ClientHttpRedirect(url);
        StaplerRequest2 req = Mockito.mock(StaplerRequest2.class);
        StaplerResponse2 rsp = Mockito.mock(StaplerResponse2.class);

        Exception e = assertThrows(Exception.class, () -> redirect.generateResponse(req, rsp, null));
        assertTrue(e.getMessage() != null && e.getMessage().contains("Unsafe redirect blocked"));
    }
}
