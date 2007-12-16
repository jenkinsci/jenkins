package hudson.security;

import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.DescriptorList;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationManager;
import org.springframework.context.ApplicationContext;

import java.util.Map;

/**
 * Pluggable security realm that connects external user database to Hudson.
 *
 * <p>
 * New implementations should be registered to {@link #LIST}.
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

    /**
     * Picks up the instance of the given type from the spring context.
     * If there are multiple beans of the same type or if there are none,
     * this method treats that as an {@link IllegalArgumentException}.
     *
     * This method is intended to be used to pick up a Acegi object from
     * spring once the bean definition file is parsed.
     */
    protected static <T> T findBean(Class<T> type, ApplicationContext context) {
        Map m = context.getBeansOfType(type);
        switch(m.size()) {
        case 0:
            throw new IllegalArgumentException("No beans of "+type+" are defined");
        case 1:
            return type.cast(m.values().iterator().next());
        default:
            throw new IllegalArgumentException("Multiple beans of "+type+" are defined: "+m);            
        }
    }

    /**
     * Singleton constant that represents "no authentication."
     */
    public static final SecurityRealm NO_AUTHENTICATION = new None();

    private static class None extends SecurityRealm {
        public AuthenticationManager createAuthenticationManager() {
            return new AuthenticationManager() {
                public Authentication authenticate(Authentication authentication) {
                    return authentication;
                }
            };
        }

        /**
         * This special instance is not configurable explicitly,
         * so it doesn't have a descriptor.
         */
        public Descriptor<SecurityRealm> getDescriptor() {
            return null;
        }

        /**
         * Maintain singleton semantics.
         */
        private Object readResolve() {
            return NO_AUTHENTICATION;
        }
    }

    /**
     * All registered {@link SecurityRealm} implementations.
     */
    public static final DescriptorList<SecurityRealm> LIST = new DescriptorList<SecurityRealm>(
        LegacySecurityRealm.DESCRIPTOR,
        HudsonPrivateSecurityRealm.DescriptorImpl.INSTANCE,
        LDAPSecurityRealm.DESCRIPTOR
    );
}
