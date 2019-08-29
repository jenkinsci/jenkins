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
import jenkins.security.NonSerializableSecurityContext;
import jenkins.security.seed.UserSeedProperty;
import org.acegisecurity.context.HttpSessionContextIntegrationFilter;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.Authentication;
import org.acegisecurity.providers.anonymous.AnonymousAuthenticationToken;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Erases the {@link SecurityContext} persisted in {@link HttpSession}
 * if {@link InvalidatableUserDetails#isInvalid()} returns true.
 *
 * @see InvalidatableUserDetails
 */
public class HttpSessionContextIntegrationFilter2 extends HttpSessionContextIntegrationFilter {
    public HttpSessionContextIntegrationFilter2() throws ServletException {
        setContext(NonSerializableSecurityContext.class);
    }

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpSession session = ((HttpServletRequest) req).getSession(false);
        if (session != null) {
            SecurityContext o = (SecurityContext) session.getAttribute(ACEGI_SECURITY_CONTEXT_KEY);
            if (o != null) {
                Authentication a = o.getAuthentication();
                if (a != null) {
                    if (isAuthInvalidated(a) || hasInvalidSessionSeed(a, session)) {
                        session.setAttribute(ACEGI_SECURITY_CONTEXT_KEY, null);
                    }
                }
            }
        }

        super.doFilter(req, res, chain);
    }

    private boolean isAuthInvalidated(Authentication authentication) {
        if (authentication.getPrincipal() instanceof InvalidatableUserDetails) {
            InvalidatableUserDetails ud = (InvalidatableUserDetails) authentication.getPrincipal();
            if (ud.isInvalid()) {
                // don't let Acegi see invalid security context
                return true;
            }
        }

        return false;
    }

    private boolean hasInvalidSessionSeed(Authentication authentication, HttpSession session) {
        if (UserSeedProperty.DISABLE_USER_SEED || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }

        User userFromSession;
        try {
            userFromSession = User.getById(authentication.getName(), false);
        } catch (IllegalStateException ise) {
            logger.warn("Encountered IllegalStateException trying to get a user. System init may not have completed yet. Invalidating user session.");
            return false;
        }
        if (userFromSession == null) {
            // no requirement for further test as there is no user inside
            return false;
        }

        // for case like recovering backup or other corner cases when the session was not populated by this version
        Object userSessionSeedObject = session.getAttribute(UserSeedProperty.USER_SESSION_SEED);
        String actualUserSessionSeed;
        if (userSessionSeedObject instanceof String) {
            actualUserSessionSeed = (String) userSessionSeedObject;
        } else {
            // the seed must be present AND be a string in the session
            return true;
        }

        UserSeedProperty userSeedProperty = userFromSession.getProperty(UserSeedProperty.class);
        if (userSeedProperty == null) {
            // if you want to filter out the user seed property, you should consider using the DISABLE_USER_SEED instead
            return true;
        }
        // no need to do a time-constant test here because all the information come from the server
        // in other words, there is no way for a user to brute-force those values
        boolean validSeed = actualUserSessionSeed.equals(userSeedProperty.getSeed());

        // if the authentication is no longer valid we need to remove it from the session
        return !validSeed;
    }
}
