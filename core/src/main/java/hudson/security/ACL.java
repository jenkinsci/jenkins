package hudson.security;

import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.Authentication;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.acls.sid.PrincipalSid;
import org.acegisecurity.acls.sid.Sid;
import org.acegisecurity.context.SecurityContextHolder;
import hudson.model.Hudson;

/**
 * Gate-keeper that controls access to Hudson's model objects.
 *
 * @author Kohsuke Kawaguchi
 * @see http://hudson.gotdns.com/wiki/display/HUDSON/Making+your+plugin+behave+in+secured+Hudson
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
        Authentication a = Hudson.getAuthentication();
        if(!hasPermission(a,p))
            throw new AccessDeniedException(a.toString()+" is missing "+p.name);
    }

    /**
     * Checks if the current security principal has this permission.
     *
     * @return false
     *      if the user doesn't have the permission.
     */
    public final boolean hasPermission(Permission p) {
        return hasPermission(Hudson.getAuthentication(),p);
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
    public static final Sid EVERYONE = new Sid() {};

    /**
     * {@link Sid} that represents the anonymous unauthenticated users.
     * <p>
     * {@link HudsonFilter} sets this up, so this sid remains the same
     * regardless of the current {@link SecurityRealm} in use.
     */
    public static final Sid ANONYMOUS = new PrincipalSid("anonymous");

    /**
     * {@link Sid} that represents the Hudson itself.
     * <p>
     * This is used when Hudson is performing computation for itself, instead
     * of acting on behalf of an user, such as doing builds.
     *
     * <p>
     * Technically speaking, this is probably a broken concept, because Hudson never
     * does anything on its own; for example, it builds a project because someone
     * configures it or someone triggers it, so ideally Hudson should be impersonating
     * that user when executing things.
     */
    public static final Authentication SYSTEM = new UsernamePasswordAuthenticationToken("SYSTEM","SYSTEM");
}
