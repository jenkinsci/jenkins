package hudson.security;

import org.acegisecurity.AccessDeniedException;

/**
 * Object that has an {@link ACL}
 *
 * @since 1.220
 * @see http://hudson.gotdns.com/wiki/display/HUDSON/Making+your+plugin+behave+in+secured+Hudson
 */
public interface AccessControlled {
    /**
     * Obtains the ACL associated with this object.
     *
     * @return never null.
     */
    ACL getACL();

    /**
     * Convenient short-cut for {@code getACL().checkPermission(permission)}
     */
    void checkPermission(Permission permission) throws AccessDeniedException;

    /**
     * Convenient short-cut for {@code getACL().hasPermission(permission)}
     */
    boolean hasPermission(Permission permission);

}
