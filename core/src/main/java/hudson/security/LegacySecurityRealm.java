package hudson.security;

import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.springframework.web.context.WebApplicationContext;
import org.kohsuke.stapler.StaplerRequest;
import groovy.lang.Binding;
import hudson.model.Descriptor;
import hudson.util.spring.BeanBuilder;
import net.sf.json.JSONObject;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;

/**
 * {@link SecurityRealm} that accepts {@link ContainerAuthentication} object
 * without any check (that is, by assuming that the such token is
 * already authenticated by the container.)
 * 
 * @author Kohsuke Kawaguchi
 */
public final class LegacySecurityRealm extends SecurityRealm implements AuthenticationManager {
    public SecurityComponents createSecurityComponents() {
        return new SecurityComponents(this);
    }

    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if(authentication instanceof ContainerAuthentication)
            return authentication;
        else
            return null;
    }

    /**
     * To have the username/password authenticated by the container,
     * submit the form to the URL defined by the servlet spec.
     */
    @Override
    public String getAuthenticationGatewayUrl() {
        return "j_security_check";
    }

    @Override
    public String getLoginUrl() {
        return "loginEntry";
    }

    /**
     * Filter to run for the LegacySecurityRealm is the
     * ChainServletFilter legacy from /WEB-INF/security/SecurityFilters.groovy.
     */
    @Override
    public Filter createFilter(FilterConfig filterConfig) {
        Binding binding = new Binding();
        SecurityComponents sc = this.createSecurityComponents();
        binding.setVariable("securityComponents", sc);
        BeanBuilder builder = new BeanBuilder();
        builder.parse(filterConfig.getServletContext().getResourceAsStream("/WEB-INF/security/SecurityFilters.groovy"),binding);
        
        WebApplicationContext context = builder.createApplicationContext();
        
        return (Filter) context.getBean("legacy");
    }

    public Descriptor<SecurityRealm> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final Descriptor<SecurityRealm> DESCRIPTOR = new Descriptor<SecurityRealm>(LegacySecurityRealm.class) {
        public SecurityRealm newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new LegacySecurityRealm();
        }

        public String getDisplayName() {
            return Messages.LegacySecurityRealm_Displayname();
        }

        public String getHelpFile() {
            return "/help/security/container-realm.html";
        }
    };

    static {
        LIST.add(DESCRIPTOR);
    }
}
