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

import static java.util.logging.Level.FINER;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;
import jenkins.ExtensionFilter;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

/**
 * Checks if the password given in the BASIC header matches the user's actual password,
 * as opposed to other pseudo-passwords like API tokens.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.576
 */
@Restricted(NoExternalUse.class)
@Extension
public class BasicHeaderRealPasswordAuthenticator extends BasicHeaderAuthenticator {
    private AuthenticationDetailsSource authenticationDetailsSource = new WebAuthenticationDetailsSource();

    @Override
    public Authentication authenticate2(HttpServletRequest req, HttpServletResponse rsp, String username, String password) throws IOException, ServletException {
        if (DISABLE)
            return null;

        UsernamePasswordAuthenticationToken authRequest =
                new UsernamePasswordAuthenticationToken(username, password);
        authRequest.setDetails(authenticationDetailsSource.buildDetails(req));

        try {
            Authentication a = Jenkins.get().getSecurityRealm().getSecurityComponents().manager2.authenticate(authRequest);
            // Authentication success
            LOGGER.log(FINER, "Authentication success: {0}", a);
            return a;
        } catch (AuthenticationException failed) {
            // Authentication failed
            LOGGER.log(FINER, "Authentication request for user: {0} failed: {1}", new Object[]{username, failed});
            return null;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(BasicHeaderRealPasswordAuthenticator.class.getName());

    /**
     * Legacy property to disable the real password support.
     * Now that this is an extension, {@link ExtensionFilter} is a better way to control this.
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static boolean DISABLE = SystemProperties.getBoolean("jenkins.security.ignoreBasicAuth");
}
