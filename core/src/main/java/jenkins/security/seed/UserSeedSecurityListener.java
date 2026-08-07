/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

package jenkins.security.seed;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.User;
import jakarta.servlet.http.HttpSession;
import jenkins.security.SecurityListener;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Inject the user seed inside the session (when there is an existing request) as part of the re-authentication mechanism
 * provided by {@link hudson.security.HttpSessionContextIntegrationFilter2} and {@link UserSeedProperty}.
 */
@Restricted(NoExternalUse.class)
@Extension(ordinal = Integer.MAX_VALUE)
public class UserSeedSecurityListener extends SecurityListener {
    @Override
    protected void loggedIn(@NonNull String username) {
        putUserSeedInSession(username, true);
    }

    @Override
    protected void authenticated2(@NonNull UserDetails details) {
        putUserSeedInSession(details.getUsername(), false);
    }

    private static void putUserSeedInSession(String username, boolean overwriteSessionSeed) {
        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req == null) {
            // expected case: CLI
            // But also HudsonPrivateSecurityRealm because of a redirect from Spring Security, the request is not a Stapler one
            return;
        }

        HttpSession session = req.getSession(false);
        if (session == null) {
            // expected case: CLI through CLIRegisterer
            return;
        }

        if (!UserSeedProperty.DISABLE_USER_SEED) {
            if (!overwriteSessionSeed && session.getAttribute(UserSeedProperty.USER_SESSION_SEED) != null) {
                return;
            }

            User user = User.getById(username, true);

            UserSeedProperty userSeed = user.getProperty(UserSeedProperty.class);
            if (userSeed == null) {
                // if you want to filter out the user seed property, you should consider using the DISABLE_USER_SEED instead
                return;
            }
            String sessionSeed = userSeed.getSeed();
            // normally invalidated before
            session.setAttribute(UserSeedProperty.USER_SESSION_SEED, sessionSeed);
        }
    }
}
