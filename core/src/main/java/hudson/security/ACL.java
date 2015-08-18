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

import javax.annotation.Nonnull;

import hudson.model.Item;
import hudson.remoting.Callable;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItemDescriptor;
import jenkins.security.NonSerializableSecurityContext;
import jenkins.model.Jenkins;
import jenkins.security.NotReallyRoleSensitiveCallable;
import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.acegisecurity.acls.sid.Sid;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

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
    public final void checkPermission(@Nonnull Permission p) {
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
    public final boolean hasPermission(@Nonnull Permission p) {
        return hasPermission(Jenkins.getAuthentication(),p);
    }

    /**
     * Checks if the given principle has the given permission.
     *
     * <p>
     * Note that {@link #SYSTEM} can be passed in as the authentication parameter,
     * in which case you should probably just assume it has every permission.
     */
    public abstract boolean hasPermission(@Nonnull Authentication a, @Nonnull Permission permission);

    /**
     * Checks if the current security principal has the permission to create top level items within the specified
     * item group.
     * <p>
     * This is just a convenience function.
     * @param c the container of the item.
     * @param d the descriptor of the item to be created.
     * @throws AccessDeniedException
     *      if the user doesn't have the permission.
     * @since TODO
     */
    public final void checkCreatePermission(@Nonnull ItemGroup c,
                                            @Nonnull TopLevelItemDescriptor d) {
        Authentication a = Jenkins.getAuthentication();
        if (!hasCreatePermission(a, c, d)) {
            throw new AccessDeniedException(Messages.AccessDeniedException2_MissingPermission(a.getName(),
                    Item.CREATE.group.title+"/"+Item.CREATE.name + Item.CREATE + "/" + d.getDisplayName()));
        }
    }
    /**
     * Checks if the given principal has the permission to create top level items within the specified item group.
     * <p>
     * Note that {@link #SYSTEM} can be passed in as the authentication parameter,
     * in which case you should probably just assume it can create anything anywhere.
     * @param a the principal.
     * @param c the container of the item.
     * @param d the descriptor of the item to be created.
     * @return false
     *      if the user doesn't have the permission.
     * @since TODO
     */
    public boolean hasCreatePermission(@Nonnull Authentication a, @Nonnull ItemGroup c,
                                       @Nonnull TopLevelItemDescriptor d) {
        return true;
    }

    //
    // Sid constants
    //

    /**
     * Special {@link Sid} that represents "everyone", even including anonymous users.
     *
     * <p>
     * This doesn't need to be included in {@link Authentication#getAuthorities()},
     * but {@link ACL} is responsible for checking it nonetheless, as if it was the
     * last entry in the granted authority.
     */
    public static final Sid EVERYONE = new Sid() {
        @Override
        public String toString() {
            return "EVERYONE";
        }
    };

    /**
     * The username for the anonymous user
     */
    @Restricted(NoExternalUse.class)
    public static final String ANONYMOUS_USERNAME = "anonymous";
    /**
     * {@link Sid} that represents the anonymous unauthenticated users.
     * <p>
     * {@link HudsonFilter} sets this up, so this sid remains the same
     * regardless of the current {@link SecurityRealm} in use.
     */
    public static final Sid ANONYMOUS = new PrincipalSid(ANONYMOUS_USERNAME);

    protected static final Sid[] AUTOMATIC_SIDS = new Sid[]{EVERYONE,ANONYMOUS};

    /**
     * The username for the system user
     */
    @Restricted(NoExternalUse.class)
    public static final String SYSTEM_USERNAME = "SYSTEM";

    /**
     * {@link Sid} that represents the Hudson itself.
     * <p>
     * This is used when Hudson is performing computation for itself, instead
     * of acting on behalf of an user, such as doing builds.
     */
    public static final Authentication SYSTEM = new UsernamePasswordAuthenticationToken(SYSTEM_USERNAME,"SYSTEM");

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
    public static @Nonnull SecurityContext impersonate(@Nonnull Authentication auth) {
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
    public static void impersonate(@Nonnull Authentication auth, @Nonnull Runnable body) {
        SecurityContext old = impersonate(auth);
        try {
            body.run();
        } finally {
            SecurityContextHolder.setContext(old);
        }
    }

    /**
     * Safer variant of {@link #impersonate(Authentication)} that does not require a finally-block.
     * @param auth authentication, such as {@link #SYSTEM}
     * @param body an action to run with this alternate authentication in effect (try {@link NotReallyRoleSensitiveCallable})
     * @since 1.587
     */
    public static <V,T extends Exception> V impersonate(Authentication auth, Callable<V,T> body) throws T {
        SecurityContext old = impersonate(auth);
        try {
            return body.call();
        } finally {
            SecurityContextHolder.setContext(old);
        }
    }

}
