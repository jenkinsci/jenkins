/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Matthew R. Harrah
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

import java.util.Properties;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import hudson.Util;
import jenkins.security.SecurityListener;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.ui.webapp.AuthenticationProcessingFilter;

/**
 * {@link AuthenticationProcessingFilter} with a change for Jenkins so that
 * we can pick up the hidden "from" form field defined in <tt>login.jelly</tt>
 * to send the user back to where he came from, after a successful authentication.
 * 
 * @author Kohsuke Kawaguchi
 */
public class AuthenticationProcessingFilter2 extends AuthenticationProcessingFilter {
    @Override
    protected String determineTargetUrl(HttpServletRequest request) {
        String targetUrl = request.getParameter("from");
        request.getSession().setAttribute("from", targetUrl);

        if (targetUrl == null)
            return getDefaultTargetUrl();

        if (Util.isAbsoluteUri(targetUrl))
            return "."; // avoid open redirect

        // URL returned from determineTargetUrl() is resolved against the context path,
        // whereas the "from" URL is resolved against the top of the website, so adjust this.
        if(targetUrl.startsWith(request.getContextPath()))
            return targetUrl.substring(request.getContextPath().length());

        // not sure when this happens, but apparently this happens in some case.
        // see #1274
        return targetUrl;
    }

    /**
     * @see org.acegisecurity.ui.AbstractProcessingFilter#determineFailureUrl(javax.servlet.http.HttpServletRequest, org.acegisecurity.AuthenticationException)
     */
    @Override
    protected String determineFailureUrl(HttpServletRequest request, AuthenticationException failed) {
        Properties excMap = getExceptionMappings();
		String failedClassName = failed.getClass().getName();
		String whereFrom = request.getParameter("from");
		request.getSession().setAttribute("from", whereFrom);
		return excMap.getProperty(failedClassName, getAuthenticationFailureUrl());
    }

    @Override
    protected void onSuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, Authentication authResult) throws IOException {
        super.onSuccessfulAuthentication(request,response,authResult);
        // make sure we have a session to store this successful authentication, given that we no longer
        // let HttpSessionContextIntegrationFilter2 to create sessions.
        // HttpSessionContextIntegrationFilter stores the updated SecurityContext object into this session later
        // (either when a redirect is issued, via its HttpResponseWrapper, or when the execution returns to its
        // doFilter method.
        request.getSession().invalidate();
        request.getSession();
        SecurityListener.fireLoggedIn(authResult.getName());
    }

    /**
     * Leave the information about login failure.
     *
     * <p>
     * Otherwise it seems like Acegi doesn't really leave the detail of the failure anywhere.
     */
    @Override
    protected void onUnsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException {
        super.onUnsuccessfulAuthentication(request, response, failed);
        LOGGER.log(Level.FINE, "Login attempt failed", failed);
        Authentication auth = failed.getAuthentication();
        if (auth != null) {
            SecurityListener.fireFailedToLogIn(auth.getName());
        }
    }

    private static final Logger LOGGER = Logger.getLogger(AuthenticationProcessingFilter2.class.getName());
}
