/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

package hudson.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import org.apache.commons.jelly.JellyTagException;
import org.kohsuke.stapler.CompatibleFilter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.access.ExceptionTranslationFilter;

/**
 * If a security exception caused {@link JellyTagException},
 * rethrow it accordingly so that {@link ExceptionTranslationFilter}
 * can pick it up and initiate the redirection.
 *
 * @author Kohsuke Kawaguchi
 */
public class UnwrapSecurityExceptionFilter implements CompatibleFilter {
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } catch (ServletException e) {
            Throwable t = e.getRootCause();
            if (t != null && !(t instanceof JellyTagException)) {
                if (t instanceof ServletException) {
                    t = ((ServletException) t).getRootCause();
                } else {
                    t = t.getCause();
                }
            }
            if (t instanceof JellyTagException) {
                JellyTagException jte = (JellyTagException) t;
                Throwable cause = jte.getCause();
                if (cause instanceof AccessDeniedException || cause instanceof AuthenticationException) {
                    throw new ServletException(cause);
                }
            }
            throw e;
        }
    }

    @Override
    public void destroy() {
    }
}
