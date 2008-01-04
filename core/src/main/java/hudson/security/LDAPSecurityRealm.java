package hudson.security;

import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.MockAuthenticationManager;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.util.spring.BeanBuilder;
import net.sf.json.JSONObject;
import groovy.lang.Binding;

/**
 * {@link SecurityRealm} implementation that uses LDAP for authentication.
 * 
 * @author Kohsuke Kawaguchi
 */
public class LDAPSecurityRealm extends SecurityRealm {
    /**
     * LDAP to connect to, and root DN.
     * String like "ldap://monkeymachine:389/dc=acegisecurity,dc=org"
     */
    public final String providerUrl;

    @DataBoundConstructor
    public LDAPSecurityRealm(String providerUrl) {
        this.providerUrl = providerUrl;
    }

    public AuthenticationManager createAuthenticationManager() {
        Binding binding = new Binding();
        binding.setVariable("it", this);

        BeanBuilder builder = new BeanBuilder();
        builder.parse(Hudson.getInstance().servletContext.getResourceAsStream("/WEB-INF/security/LDAPBindSecurityRealm.groovy"),binding);
        return findBean(AuthenticationManager.class,builder.createApplicationContext());
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
