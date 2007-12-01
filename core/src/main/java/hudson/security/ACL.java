package hudson.security;

import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.Authentication;
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
}
