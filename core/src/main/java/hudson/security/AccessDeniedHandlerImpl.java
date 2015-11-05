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

import jenkins.model.Jenkins;
import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.ui.AccessDeniedHandler;
import org.kohsuke.stapler.WebApp;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Handles {@link AccessDeniedException} happened during request processing.
 * Specifically, send 403 error code and the login page.
 *
 * @author Kohsuke Kawaguchi
 */
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {
    public void handle(ServletRequest request, ServletResponse response, AccessDeniedException cause) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse rsp = (HttpServletResponse) response;

        rsp.setStatus(HttpServletResponse.SC_FORBIDDEN);
        req.setAttribute("exception",cause);

        if (cause instanceof AccessDeniedException2) {
            ((AccessDeniedException2)cause).reportAsHeaders(rsp);
        }

        WebApp.get(Jenkins.getInstance().servletContext).getSomeStapler()
                .invoke(req,rsp, Jenkins.getInstance(), "/accessDenied");
    }
}
