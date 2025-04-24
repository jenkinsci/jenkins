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

import hudson.model.User;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.security.BasicApiTokenHelper;
import jenkins.security.SecurityListener;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Implements the dual authentication mechanism.
 *
 * <p>
 * Jenkins supports both the HTTP basic authentication and the form-based authentication.
 * The former is for scripted clients, and the latter is for humans. Unfortunately,
 * because the servlet spec does not allow us to programmatically authenticate users,
 * we need to rely on some hack to make it work, and this is the class that implements
 * that hack.
 *
 * <p>
 * When an HTTP request arrives with an HTTP basic auth header, this filter detects
 * that and emulate an invocation of {@code /j_security_check}
 * (see <a href="http://mail-archives.apache.org/mod_mbox/tomcat-users/200105.mbox/%3C9005C0C9C85BD31181B20060085DAC8B10C8EF@tuvi.andmevara.ee%3E">this page</a> for the original technique.)
 *
 * <p>
 * This causes the container to perform authentication, but there's no way
 * to find out whether the user has been successfully authenticated or not.
 * So to find this out, we then redirect the user to
 * {@link jenkins.model.Jenkins#doSecured(StaplerRequest2, StaplerResponse2) /secured/... page}.
 *
 * <p>
 * The handler of the above URL checks if the user is authenticated,
 * and if not report an HTTP error code. Otherwise the user is
 * redirected back to the original URL, where the request is served.
 *
 * <p>
 * So all in all, the redirection works like {@code /abc/def} → {@code /secured/abc/def}
 * → {@code /abc/def}.
 *
 * <h2>Notes</h2>
 * <ul>
 * <li>
 * The technique of getting a request dispatcher for {@code /j_security_check} may not
 * work for all containers, but so far that seems like the only way to make this work.
 * <li>
 * This A → B → A redirect is a cyclic redirection, so we need to watch out for clients
 * that detect this as an error.
 * </ul>
 *
 * @author Kohsuke Kawaguchi
 */
public class BasicAuthenticationFilter implements Filter {
    private static final Logger LOGGER = Logger.getLogger(BasicAuthenticationFilter.class.getName());
    private ServletContext servletContext;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        servletContext = filterConfig.getServletContext();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse rsp = (HttpServletResponse) response;
        String authorization = req.getHeader("Authorization");

        String path = req.getServletPath();
        if (authorization == null || req.getUserPrincipal() != null || path.startsWith("/secured/")
        || !Jenkins.get().isUseSecurity()) {
            // normal requests, or security not enabled
            if (req.getUserPrincipal() != null) {
                // before we route this request, integrate the container authentication
                // to Spring Security. For anonymous users that doesn't have user principal,
                // AnonymousProcessingFilter that follows this should create
                // an Authentication object.
                SecurityContextHolder.getContext().setAuthentication(new ContainerAuthentication(req));
            }
            try {
                chain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
            return;
        }

        // authenticate the user
        String username = null;
        String password = null;
        String uidpassword = null;
        try {
            uidpassword = new String(Base64.getDecoder().decode(authorization.substring(6).getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.FINE, ex, () -> "Failed to decode authentication from: " + authorization);
            uidpassword = "";
        }
        int idx = uidpassword.indexOf(':');
        if (idx >= 0) {
            username = uidpassword.substring(0, idx);
            password = uidpassword.substring(idx + 1);
        }

        if (username == null) {
            rsp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            rsp.setHeader("WWW-Authenticate", "Basic realm=\"Jenkins user\"");
            return;
        }

        {
            User u = BasicApiTokenHelper.isConnectingUsingApiToken(username, password);
            if (u != null) {
                UserDetails userDetails = u.getUserDetailsForImpersonation2();
                Authentication auth = u.impersonate(userDetails);

                SecurityListener.fireAuthenticated2(userDetails);

                SecurityContextHolder.getContext().setAuthentication(auth);
                try {
                    chain.doFilter(request, response);
                } finally {
                    SecurityContextHolder.clearContext();
                }
                return;
            }
        }


        path = req.getContextPath() + "/secured" + path;
        String q = req.getQueryString();
        if (q != null)
            path += '?' + q;

        // prepare a redirect
        prepareRedirect(rsp, path);

        // ... but first let the container authenticate this request
        RequestDispatcher d = servletContext.getRequestDispatcher("/j_security_check?j_username=" +
            URLEncoder.encode(username, StandardCharsets.UTF_8) + "&j_password=" + URLEncoder.encode(password, StandardCharsets.UTF_8));
        d.include(req, rsp);
    }

    private void prepareRedirect(HttpServletResponse rsp, String path) {
        rsp.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
        rsp.setHeader("Location", path);
    }

    @Override
    public void destroy() {
    }
}
