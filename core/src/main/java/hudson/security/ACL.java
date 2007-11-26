package hudson.security;

import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.Authentication;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class ACL {
    /**
     * Checks if the current security principal has this permission.
     *
     * @throws AccessDeniedException
     *      if the user doesn't have the permission.
     */
    public abstract void checkPermission(Permission p);

    public abstract boolean hasPermission(Authentication a, Permission permission);
}
