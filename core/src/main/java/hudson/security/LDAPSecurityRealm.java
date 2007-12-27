package hudson.security;

import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.MockAuthenticationManager;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.model.Descriptor;
import net.sf.json.JSONObject;

/**
 * {@link SecurityRealm} implementation that uses LDAP for authentication.
 * 
 * @author Kohsuke Kawaguchi
 */
public class LDAPSecurityRealm extends SecurityRealm {
    public final String providerUrl;

    @DataBoundConstructor
    public LDAPSecurityRealm(String providerUrl) {
        this.providerUrl = providerUrl;
    }

    public AuthenticationManager createAuthenticationManager() {
        // TODO
        return new MockAuthenticationManager(true);
    }

    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    private static final class DescriptorImpl extends Descriptor<SecurityRealm> {
        private DescriptorImpl() {
            super(LDAPSecurityRealm.class);
        }

        public LDAPSecurityRealm newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(LDAPSecurityRealm.class,formData);
        }

        public String getDisplayName() {
            return "LDAP";
        }
    }

    static {
//        LIST.add(DESCRIPTOR);
    }
}
