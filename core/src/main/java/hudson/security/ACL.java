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

import jenkins.security.NonSerializableSecurityContext;
import jenkins.model.Jenkins;
import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.acegisecurity.acls.sid.Sid;

/**
 * Gate-keeper that controls access to Hudson's model objects.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class ACL {
    /**
     * Checks if the current security principal has this permission.
     *
     * <p>
     * This is just a convenience function.
     *
     * @throws AccessDeniedException
     *      if the user doesn't have the permission.
     */
    public final void checkPermission(Permission p) {
        Authentication a = Jenkins.getAuthentication();
        if(!hasPermission(a,p))
            throw new AccessDeniedException2(a,p);
    }

    /**
     * Checks if the current security principal has this permission.
     *
     * @return false
     *      if the user doesn't have the permission.
     */
    public final boolean hasPermission(Permission p) {
        return hasPermission(Jenkins.getAuthentication(),p);
    }

    /**
     * Checks if the given principle has the given permission.
     *
     * <p>
     * Note that {@link #SYSTEM} can be passed in as the authentication parameter,
     * in which case you should probably just assume it has every permission.
     */
    public abstract boolean hasPermission(Authentication a, Permission permission);

    //
    // Sid constants
    //

    /**
     * Special {@link Sid} that represents "everyone", even including anonymous users.
     *
     * <p>
     * This doesn't need to be included in {@link Authentication#getAuthorities()},
     * but {@link ACL} is responsible for checking it nontheless, as if it was the
     * last entry in the granted authority.
     */
    public static final Sid EVERYONE = new Sid() {
        @Override
        public String toString() {
            return "EVERYONE";
        }
    };

    /**
     * {@link Sid} that represents the anonymous unauthenticated users.
     * <p>
     * {@link HudsonFilter} sets this up, so this sid remains the same
     * regardless of the current {@link SecurityRealm} in use.
     */
    public static final Sid ANONYMOUS = new PrincipalSid("anonymous");

    protected static final Sid[] AUTOMATIC_SIDS = new Sid[]{EVERYONE,ANONYMOUS};

    /**
     * {@link Sid} that represents the Hudson itself.
     * <p>
     * This is used when Hudson is performing computation for itself, instead
     * of acting on behalf of an user, such as doing builds.
     */
    public static final Authentication SYSTEM = new UsernamePasswordAuthenticationToken("SYSTEM","SYSTEM");

    /**
     * Changes the {@link Authentication} associated with the current thread
     * to the specified one, and returns  the previous security context.
     * 
     * <p>
     * When the impersonation is over, be sure to restore the previous authentication
     * via {@code SecurityContextHolder.setContext(returnValueFromThisMethod)};
     * or just use {@link #impersonate(Authentication,Runnable)}.
     * 
     * <p>
     * We need to create a new {@link SecurityContext} instead of {@link SecurityContext#setAuthentication(Authentication)}
     * because the same {@link SecurityContext} object is reused for all the concurrent requests from the same session.
     * @since 1.462
     */
    public static SecurityContext impersonate(Authentication auth) {
        SecurityContext old = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext(new NonSerializableSecurityContext(auth));
        return old;
    }

    /**
     * Safer variant of {@link #impersonate(Authentication)} that does not require a finally-block.
     * @param auth authentication, such as {@link #SYSTEM}
     * @param body an action to run with this alternate authentication in effect
     * @since 1.509
     */
    public static void impersonate(Authentication auth, Runnable body) {
        SecurityContext old = impersonate(auth);
        try {
            body.run();
        } finally {
            SecurityContextHolder.setContext(old);
        }
    }

}
