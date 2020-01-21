/*
 * The MIT License
 *
 * Copyright (c) 2019-2020 CloudBees, Inc.
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
package jenkins.security;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.util.PluginServletFilter;
import org.acegisecurity.AccessDeniedException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Stapler;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

@Restricted(NoExternalUse.class)
public class StackTraceSuppressionFilter implements Filter {

    public static boolean SHOW_STACK_TRACE = Boolean.getBoolean(StackTraceSuppressionFilter.class.getName() + ".SHOW_STACK_TRACE");

    private static final Logger LOGGER = Logger.getLogger(StackTraceSuppressionFilter.class.getName());

//    @Initializer(after = InitMilestone.STARTED)
//    public static void init() throws ServletException {
//        PluginServletFilter.addFilter(new StackTraceSuppressionFilter());
//    }

    @Override
    public void init(FilterConfig filterConfig) {
        // no-op
    }

    @Override
    public void destroy() {
        // no-op
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } catch (Exception e) {
            AccessDeniedException accessDeniedException = containsAccessDeniedException(e);
            if (accessDeniedException != null) {
                throw accessDeniedException;
            }
            if (Stapler.isSocketException(e)) {
                return;
            }

            if (showStackTrace()) {
                if (e instanceof IOException || e instanceof ServletException || e instanceof RuntimeException) {
                    // thing we can throw without wrapping
                    throw e;
                }
                throwServletException(e);
            } else if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
                String errorId = logException((HttpServletRequest) request, e);

                respondWithSimpleErrorPage((HttpServletResponse) response, errorId);
            }
        }
    }

    private void respondWithSimpleErrorPage(HttpServletResponse response, String errorId) throws ServletException {
        if (response.isCommitted()) {
            LOGGER.finest("Response is already committed: " + response);
        } else {
            try {
                response.setStatus(SC_INTERNAL_SERVER_ERROR);
                response.setContentType("text/html;charset=UTF-8");
                response.setHeader("Cache-Control", "no-cache,must-revalidate");
                PrintWriter w;
                try {
                    w = response.getWriter();
                } catch (IllegalStateException x) {
                    w = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
                }
                w.println("<html><head><title>" + Messages.SuppressionFilter_Title() + "</title><body>");
                w.println("<p>" + Messages.SuppressionFilter_ContactAdmin(errorId) + "</p>");
                w.println("</body></html>");
                w.close();
            } catch (Error error) {
                throw error;
            } catch (Throwable x) {
                // if we fail to report this error, bail out
                throw new ServletException(Messages.SuppressionFilter_ContactAdmin(errorId)); // not chaining x since it might contain something
            }
        }
    }

    private void throwServletException(Exception e) throws ServletException {
        // ServletException chaining is messed up. We go extra mile here to really
        // chain the cause.
        ServletException servletException = new ServletException("Request processing failed", e);
        try {
            if (servletException.getCause() == null) {
                servletException.initCause(e);
            }
        } catch (IllegalStateException ise) {
            // just in case.
        }

        throw servletException;
    }

    /**
     * Report an error and then generate a unique ID for that error.
     */
    private String logException(HttpServletRequest req, Throwable e) {
        String id = UUID.randomUUID().toString();
        LOGGER.log(Level.WARNING, "Request processing failed. URI=" + req.getRequestURI() + " clientIP=" + req.getRemoteAddr() + " ErrorID=" + id, e);
        return id;
    }

    private static boolean showStackTrace() {
        return SHOW_STACK_TRACE;
    }

    private AccessDeniedException containsAccessDeniedException(Exception exception) {
        // Guard against malicious overrides of Throwable.equals by
        // using a Set with identity equality semantics.
        Set<Throwable> dejaVu = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable currentException = exception;
        do {
            dejaVu.add(currentException);
            if (currentException instanceof AccessDeniedException) {
                return (AccessDeniedException) currentException;
            }
            currentException = currentException.getCause();
        } while (currentException != null && !dejaVu.contains(currentException));
        return null;
    }

}
