package hudson.security;

/**
 * Object that has an {@link ACL}
 *
 * @since 1.220
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
    void checkPermission(Permission permission);

    /**
     * Convenient short-cut for {@code getACL().hasPermission(permission)}
     */
    boolean hasPermission(Permission permission);

}
