package hudson.security;

import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.Authentication;

/**
 * {@link AccessDeniedException} with more information.
 * @author Kohsuke Kawaguchi
 */
public class AccessDeniedException2 extends AccessDeniedException {
    /**
     * This object represents the user being authenticated.
     */
    public final Authentication authentication;

    /**
     * This object represents the permission that the user needed.
     */
    public final Permission permission;

    public AccessDeniedException2(Authentication authentication, Permission permission) {
        this(null,authentication,permission);
    }

    public AccessDeniedException2(Throwable t, Authentication authentication, Permission permission) {
        super(Messages.AccessDeniedException2_MissingPermission(authentication.getName(),permission.name), t);
        this.authentication = authentication;
        this.permission = permission;
    }
}
