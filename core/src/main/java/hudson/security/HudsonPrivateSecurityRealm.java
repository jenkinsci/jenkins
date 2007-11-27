package hudson.security;

import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.User;
import hudson.util.spring.BeanBuilder;
import net.sf.json.JSONObject;
import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UserDetailsService;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.kohsuke.stapler.StaplerRequest;

/**
 * {@link SecurityRealm} that performs authentication by looking up {@link User}.
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class HudsonPrivateSecurityRealm extends SecurityRealm {
    @Override
    public AuthenticationManager createAuthenticationManager() {
        BeanBuilder builder = new BeanBuilder();
        builder.parse(Hudson.getInstance().servletContext.getResourceAsStream("/WEB-INF/security/HudsonPrivateSecurityRealm.groovy"));
        return findBean(AuthenticationManager.class,builder.createApplicationContext());
    }

    public Descriptor<SecurityRealm> getDescriptor() {
        return DescriptorImpl.INSTANCE;
    }

    /**
     * {@link UserDetailsService} that loads user information from {@link User} object. 
     */
    public static final class HudsonUserDetailsService implements UserDetailsService {
        public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
            User u = User.get(username, false);
            if(u==null)
                throw new UsernameNotFoundException("No such user: "+username);
            return u.asUserDetails();
        }
    }

    public static final class DescriptorImpl extends Descriptor<SecurityRealm> {
        public static final DescriptorImpl INSTANCE = new DescriptorImpl();

        private DescriptorImpl() {
            super(HudsonPrivateSecurityRealm.class);
        }

        public String getDisplayName() {
            return "Hudson's own user database";
        }

        public HudsonPrivateSecurityRealm newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new HudsonPrivateSecurityRealm();
        }
    }
}
