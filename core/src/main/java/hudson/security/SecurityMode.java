package hudson.security;

/**
 * What security enforcement does Hudson do?
 *
 * @author Kohsuke Kawaguchi
 */
public enum SecurityMode {
    /**
     * None. Anyone can make any changes. 
     */
    UNSECURED,
    /**
     * Legacy "secure mode."
     * <p>
     * In this model, an user is either admin or not. An admin user
     * can do anything, and non-admin user can only browse.
     * Authentication is performed by the container.
     * <p>
     * This is the only secured mode of Hudson up to 1.160.
     * This is maintained only for backward compatibility. 
     */
    LEGACY,
    /**
     * Security-enabled mode implemented through Acegi.
     */
    SECURED
}
