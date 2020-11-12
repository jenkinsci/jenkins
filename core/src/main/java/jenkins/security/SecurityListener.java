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
import edu.umd.cs.findbugs.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

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
     * @since TODO
     */
    protected void authenticated2(@NonNull UserDetails details) {
        authenticated(org.acegisecurity.userdetails.UserDetails.fromSpring(details));
    }

    /**
     * @deprecated use {@link #authenticated2}
     */
    @Deprecated
    protected void authenticated(@NonNull org.acegisecurity.userdetails.UserDetails details) {}

    /**
     * Fired when a user tried to authenticate but failed.
     * In case the authentication method uses multiple layers to validate the credentials,
     * we do fire this event only when even the last layer failed to authenticate.
     * @param username the user
     * @see #authenticated2
     */
    protected void failedToAuthenticate(@NonNull String username){}

    /**
     * Fired when a user has logged in. Compared to authenticated, there is a notion of storage / cache.
     * Would be called after {@link #authenticated2}.
     * It should be called after the {@link SecurityContextHolder#getContext()}'s authentication is set.
     * @param username the user
     */
    protected void loggedIn(@NonNull String username){}

    /**
     * @since 2.161
     *
     * Fired after a new user account has been created and saved to disk.
     *
     * @param username the user
     */
    protected void userCreated(@NonNull String username) {}

    /**
     * Fired when a user has failed to log in.
     * Would be called after {@link #failedToAuthenticate}.
     * @param username the user
     */
    protected void failedToLogIn(@NonNull String username){}

    /**
     * Fired when a user logs out.
     * @param username the user
     */
    protected void loggedOut(@NonNull String username){}

    /**
     * @since TODO
     */
    public static void fireAuthenticated2(@NonNull UserDetails details) {
        if (LOGGER.isLoggable(Level.FINE)) {
            List<String> groups = new ArrayList<>();
            for (GrantedAuthority auth : details.getAuthorities()) {
                if (!auth.equals(SecurityRealm.AUTHENTICATED_AUTHORITY2)) {
                    groups.add(auth.getAuthority());
                }
            }
            LOGGER.log(Level.FINE, "authenticated: {0} {1}", new Object[] {details.getUsername(), groups});
        }
        for (SecurityListener l : all()) {
            l.authenticated2(details);
        }
    }

    /**
     * @deprecated use {@link #fireAuthenticated2}
     * @since 1.569
     */
    @Deprecated
    public static void fireAuthenticated(@NonNull org.acegisecurity.userdetails.UserDetails details) {
        fireAuthenticated2(details.toSpring());
    }

    /** @since 2.161 */
    public static void fireUserCreated(@NonNull String username) {
        LOGGER.log(Level.FINE, "new user created: {0}", username);
        for (SecurityListener l : all()) {
            l.userCreated(username);
        }
    }

    /** @since 1.569 */
    public static void fireFailedToAuthenticate(@NonNull String username) {
        LOGGER.log(Level.FINE, "failed to authenticate: {0}", username);
        for (SecurityListener l : all()) {
            l.failedToAuthenticate(username);
        }
    }

    /** @since 1.569 */
    public static void fireLoggedIn(@NonNull String username) {
        LOGGER.log(Level.FINE, "logged in: {0}", username);
        for (SecurityListener l : all()) {
            l.loggedIn(username);
        }
    }

    /** @since 1.569 */
    public static void fireFailedToLogIn(@NonNull String username) {
        LOGGER.log(Level.FINE, "failed to log in: {0}", username);
        for (SecurityListener l : all()) {
            l.failedToLogIn(username);
        }
    }

    /** @since 1.569 */
    public static void fireLoggedOut(@NonNull String username) {
        LOGGER.log(Level.FINE, "logged out: {0}", username);
        for (SecurityListener l : all()) {
            l.loggedOut(username);
        }
    }

    private static List<SecurityListener> all() {
        return ExtensionList.lookup(SecurityListener.class);
    }

}
