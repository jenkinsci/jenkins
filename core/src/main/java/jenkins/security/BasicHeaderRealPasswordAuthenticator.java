/* Copyright 2004, 2005, 2006 Acegi Technology Pty Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jenkins.security;

import hudson.Extension;
import jenkins.ExtensionFilter;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.providers.anonymous.AnonymousAuthenticationToken;
import org.acegisecurity.ui.AuthenticationDetailsSource;
import org.acegisecurity.ui.AuthenticationDetailsSourceImpl;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;

import static java.util.logging.Level.*;

/**
 * Checks if the password given in the BASIC header matches the user's actual password,
 * as opposed to other pseudo-passwords like API tokens.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.576
 */
@Extension
public class BasicHeaderRealPasswordAuthenticator extends BasicHeaderAuthenticator {
    private AuthenticationDetailsSource authenticationDetailsSource = new AuthenticationDetailsSourceImpl();

    @Override
    public Authentication authenticate(HttpServletRequest req, HttpServletResponse rsp, String username, String password) throws IOException, ServletException {
        if (DISABLE)
            return null;

        if (!authenticationIsRequired(username))
            return null;

        UsernamePasswordAuthenticationToken authRequest =
                new UsernamePasswordAuthenticationToken(username, password);
        authRequest.setDetails(authenticationDetailsSource.buildDetails(req));

        try {
            Authentication a = Jenkins.getInstance().getSecurityRealm().getSecurityComponents().manager.authenticate(authRequest);
            // Authentication success
            LOGGER.log(FINER, "Authentication success: {0}", a);
            return a;
        } catch (AuthenticationException failed) {
            // Authentication failed
            LOGGER.log(FINER, "Authentication request for user: {0} failed: {1}", new Object[]{username,failed});
            return null;
        }
    }

    // taken from BasicProcessingFilter.java
    protected boolean authenticationIsRequired(String username) {
        // Only reauthenticate if username doesn't match SecurityContextHolder and user isn't authenticated
        // (see SEC-53)
        Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();

        if(existingAuth == null || !existingAuth.isAuthenticated()) {
            return true;
        }

        // Limit username comparison to providers which use usernames (ie UsernamePasswordAuthenticationToken)
        // (see SEC-348)

        if (existingAuth instanceof UsernamePasswordAuthenticationToken && !existingAuth.getName().equals(username)) {
            return true;
        }

        // Handle unusual condition where an AnonymousAuthenticationToken is already present
        // This shouldn't happen very often, as BasicProcessingFitler is meant to be earlier in the filter
        // chain than AnonymousProcessingFilter. Nevertheless, presence of both an AnonymousAuthenticationToken
        // together with a BASIC authentication request header should indicate reauthentication using the
        // BASIC protocol is desirable. This behaviour is also consistent with that provided by form and digest,
        // both of which force re-authentication if the respective header is detected (and in doing so replace
        // any existing AnonymousAuthenticationToken). See SEC-610.
        if (existingAuth instanceof AnonymousAuthenticationToken) {
            return true;
        }

        return false;
    }

    private static final Logger LOGGER = Logger.getLogger(BasicHeaderRealPasswordAuthenticator.class.getName());

    /**
     * Legacy property to disable the real password support.
     * Now that this is an extension, {@link ExtensionFilter} is a better way to control this.
     */
    public static boolean DISABLE = Boolean.getBoolean("jenkins.security.ignoreBasicAuth");
}
