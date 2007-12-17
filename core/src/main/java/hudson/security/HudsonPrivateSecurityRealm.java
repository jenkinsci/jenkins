package hudson.security;

import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.User;
import hudson.util.spring.BeanBuilder;
import net.sf.json.JSONObject;
import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.GrantedAuthorityImpl;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UserDetailsService;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * {@link SecurityRealm} that performs authentication by looking up {@link User}.
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
     * Creates an user account.
     */
    public void doCreateAccount(StaplerRequest req, StaplerResponse rsp) throws IOException {
        rsp.getWriter().println(
           validateCaptcha(req.getParameter("captcha"))
        );
    }

    // TODO
    private static final GrantedAuthority[] TEST_AUTHORITY = {new GrantedAuthorityImpl("authenticated")};

    /**
     * Returns the {@link UserDetails} view of the User object.
     * <p>
     * This interface is implemented by a separate object to avoid having confusing methods
     * at the {@link User} class level.
     */
    private static final class UserDetailsImpl implements UserDetails {
        private final User user;

        private UserDetailsImpl(User user) {
            this.user = user;
        }

        public GrantedAuthority[] getAuthorities() {
            // TODO
            return TEST_AUTHORITY;
        }

        public String getPassword() {
            // TODO
            return user.getId();
        }

        public String getUsername() {
            return user.getId();
        }

        public boolean isAccountNonExpired() {
            return true;
        }

        public boolean isAccountNonLocked() {
            return true;
        }

        public boolean isCredentialsNonExpired() {
            return true;
        }

        public boolean isEnabled() {
            // TODO: if password is not set, don't allow login
            return true;
        }
    }

    /**
     * {@link UserDetailsService} that loads user information from {@link User} object. 
     */
    public static final class HudsonUserDetailsService implements UserDetailsService {
        public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
            User u = User.get(username, false);
            if(u==null)
                throw new UsernameNotFoundException("No such user: "+username);
            return new UserDetailsImpl(u);
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

        public String getHelpFile() {
            return "/help/security/private-realm.html"; 
        }

        public HudsonPrivateSecurityRealm newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new HudsonPrivateSecurityRealm();
        }
    }
}
