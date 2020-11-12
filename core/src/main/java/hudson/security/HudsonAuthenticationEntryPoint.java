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

import com.google.common.base.Strings;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Functions;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.text.MessageFormat;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

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
@Restricted(NoExternalUse.class)
public class HudsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final String loginFormUrl;

    public HudsonAuthenticationEntryPoint(String loginFormUrl) {
        this.loginFormUrl = loginFormUrl;
    }

    @Override
    public void commence(HttpServletRequest req, HttpServletResponse rsp, AuthenticationException reason) throws IOException, ServletException {
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
            String uriFrom = req.getRequestURI();
            if(!Strings.isNullOrEmpty(req.getQueryString())) uriFrom += "?" + req.getQueryString();
            String loginForm = req.getContextPath() + loginFormUrl;
            loginForm = MessageFormat.format(loginForm, URLEncoder.encode(uriFrom,"UTF-8"));
            req.setAttribute("loginForm", loginForm);

            rsp.setStatus(SC_FORBIDDEN);
            rsp.setContentType("text/html;charset=UTF-8");

            Functions.advertiseHeaders(rsp);

            AccessDeniedException3 cause = null;
            // report the diagnosis information if possible
            if (reason instanceof InsufficientAuthenticationException) {
                if (reason.getCause() instanceof AccessDeniedException3) {
                    cause = (AccessDeniedException3) reason.getCause();
                    cause.reportAsHeaders(rsp);
                }
            }

            PrintWriter out;
            try {
                out = new PrintWriter(new OutputStreamWriter(rsp.getOutputStream()));
            } catch (IllegalStateException e) {
                out = rsp.getWriter();
            }
            printResponse(loginForm, out);

            if (cause!=null)
                cause.report(out);

            out.printf(
                "-->%n%n"+
                "</body></html>");
            // Turn Off "Show Friendly HTTP Error Messages" Feature on the Server Side.
            // See http://support.microsoft.com/kb/294807
            for (int i=0; i < 10; i++)
                out.print("                              ");
            out.close();
        }
    }

    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "Intermediate step for redirecting users to login page.")
    private void printResponse(String loginForm, PrintWriter out) {
        out.printf(
            "<html><head>" +
            "<meta http-equiv='refresh' content='1;url=%1$s'/>" +
            "<script>window.location.replace('%1$s');</script>" +
            "</head>" +
            "<body style='background-color:white; color:white;'>%n" +
            "%n%n"+
            "Authentication required%n"+
            "<!--%n",loginForm);
    }
}
