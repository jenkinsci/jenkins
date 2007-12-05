package hudson.security;

import org.acegisecurity.AuthenticationManager;
import hudson.model.Descriptor;

/**
 * {@link SecurityRealm} implementation that uses LDAP for authentication.
 * 
 * @author Kohsuke Kawaguchi
 */
public class LDAPSecurityRealm extends SecurityRealm {
    public AuthenticationManager createAuthenticationManager() {
        throw new UnsupportedOperationException();
    }

    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private static final class DescriptorImpl extends Descriptor<SecurityRealm> {
        private DescriptorImpl() {
            super(LDAPSecurityRealm.class);
        }

        public String getDisplayName() {
            return "LDAP";
        }
    }
}
