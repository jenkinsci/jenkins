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

import org.acegisecurity.AuthenticationException;
import org.acegisecurity.ui.webapp.AuthenticationProcessingFilterEntryPoint;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.text.MessageFormat;

/**
 * For anonymous requests to pages that require authentication,
 * first respond with {@link HttpServletResponse#SC_FORBIDDEN},
 * then redirect browsers automatically to the login page.
 *
 * <p>
 * This is a compromise to handle programmatic access and
 * real browsers equally well.
 *
 * <p>
 * The page that programs see is entirely white, and it auto-redirects,
 * so humans wouldn't notice it. 
 *
 * @author Kohsuke Kawaguchi
 */
public class HudsonAuthenticationEntryPoint extends AuthenticationProcessingFilterEntryPoint {
    public void commence(ServletRequest request, ServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse rsp = (HttpServletResponse) response;

        String requestedWith = req.getHeader("X-Requested-With");
        if("XMLHttpRequest".equals(requestedWith)) {
            // container authentication normally relies on session attribute to
            // remember where the user came from, so concurrent AJAX requests
            // often ends up sending users back to AJAX pages after successful login.
            // this is not desirable, so don't redirect AJAX requests to the user.
            // this header value is sent from Prototype.
            rsp.sendError(SC_FORBIDDEN);
        } else {
            // give the opportunity to include the target URL
            String loginForm = req.getContextPath()+getLoginFormUrl();
            loginForm = MessageFormat.format(loginForm, URLEncoder.encode(req.getRequestURI(),"UTF-8"));
            req.setAttribute("loginForm", loginForm);

            rsp.setStatus(SC_FORBIDDEN);
            rsp.setContentType("text/html;charset=UTF-8");
            PrintWriter out;
            try {
                ServletOutputStream sout = rsp.getOutputStream();
                out = new PrintWriter(new OutputStreamWriter(sout));
            } catch (IllegalStateException e) {
                out = rsp.getWriter();
            }
            out.printf(
                "<html><head>" +
                "<meta http-equiv='refresh' content='1;url=%1$s'/>" +
                "<script>window.location.replace('%1$s');</script>" +
                "</head>" +
                "<body style='background-color:white; color:white;'>" +
                "Authentication required</body></html>", loginForm
            );
            out.flush();
        }
    }
}
