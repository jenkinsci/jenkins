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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.SecurityListener;
import jenkins.security.seed.UserSeedProperty;
import jenkins.util.SystemProperties;
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

    @SuppressFBWarnings(value = "HARD_CODE_PASSWORD", justification = "This is a password parameter, not a password")
    public AuthenticationProcessingFilter2(String authenticationGatewayUrl) {
        setRequiresAuthenticationRequestMatcher(new AntPathRequestMatcher("/" + authenticationGatewayUrl, "POST"));
        // Jenkins/login.jelly & SetupWizard/authenticate-security-token.jelly
        setUsernameParameter("j_username");
        setPasswordParameter("j_password");
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) throws IOException, ServletException {
        if (SystemProperties.getInteger(SecurityRealm.class.getName() + ".sessionFixationProtectionMode", 1) == 2) {
            // While use of SessionFixationProtectionStrategy is the canonical Spring Security approach, it may not be compatible with some security realms, so offer this alternative
            request.getSession().invalidate();
            request.getSession(true);
        }
        super.successfulAuthentication(request, response, chain, authResult);
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
