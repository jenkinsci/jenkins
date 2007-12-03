package hudson.security;

import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import hudson.model.Descriptor;

/**
 * {@link SecurityRealm} that accepts {@link ContainerAuthentication} object
 * without any check (that is, by assuming that the such token is
 * already authenticated by the container.)
 * @author Kohsuke Kawaguchi
 */
public class LegacySecurityRealm extends SecurityRealm implements AuthenticationManager {
    public AuthenticationManager createAuthenticationManager() {
        return this;
    }

    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if(authentication instanceof ContainerAuthentication)
            return authentication;
        else
            return null;
    }

    public Descriptor<SecurityRealm> getDescriptor() {
        return DESCRIPTOR;
    }

    private static final Descriptor<SecurityRealm> DESCRIPTOR = new Descriptor<SecurityRealm>(LegacySecurityRealm.class) {
        public String getDisplayName() {
            return "Legacy mode"; // TODO
        }
    };

}
