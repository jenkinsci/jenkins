/*
 * The MIT License
 *
 * Copyright (c) 2026, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.RequestDispatcher;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.jvnet.hudson.test.Issue;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

@Issue("JENKINS-26387")
class ClientHttpRedirectTest {

    @Test
    void nullConstructorArgumentThrows() {
        assertThrows(NullPointerException.class, () -> new ClientHttpRedirect(null));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://example.com",
                "https://jenkins.io",
                "http://localhost:8080/jenkins",
                "HTTP://EXAMPLE.COM/DOCS",
                "HTTPS://WWW.JENKINS.IO/REDIRECT/FOO",
                "/manage",
                "relative/path",
                "/relative/path?param=value"
            })
    void isAllowedValidUrls(String url) {
        assertTrue(ClientHttpRedirect.isAllowed(url));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "javascript:alert(1)",
                "javascript:void(0)",
                "data:text/html,<script>alert(1)</script>",
                "vbscript:msgbox(1)",
                "file:///etc/passwd",
                "ftp://example.com",
                "//example.com",
                "\\example.com",
                "",
                "   "
            })
    void isAllowedInvalidUrls(String url) {
        assertFalse(ClientHttpRedirect.isAllowed(url));
    }

    @Test
    void isAllowedNull() {
        assertFalse(ClientHttpRedirect.isAllowed(null));
    }

    @Test
    void generateResponseAllowedUrl() throws Exception {
        String targetUrl = "https://www.jenkins.io/redirect/test";
        ClientHttpRedirect redirect = new ClientHttpRedirect(targetUrl);

        StaplerRequest2 req = mock(StaplerRequest2.class);
        StaplerResponse2 rsp = mock(StaplerResponse2.class);
        when(req.getContextPath()).thenReturn("/jenkins");

        StringWriter out = new StringWriter();
        when(rsp.getWriter()).thenReturn(new PrintWriter(out));

        redirect.generateResponse(req, rsp, null);

        verify(rsp).setContentType("text/html;charset=UTF-8");
        String responseHtml = out.toString();
        assertTrue(responseHtml.contains("url=https://www.jenkins.io/redirect/test"));
        assertTrue(responseHtml.contains("data-redirect-url='https://www.jenkins.io/redirect/test'"));
    }

    @Test
    void generateResponseBlockedUrlForwardsToSecurityWarning() throws Exception {
        String maliciousUrl = "javascript:alert('XSS')";
        ClientHttpRedirect redirect = new ClientHttpRedirect(maliciousUrl);

        StaplerRequest2 req = mock(StaplerRequest2.class);
        StaplerResponse2 rsp = mock(StaplerResponse2.class);
        RequestDispatcher rd = mock(RequestDispatcher.class);

        when(req.getView(ClientHttpRedirect.class, "securityWarning")).thenReturn(rd);

        redirect.generateResponse(req, rsp, null);

        verify(req).setAttribute("redirectUrl", maliciousUrl);
        verify(rd).forward(req, rsp);
    }
}
