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

import hudson.model.User;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import jenkins.security.SecurityListener;
import jenkins.security.seed.UserSeedProperty;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Login filter with a change for Jenkins so that
 * we can pick up the hidden "from" form field defined in {@code login.jelly}
 * to send the user back to where he came from, after a successful authentication.
 * 
 * @author Kohsuke Kawaguchi
 */
@Restricted(NoExternalUse.class)
public final class AuthenticationProcessingFilter2 extends UsernamePasswordAuthenticationFilter {

    public AuthenticationProcessingFilter2(String authenticationGatewayUrl) {
        setRequiresAuthenticationRequestMatcher(new AntPathRequestMatcher("/" + authenticationGatewayUrl, "POST"));
        // Jenkins/login.jelly & SetupWizard/authenticate-security-token.jelly
        setUsernameParameter("j_username");
        setPasswordParameter("j_password");
    }

    /* TODO none of this compiles against Spring Security; rewrite (try InteractiveAuthenticationSuccessEvent & SimpleUrlAuthenticationFailureHandler):

    @Override
    protected String determineTargetUrl(HttpServletRequest request) {
        AbstractAuthenticationProcessingFilter f = new UsernamePasswordAuthenticationFilter();
        String targetUrl = request.getParameter("from");
        request.getSession().setAttribute("from", targetUrl);

        if (targetUrl == null)
            return getDefaultTargetUrl();

        if (!Util.isSafeToRedirectTo(targetUrl))
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
     * @see AbstractProcessingFilter#determineFailureUrl(HttpServletRequest, AuthenticationException)
     * /
    @Override
    protected String determineFailureUrl(HttpServletRequest request, AuthenticationException failed) {
        Properties excMap = getExceptionMappings();
		String failedClassName = failed.getClass().getName();
		String whereFrom = request.getParameter("from");
		request.getSession().setAttribute("from", whereFrom);
		return excMap.getProperty(failedClassName, getAuthenticationFailureUrl());
    }
    */

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) throws IOException, ServletException {
        super.successfulAuthentication(request, response, chain, authResult);
        // make sure we have a session to store this successful authentication, given that we no longer
        // let HttpSessionContextIntegrationFilter2 to create sessions.
        // SecurityContextPersistenceFilter stores the updated SecurityContext object into this session later
        // (either when a redirect is issued, via its HttpResponseWrapper, or when the execution returns to its
        // doFilter method.
        /* TODO causes an ISE on the next line:
        request.getSession().invalidate();
        */
        HttpSession newSession = request.getSession();

        if (!UserSeedProperty.DISABLE_USER_SEED) {
            User user = User.getById(authResult.getName(), true);

            UserSeedProperty userSeed = user.getProperty(UserSeedProperty.class);
            String sessionSeed = userSeed.getSeed();
            newSession.setAttribute(UserSeedProperty.USER_SESSION_SEED, sessionSeed);
        }

        // as the request comes from Spring Security redirect, that's not a Stapler one
        // thus it's not possible to retrieve it in the SecurityListener in that case
        // for that reason we need to keep the above code that apply quite the same logic as UserSeedSecurityListener
        SecurityListener.fireLoggedIn(authResult.getName());
    }

    /**
     * Leave the information about login failure.
     */
    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException, ServletException {
        super.unsuccessfulAuthentication(request, response, failed);
        LOGGER.log(Level.FINE, "Login attempt failed", failed);
        /* TODO this information appears to have been deliberately removed from Spring Security:
        Authentication auth = failed.getAuthentication();
        if (auth != null) {
            SecurityListener.fireFailedToLogIn(auth.getName());
        }
        */
    }

    private static final Logger LOGGER = Logger.getLogger(AuthenticationProcessingFilter2.class.getName());
}
