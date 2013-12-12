/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

package jenkins.security;

import hudson.ExtensionPoint;
import hudson.security.SecurityRealm;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Listener notified of various significant events related to security.
 * @since TODO
 */
public abstract class SecurityListener implements ExtensionPoint {
    
    private static final Logger LOGGER = Logger.getLogger(SecurityListener.class.getName());

    // TODO should these methods take User rather than Authentication? Higher level, perhaps more convenient, perhaps not.

    /**
     * Fired when a user logs in.
     * @param user the user authentication
     */
    protected abstract void loggedIn(@Nonnull Authentication user);

    /**
     * Fired when a user logs out.
     * @param user the user authentication
     */
    protected abstract void loggedOut(@Nonnull Authentication user);

    // TODO event for new user signed up (e.g. in HudsonPrivateSecurityRealm)
    // TODO event for CAPTCHA failure
    // TODO event for authenticated via CLI
    // TODO event for authenticated via API token
    // TODO event for permission denied exception thrown (and/or caught at top level)

    @Restricted(NoExternalUse.class)
    public static void fireLoggedIn(@Nonnull Authentication user) {
        if (LOGGER.isLoggable(Level.FINE)) {
            StringBuilder b = new StringBuilder("logged in: ").append(user.getName());
            for (GrantedAuthority auth : user.getAuthorities()) {
                if (auth.equals(SecurityRealm.AUTHENTICATED_AUTHORITY)) {
                    continue;
                }
                b.append(' ').append(auth.getAuthority());
            }
            LOGGER.log(Level.FINE, b.toString());
        }
        for (SecurityListener l : all()) {
            l.loggedIn(user);
        }
    }

    @Restricted(NoExternalUse.class)
    public static void fireLoggedOut(@Nonnull Authentication user) {
        LOGGER.log(Level.FINE, "logged out: {0}", user.getName());
        for (SecurityListener l : all()) {
            l.loggedOut(user);
        }
    }

    private static List<SecurityListener> all() {
        return Jenkins.getInstance().getExtensionList(SecurityListener.class);
    }

}
