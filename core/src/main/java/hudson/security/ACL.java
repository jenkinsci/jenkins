package hudson.security;

import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.Authentication;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.acegisecurity.acls.sid.Sid;
import org.acegisecurity.context.SecurityContextHolder;

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
        Authentication a = SecurityContextHolder.getContext().getAuthentication();

        if(!hasPermission(a,p))
            throw new AccessDeniedException(a.toString()+" is missing "+p.name);
    }

    /**
     * Checks if the given principle has the given permission.
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
    public static final Sid EVERYONE = new Sid() {};

    /**
     * {@link Sid} that represents the anonymous unauthenticated users.
     * <p>
     * {@link HudsonFilter} sets this up, so this sid remains the same
     * regardless of the current {@link SecurityRealm} in use.
     */
    public static final Sid ANONYMOUS = new PrincipalSid("anonymous");
}
