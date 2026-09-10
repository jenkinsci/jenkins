package hudson.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.security.BasicApiTokenHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class BasicAuthenticationFilterTest {

    private BasicAuthenticationFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private ServletContext servletContext;
    private MockedStatic<Jenkins> jenkinsMock;
    private MockedStatic<BasicApiTokenHelper> tokenHelperMock;

    @BeforeEach
    void setUp() throws Exception {
        filter = new BasicAuthenticationFilter();

        FilterConfig filterConfig = mock(FilterConfig.class);
        servletContext = mock(ServletContext.class);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        filter.init(filterConfig);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);

        Jenkins jenkins = mock(Jenkins.class);
        when(jenkins.isUseSecurity()).thenReturn(true);
        jenkinsMock = mockStatic(Jenkins.class);
        jenkinsMock.when(Jenkins::get).thenReturn(jenkins);

        tokenHelperMock = mockStatic(BasicApiTokenHelper.class);
        tokenHelperMock.when(() -> BasicApiTokenHelper.isConnectingUsingApiToken(any(), any())).thenReturn(null);

        when(request.getUserPrincipal()).thenReturn(null);
        when(request.getServletPath()).thenReturn("/api/json");
    }

    @AfterEach
    void tearDown() {
        tokenHelperMock.close();
        jenkinsMock.close();
    }

    /**
     * Non-Basic Authorization headers (e.g., Bearer, Digest) should be passed
     * through the filter chain, not treated as malformed Basic auth.
     */
    @Test
    void nonBasicAuthorizationHeaderIsPassedThrough() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer eyJhbGciOiJSUzI1NiJ9");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    /**
     * A short Authorization header (fewer than 6 characters) must not cause
     * a StringIndexOutOfBoundsException. It should be passed through since
     * it cannot be a "Basic " header.
     */
    @Test
    void shortAuthorizationHeaderDoesNotCrash() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("X");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    /**
     * An empty Authorization header should be passed through without crashing.
     */
    @Test
    void emptyAuthorizationHeaderDoesNotCrash() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    /**
     * A valid "Basic " prefix with invalid Base64 content should result in 401,
     * and the log output should contain the raw Authorization header for diagnostics
     * since FINE logging requires explicit administrator action to enable.
     */
    @Test
    void malformedBase64ReturnsUnauthorizedAndLogsDiagnosticData() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic not-valid-base64!!!");

        Logger logger = Logger.getLogger(BasicAuthenticationFilter.class.getName());
        Level originalLevel = logger.getLevel();
        logger.setLevel(Level.FINE);
        TestLogHandler logHandler = new TestLogHandler();
        logger.addHandler(logHandler);

        try {
            filter.doFilter(request, response, chain);
        } finally {
            logger.removeHandler(logHandler);
            logger.setLevel(originalLevel);
        }

        verify(response).setStatus(eq(HttpServletResponse.SC_UNAUTHORIZED));
        verify(response).setHeader(eq("WWW-Authenticate"), eq("Basic realm=\"Jenkins user\""));
        verify(chain, never()).doFilter(any(), any());

        // Verify that a log record was emitted and that it includes the raw Authorization header for diagnostics
        assertFalse(logHandler.records.isEmpty(), "Expected at least one log record for decoding failure");
        boolean foundDiagnosticLog = false;
        for (LogRecord record : logHandler.records) {
            String message = record.getMessage();
            if (message.contains("Failed to decode authentication from: ") && message.contains("not-valid-base64")) {
                foundDiagnosticLog = true;
                break;
            }
        }
        assertTrue(foundDiagnosticLog, "Log message should contain the raw Authorization header for diagnostics");
    }

    /**
     * A valid Basic authorization header should extract username and password
     * and proceed to authenticate rather than being bypassed or rejected.
     */
    @Test
    void validBasicAuthorizationHeaderExtractsCredentials() throws Exception {
        // "alice:secret" encoded in Base64 is "YWxpY2U6c2VjcmV0"
        when(request.getHeader("Authorization")).thenReturn("Basic YWxpY2U6c2VjcmV0");
        when(request.getContextPath()).thenReturn("");
        when(request.getQueryString()).thenReturn(null);

        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(servletContext.getRequestDispatcher("/j_security_check?j_username=alice&j_password=secret")).thenReturn(dispatcher);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
        verify(response).setHeader("Location", "/secured/api/json");
        verify(dispatcher).include(request, response);
        verify(chain, never()).doFilter(any(), any());
    }

    /**
     * Digest authorization should be passed through, not treated as Basic.
     */
    @Test
    void digestAuthorizationHeaderIsPassedThrough() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Digest username=\"admin\", realm=\"test\"");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private static class TestLogHandler extends Handler {
        final java.util.List<LogRecord> records = new java.util.ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
