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

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.security.SecurityRealm;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.userdetails.UserDetails;

/**
 * Listener notified of various significant events related to security.
 * @since 1.548
 */
public abstract class SecurityListener implements ExtensionPoint {
    
    private static final Logger LOGGER = Logger.getLogger(SecurityListener.class.getName());

    /**
     * Fired when a user was successfully authenticated using credentials. It could be password or any other credentials.
     * This might be via the web UI, or via REST (using API token or Basic), or CLI (remoting, auth, ssh)
     * or any other way plugins can propose.
     * @param details details of the newly authenticated user, such as name and groups.
     */
    protected void authenticated(@Nonnull UserDetails details){}

    /**
     * Fired when a user tried to authenticate but failed.
     * In case the authentication method uses multiple layers to validate the credentials,
     * we do fire this event only when even the last layer failed to authenticate.
     * @param username the user
     * @see #authenticated
     */
    protected void failedToAuthenticate(@Nonnull String username){}

    /**
     * Fired when a user has logged in. Compared to authenticated, there is a notion of storage / cache.
     * Would be called after {@link #authenticated}.
     * It should be called after the {@link org.acegisecurity.context.SecurityContextHolder#getContext()}'s authentication is set.
     * @param username the user
     */
    protected void loggedIn(@Nonnull String username){}

    /**
     * @since 2.161
     *
     * Fired after a new user account has been created and saved to disk.
     *
     * @param username the user
     */
    protected void userCreated(@Nonnull String username) {}

    /**
     * Fired when a user has failed to log in.
     * Would be called after {@link #failedToAuthenticate}.
     * @param username the user
     */
    protected void failedToLogIn(@Nonnull String username){}

    /**
     * Fired when a user logs out.
     * @param username the user
     */
    protected void loggedOut(@Nonnull String username){}

    /** @since 1.569 */
    public static void fireAuthenticated(@Nonnull UserDetails details) {
        if (LOGGER.isLoggable(Level.FINE)) {
            List<String> groups = new ArrayList<>();
            for (GrantedAuthority auth : details.getAuthorities()) {
                if (!auth.equals(SecurityRealm.AUTHENTICATED_AUTHORITY)) {
                    groups.add(auth.getAuthority());
                }
            }
            LOGGER.log(Level.FINE, "authenticated: {0} {1}", new Object[] {details.getUsername(), groups});
        }
        for (SecurityListener l : all()) {
            l.authenticated(details);
        }
    }

    /** @since 2.161 */
    public static void fireUserCreated(@Nonnull String username) {
        LOGGER.log(Level.FINE, "new user created: {0}", username);
        for (SecurityListener l : all()) {
            l.userCreated(username);
        }
    }

    /** @since 1.569 */
    public static void fireFailedToAuthenticate(@Nonnull String username) {
        LOGGER.log(Level.FINE, "failed to authenticate: {0}", username);
        for (SecurityListener l : all()) {
            l.failedToAuthenticate(username);
        }
    }

    /** @since 1.569 */
    public static void fireLoggedIn(@Nonnull String username) {
        LOGGER.log(Level.FINE, "logged in: {0}", username);
        for (SecurityListener l : all()) {
            l.loggedIn(username);
        }
    }

    /** @since 1.569 */
    public static void fireFailedToLogIn(@Nonnull String username) {
        LOGGER.log(Level.FINE, "failed to log in: {0}", username);
        for (SecurityListener l : all()) {
            l.failedToLogIn(username);
        }
    }

    /** @since 1.569 */
    public static void fireLoggedOut(@Nonnull String username) {
        LOGGER.log(Level.FINE, "logged out: {0}", username);
        for (SecurityListener l : all()) {
            l.loggedOut(username);
        }
    }

    private static List<SecurityListener> all() {
        return ExtensionList.lookup(SecurityListener.class);
    }

}
