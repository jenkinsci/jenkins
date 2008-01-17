package hudson.security;

import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.util.Scrambler;
import hudson.util.Protector;
import hudson.util.spring.BeanBuilder;
import hudson.Util;
import hudson.security.HudsonPrivateSecurityRealm.Details;
import hudson.tasks.Mailer;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.DisabledException;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.GrantedAuthorityImpl;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UserDetailsService;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.Stapler;

import javax.servlet.ServletException;
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
    public void doCreateAccount(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        // form field validation
        // this pattern needs to be generalized and moved to stapler
        SignupInfo si = new SignupInfo();
        req.bindParameters(si);

        if(!validateCaptcha(si.captcha))
            si.errorMessage = "Text didn't match the word shown in the image";

        if(!si.password1.equals(si.password2))
            si.errorMessage = "Password didn't match";

        if(si.password1.length()==0)
            si.errorMessage = "Password is required";

        if(si.username.length()==0)
            si.errorMessage = "User name is required";
        else {
            User user = User.get(si.username);
            if(user.getProperty(Details.class)!=null)
                si.errorMessage = "User name is already taken. Did you forget the password?";
        }

        if(si.fullname.length()==0)
            si.fullname = si.username;

        if(!si.email.contains("@"))
            si.errorMessage = "Invalid e-mail address";


        if(si.errorMessage!=null) {
            // failed. ask the user to try again.
            req.setAttribute("data",si);
            req.getView(this,"signup.jelly").forward(req,rsp);
            return;
        }

        // register the user
        User user = User.get(si.username);
        user.addProperty(new Details(si.password1));
        user.addProperty(new Mailer.UserProperty(si.email));
        user.setFullName(si.fullname);
        user.save();
        
        // ... and let him login
        Authentication a = new UsernamePasswordAuthenticationToken(si.username,si.password1);
        a = HudsonFilter.AUTHENTICATION_MANAGER.authenticate(a);
        SecurityContextHolder.getContext().setAuthentication(a);
//        req.getSession().setAttribute(HttpSessionContextIntegrationFilter.ACEGI_SECURITY_CONTEXT_KEY,SecurityContextHolder.getContext());

        // then back to top
        req.getView(this,"success.jelly").forward(req,rsp);
    }

    // TODO
    private static final GrantedAuthority[] TEST_AUTHORITY = {new GrantedAuthorityImpl("authenticated"),new GrantedAuthorityImpl("admin")};

    public static final class SignupInfo {
        public String username,password1,password2,fullname,email,captcha;

        /**
         * To display an error message, set it here.
         */
        public String errorMessage;
    }

    /**
     * {@link UserProperty} that provides the {@link UserDetails} view of the User object.
     *
     * <p>
     * When a {@link User} object has this property on it, it means the user is configured
     * for log-in.
     *
     * <p>
     * When a {@link User} object is re-configured via the UI, the password
     * is sent to the hidden input field by using {@link Protector}, so that
     * the same password can be retained but without leaking information to the browser.
     */
    public static final class Details extends UserProperty implements UserDetails {
        /**
         * Scrambled password.
         */
        private final String password;

        Details(String password) {
            this.password = Scrambler.scramble(password);
        }

        public GrantedAuthority[] getAuthorities() {
            // TODO
            return TEST_AUTHORITY;
        }

        public String getPassword() {
            return Scrambler.descramble(password);
        }

        public String getProtectedPassword() {
            // put session Id in it to prevent a replay attack.
            return Protector.protect(Stapler.getCurrentRequest().getSession().getId()+':'+getPassword());
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
            return true;
        }

        public UserPropertyDescriptor getDescriptor() {
            return DETAILS_DESCRIPTOR;
        }
    }

    public static final UserPropertyDescriptor DETAILS_DESCRIPTOR = new UserPropertyDescriptor(Details.class) {
        public String getDisplayName() {
            return Messages.HudsonPrivateSecurityRealm_Details_DisplayName();
        }

        public Details newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            String pwd = Util.fixEmpty(req.getParameter("user.password"));
            String data = Protector.unprotect(pwd);
            if(data!=null) {
                String prefix = Stapler.getCurrentRequest().getSession().getId() + ':';
                if(data.startsWith(prefix))
                    return new Details(data.substring(prefix.length()));
            }
            return new Details(pwd);
        }

        public UserProperty newInstance(User user) {
            return null;
        }
    };

    /**
     * {@link UserDetailsService} that loads user information from {@link User} object. 
     */
    public static final class HudsonUserDetailsService implements UserDetailsService {
        public UserDetails loadUserByUsername(String username) {
            Details p = User.get(username).getProperty(Details.class);
            if(p==null)
                throw new DisabledException("Password is not set: "+username);
            return p;
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

    static {
        LIST.add(DescriptorImpl.INSTANCE);
    }
}
