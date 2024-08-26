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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.User;
import hudson.model.View;
import hudson.model.ViewDescriptor;
import hudson.model.ViewGroup;
import hudson.remoting.Callable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import jenkins.security.NonSerializableSecurityContext;
import jenkins.security.NotReallyRoleSensitiveCallable;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.acegisecurity.acls.sid.Sid;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

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
    public final void checkPermission(@NonNull Permission p) {
        Authentication a = Jenkins.getAuthentication2();
        if (a.equals(SYSTEM2)) { // perhaps redundant given check in AccessControlled
            return;
        }
        if (!hasPermission2(a, p)) {
            while (!p.enabled && p.impliedBy != null) {
                p = p.impliedBy;
            }
            throw new AccessDeniedException3(a, p);
        }
    }

    /**
     * Checks if the current security principal has one of the supplied permissions.
     *
     * This is just a convenience function.
     *
     * @throws AccessDeniedException
     *      if the user doesn't have the permission.
     * @throws IllegalArgumentException
     *      if no permissions are provided
     *
     * @since 2.222
     */
    public final void checkAnyPermission(@NonNull Permission... permissions) {
        if (permissions.length == 0) {
            throw new IllegalArgumentException("At least one permission must be provided");
        }

        boolean failed = !hasAnyPermission(permissions);

        Authentication authentication = Jenkins.getAuthentication2();
        if (failed) { // we know that none of the permissions are granted
            Set<Permission> enabledPermissions = new LinkedHashSet<>();
            for (Permission p : permissions) {
                while (!p.enabled && p.impliedBy != null) {
                    p = p.impliedBy;
                }
                enabledPermissions.add(p);
            }
            String permissionsDisplayName = enabledPermissions.stream()
                    .map(p -> p.group.title + "/" + p.name)
                    .collect(Collectors.joining(", "));

            String errorMessage;
            if (enabledPermissions.size() == 1) {
                errorMessage = Messages.AccessDeniedException2_MissingPermission(authentication.getName(), permissionsDisplayName);
            } else {
                errorMessage = Messages.AccessDeniedException_MissingPermissions(authentication.getName(), permissionsDisplayName);
            }

            throw new AccessDeniedException(errorMessage);
        }
    }

    /**
     * Checks if the current security principal has this permission.
     *
     * @return false
     *      if the user doesn't have the permission.
     */
    public final boolean hasPermission(@NonNull Permission p) {
        Authentication a = Jenkins.getAuthentication2();
        if (a.equals(SYSTEM2)) { // perhaps redundant given check in AccessControlled
            return true;
        }
        return hasPermission2(a, p);
    }

    /**
     * Checks if the current security principal has any of the permissions.
     *
     * @return {@code false}
     *      if the user doesn't have one of the required permissions.
     *
     * @throws IllegalArgumentException
     *      if no permissions are provided
     */
    public final boolean hasAnyPermission(@NonNull Permission... permissions) {
        if (permissions.length == 0) {
            throw new IllegalArgumentException("At least one permission must be provided");
        }

        Authentication a = Jenkins.getAuthentication2();
        if (a.equals(SYSTEM2)) {
            return true;
        }

        for (Permission permission : permissions) {
            if (hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the given principle has the given permission.
     *
     * <p>
     * Note that {@link #SYSTEM2} can be passed in as the authentication parameter,
     * in which case you should probably just assume it has every permission.
     * @since 2.266
     */
    public boolean hasPermission2(@NonNull Authentication a, @NonNull Permission permission) {
        if (Util.isOverridden(ACL.class, getClass(), "hasPermission", org.acegisecurity.Authentication.class, Permission.class)) {
            return hasPermission(org.acegisecurity.Authentication.fromSpring(a), permission);
        } else {
            throw new AbstractMethodError("implement hasPermission2");
        }
    }

    /**
     * @deprecated use {@link #hasPermission2}
     */
    @Deprecated
    public boolean hasPermission(@NonNull org.acegisecurity.Authentication a, @NonNull Permission permission) {
        return hasPermission2(a.toSpring(), permission);
    }

    /**
     * Creates a simple {@link ACL} implementation based on a “single-abstract-method” easily implemented via lambda syntax.
     * @param impl the implementation of {@link ACL#hasPermission2(Authentication, Permission)}
     * @return an adapter to that lambda
     * @since 2.266
     */
    public static ACL lambda2(final BiFunction<Authentication, Permission, Boolean> impl) {
        return new ACL() {
            @Override
            public boolean hasPermission2(Authentication a, Permission permission) {
                return impl.apply(a, permission);
            }
        };
    }

    /**
     * @deprecated use {@link #lambda2}
     * @since 2.105
     */
    @Deprecated
    public static ACL lambda(final BiFunction<org.acegisecurity.Authentication, Permission, Boolean> impl) {
        return new ACL() {
            @Override
            public boolean hasPermission(org.acegisecurity.Authentication a, Permission permission) {
                return impl.apply(a, permission);
            }
        };
    }

    /**
     * Checks if the current security principal has the permission to create top level items within the specified
     * item group.
     * <p>
     * This is just a convenience function.
     * @param c the container of the item.
     * @param d the descriptor of the item to be created.
     * @throws AccessDeniedException
     *      if the user doesn't have the permission.
     * @since 1.607
     */
    public final void checkCreatePermission(@NonNull ItemGroup c,
                                            @NonNull TopLevelItemDescriptor d) {
        Authentication a = Jenkins.getAuthentication2();
        if (a.equals(SYSTEM2)) {
            return;
        }
        if (!hasCreatePermission2(a, c, d)) {
            throw new AccessDeniedException(Messages.AccessDeniedException2_MissingPermission(a.getName(),
                    Item.CREATE.group.title + "/" + Item.CREATE.name + "/" + d.getDisplayName()));
        }
    }
    /**
     * Checks if the given principal has the permission to create top level items within the specified item group.
     * <p>
     * Note that {@link #SYSTEM2} can be passed in as the authentication parameter,
     * in which case you should probably just assume it can create anything anywhere.
     * @param a the principal.
     * @param c the container of the item.
     * @param d the descriptor of the item to be created.
     * @return false
     *      if the user doesn't have the permission.
     * @since 2.266
     */

    public boolean hasCreatePermission2(@NonNull Authentication a, @NonNull ItemGroup c,
                                       @NonNull TopLevelItemDescriptor d) {
        if (Util.isOverridden(ACL.class, getClass(), "hasCreatePermission", org.acegisecurity.Authentication.class, ItemGroup.class, TopLevelItemDescriptor.class)) {
            return hasCreatePermission(org.acegisecurity.Authentication.fromSpring(a), c, d);
        } else {
            return true;
        }
    }

    /**
     * @deprecated use {@link #hasCreatePermission2(Authentication, ItemGroup, TopLevelItemDescriptor)}
     * @since 1.607
     */
    @Deprecated
    public boolean hasCreatePermission(@NonNull org.acegisecurity.Authentication a, @NonNull ItemGroup c,
                                       @NonNull TopLevelItemDescriptor d) {
        return hasCreatePermission2(a.toSpring(), c, d);
    }

    /**
     * Checks if the current security principal has the permission to create views within the specified view group.
     * <p>
     * This is just a convenience function.
     *
     * @param c the container of the item.
     * @param d the descriptor of the view to be created.
     * @throws AccessDeniedException if the user doesn't have the permission.
     * @since 1.607
     */
    public final void checkCreatePermission(@NonNull ViewGroup c,
                                            @NonNull ViewDescriptor d) {
        Authentication a = Jenkins.getAuthentication2();
        if (a.equals(SYSTEM2)) {
            return;
        }
        if (!hasCreatePermission2(a, c, d)) {
            throw new AccessDeniedException(Messages.AccessDeniedException2_MissingPermission(a.getName(),
                    View.CREATE.group.title + "/" + View.CREATE.name + "/" + d.getDisplayName()));
        }
    }

    /**
     * Checks if the given principal has the permission to create views within the specified view group.
     * <p>
     * Note that {@link #SYSTEM2} can be passed in as the authentication parameter,
     * in which case you should probably just assume it can create anything anywhere.
     * @param a the principal.
     * @param c the container of the view.
     * @param d the descriptor of the view to be created.
     * @return false
     *      if the user doesn't have the permission.
     * @since 2.266
     */
    public boolean hasCreatePermission2(@NonNull Authentication a, @NonNull ViewGroup c,
                                       @NonNull ViewDescriptor d) {
        if (Util.isOverridden(ACL.class, getClass(), "hasCreatePermission", org.acegisecurity.Authentication.class, ViewGroup.class, ViewDescriptor.class)) {
            return hasCreatePermission(org.acegisecurity.Authentication.fromSpring(a), c, d);
        } else {
            return true;
        }
    }

    /**
     * @deprecated use {@link #hasCreatePermission2(Authentication, ItemGroup, TopLevelItemDescriptor)}
     * @since 2.37
     */
    @Deprecated
    public boolean hasCreatePermission(@NonNull org.acegisecurity.Authentication a, @NonNull ViewGroup c,
                                       @NonNull ViewDescriptor d) {
        return hasCreatePermission2(a.toSpring(), c, d);
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

    static final Sid[] AUTOMATIC_SIDS = new Sid[]{EVERYONE, ANONYMOUS};

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
     * @since 2.266
     */
    public static final Authentication SYSTEM2 = new UsernamePasswordAuthenticationToken(SYSTEM_USERNAME, "SYSTEM");

    /**
     * @deprecated use {@link #SYSTEM2}
     */
    @Deprecated
    public static final org.acegisecurity.Authentication SYSTEM = new org.acegisecurity.providers.UsernamePasswordAuthenticationToken((UsernamePasswordAuthenticationToken) SYSTEM2);

    /**
     * Changes the {@link Authentication} associated with the current thread
     * to the specified one, and returns  the previous security context.
     *
     * <p>
     * When the impersonation is over, be sure to restore the previous authentication
     * via {@code SecurityContextHolder.setContext(returnValueFromThisMethod)};
     * or just use {@link #impersonate2(Authentication, Runnable)}.
     *
     * <p>
     * We need to create a new {@link SecurityContext} instead of {@link SecurityContext#setAuthentication(Authentication)}
     * because the same {@link SecurityContext} object is reused for all the concurrent requests from the same session.
     * @since 2.266
     * @deprecated use try with resources and {@link #as2(Authentication)}
     */
    @Deprecated
    public static @NonNull SecurityContext impersonate2(@NonNull Authentication auth) {
        SecurityContext old = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext(new NonSerializableSecurityContext(auth));
        return old;
    }

    /**
     * @deprecated use {@link #impersonate2(Authentication)}
     * @since 1.462
     */
    @Deprecated
    public static @NonNull org.acegisecurity.context.SecurityContext impersonate(@NonNull org.acegisecurity.Authentication auth) {
        return org.acegisecurity.context.SecurityContext.fromSpring(impersonate2(auth.toSpring()));
    }

    /**
     * Safer variant of {@link #impersonate2(Authentication)} that does not require a finally-block.
     * @param auth authentication, such as {@link #SYSTEM2}
     * @param body an action to run with this alternate authentication in effect
     * @since 2.266
     * @deprecated use try with resources and {@link #as2(Authentication)}
     */
    @Deprecated
    public static void impersonate2(@NonNull Authentication auth, @NonNull Runnable body) {
        SecurityContext old = impersonate2(auth);
        try {
            body.run();
        } finally {
            SecurityContextHolder.setContext(old);
        }
    }

    /**
     * @deprecated use {@link #impersonate2(Authentication, Runnable)}
     * @since 1.509
     */
    @Deprecated
    public static void impersonate(@NonNull org.acegisecurity.Authentication auth, @NonNull Runnable body) {
        impersonate2(auth.toSpring(), body);
    }

    /**
     * Safer variant of {@link #impersonate2(Authentication)} that does not require a finally-block.
     * @param auth authentication, such as {@link #SYSTEM2}
     * @param body an action to run with this alternate authentication in effect (try {@link NotReallyRoleSensitiveCallable})
     * @since 2.266
     * @deprecated use try with resources and {@link #as2(Authentication)}
     */
    @Deprecated
    public static <V, T extends Exception> V impersonate2(Authentication auth, Callable<V, T> body) throws T {
        SecurityContext old = impersonate2(auth);
        try {
            return body.call();
        } finally {
            SecurityContextHolder.setContext(old);
        }
    }

    /**
     * @deprecated use {@link #impersonate2(Authentication, Callable)}
     * @since 1.587
     */
    @Deprecated
    public static <V, T extends Exception> V impersonate(org.acegisecurity.Authentication auth, Callable<V, T> body) throws T {
        return impersonate2(auth.toSpring(), body);
    }

    /**
     * Changes the {@link Authentication} associated with the current thread to the specified one and returns an
     * {@link AutoCloseable} that restores the previous security context.
     *
     * <p>
     * This makes impersonation much easier within code as it can now be used using the try with resources construct:
     * <pre>
     *     try (ACLContext ctx = ACL.as2(auth)) {
     *        ...
     *     }
     * </pre>
     * @param auth the new authentication.
     * @return the previous authentication context
     * @since 2.266
     */
    @NonNull
    public static ACLContext as2(@NonNull Authentication auth) {
        final ACLContext context = new ACLContext(SecurityContextHolder.getContext());
        SecurityContextHolder.setContext(new NonSerializableSecurityContext(auth));
        return context;
    }

    /**
     * @deprecated use {@link #as2(Authentication)}
     * @since 2.14
     */
    @Deprecated
    @NonNull
    public static ACLContext as(@NonNull org.acegisecurity.Authentication auth) {
        return as2(auth.toSpring());
    }

    /**
     * Changes the {@link Authentication} associated with the current thread to the specified one and returns an
     * {@link AutoCloseable} that restores the previous security context.
     *
     * <p>
     * This makes impersonation much easier within code as it can now be used using the try with resources construct:
     * <pre>
     *     try (ACLContext ctx = ACL.as2(auth)) {
     *        ...
     *     }
     * </pre>
     *
     * @param user the user to impersonate.
     * @return the previous authentication context
     * @since 2.14
     */
    @NonNull
    public static ACLContext as(@CheckForNull User user) {
        return as2(user == null ? Jenkins.ANONYMOUS2 : user.impersonate2());
    }

    /**
     * Checks if the given authentication is anonymous by checking its class.
     * @see Jenkins#ANONYMOUS2
     * @see AnonymousAuthenticationToken
     * @since 2.266
     */
    public static boolean isAnonymous2(@NonNull Authentication authentication) {
        //TODO use AuthenticationTrustResolver instead to be consistent through the application
        return authentication instanceof AnonymousAuthenticationToken;
    }

    /**
     * @deprecated use {@link #isAnonymous2}
     */
    @Deprecated
    public static boolean isAnonymous(@NonNull org.acegisecurity.Authentication authentication) {
        return isAnonymous2(authentication.toSpring());
    }

}
