package hudson.init.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hudson.util.RunListInterruptedException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

class InstallUncaughtExceptionHandlerTest {

    @Test
    void interruptedRequest() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse rsp = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(rsp.getWriter()).thenReturn(new PrintWriter(body));
        try {
            Thread.currentThread().interrupt();
            InstallUncaughtExceptionHandler.handleException(null, new ServletException(new RunListInterruptedException()), req, rsp, 500);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        verify(rsp).resetBuffer();
        verify(rsp).setStatus(503);
        verify(rsp).setContentType("text/plain;charset=UTF-8");
        assertEquals("Resource-intensive request interrupted before a complete result could be produced.", body.toString());
    }

    @Test
    void committedResponseIsLeftAlone() throws Exception {
        HttpServletResponse rsp = mock(HttpServletResponse.class);
        when(rsp.isCommitted()).thenReturn(true);
        InstallUncaughtExceptionHandler.handleException(null, new RunListInterruptedException(), mock(HttpServletRequest.class), rsp, 500);
        verify(rsp, never()).resetBuffer();
        verify(rsp, never()).setStatus(503);
        verify(rsp, never()).getWriter();
    }

    @Test
    void headResponseHasNoBody() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse rsp = mock(HttpServletResponse.class);
        when(req.getMethod()).thenReturn("HEAD");
        InstallUncaughtExceptionHandler.handleException(null, new RunListInterruptedException(), req, rsp, 500);
        verify(rsp).setStatus(503);
        verify(rsp, never()).getWriter();
    }

    @Test
    void unrelatedCancellationIsNotAnInterruptedScan() throws Exception {
        HttpServletResponse rsp = mock(HttpServletResponse.class);
        when(rsp.isCommitted()).thenReturn(true);
        InstallUncaughtExceptionHandler.handleException(null, new CancellationException(), mock(HttpServletRequest.class), rsp, 500);
        verify(rsp, never()).setStatus(503);
    }
}
