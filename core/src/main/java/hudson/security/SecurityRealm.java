package hudson.security;

import hudson.ExtensionPoint;
import hudson.model.Describable;
import org.acegisecurity.AuthenticationManager;

/**
 * Pluggable security realm that connects external user database to Hudson.
 *
 * @author Kohsuke Kawaguchi
 * @sicne 1.160
 */
public abstract class SecurityRealm implements Describable<SecurityRealm>, ExtensionPoint {
    /**
     * Creates fully-configured {@link AuthenticationManager} that performs authentication
     * against the user realm. The implementation hides how such authentication manager
     * is configured.
     *
     * <p>
     * {@link AuthenticationManager} instantiation often depends on the user configuration
     * (for example, if the authentication is based on LDAP, the host name of the LDAP server
     * depends on the user configuration), and such configuration is expected to be
     * captured as instance variables of {@link SecurityRealm} implementation.
     */
    public abstract AuthenticationManager createAuthenticationManager();
}
