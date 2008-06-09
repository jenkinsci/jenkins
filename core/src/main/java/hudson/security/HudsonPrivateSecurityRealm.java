package hudson.security;

import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.model.ManagementLink;
import hudson.model.ModelObject;
import hudson.tasks.Mailer;
import hudson.util.Protector;
import hudson.util.Scrambler;
import hudson.util.spring.BeanBuilder;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.GrantedAuthorityImpl;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UserDetailsService;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

/**
 * {@link SecurityRealm} that performs authentication by looking up {@link User}.
 *
 * @author Kohsuke Kawaguchi
 */
public class HudsonPrivateSecurityRealm extends SecurityRealm implements ModelObject {
    /**
     * If true, sign up is not allowed.
     * <p>
     * This is a negative switch so that the default value 'false' remains compatible with older installations. 
     */
    private final boolean disableSignup;

    @DataBoundConstructor
    public HudsonPrivateSecurityRealm(boolean allowsSignup) {
        this.disableSignup = !allowsSignup;
    }

    @Override
    public boolean allowsSignup() {
        return !disableSignup;
    }

    @Override
    public SecurityComponents createSecurityComponents() {
        BeanBuilder builder = new BeanBuilder();
        builder.parse(Hudson.getInstance().servletContext.getResourceAsStream("/WEB-INF/security/HudsonPrivateSecurityRealm.groovy"));
        WebApplicationContext context = builder.createApplicationContext();
        return new SecurityComponents(
                findBean(AuthenticationManager.class, context),
                findBean(UserDetailsService.class, context));
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

    /**
     * This is used primarily when the object is listed in the breadcrumb, in the user management screen.
     */
    public String getDisplayName() {
        return "User Database";
    }

    /**
     * All users who can login to the system.
     */
    public List<User> getAllUsers() {
        List<User> r = new ArrayList<User>();
        for (User u : User.getAll()) {
            if(u.getProperty(Details.class)!=null)
                r.add(u);
        }
        return r;
    }

    // TODO
    private static final GrantedAuthority[] TEST_AUTHORITY = {new GrantedAuthorityImpl("authenticated")};

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
    public static final class Details extends UserProperty implements InvalidatableUserDetails {
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

        /*package*/ User getUser() {
            return user;
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

        public boolean isInvalid() {
            return user==null;
        }

        public UserPropertyDescriptor getDescriptor() {
            return DETAILS_DESCRIPTOR;
        }
    }

    public static final UserPropertyDescriptor DETAILS_DESCRIPTOR = new UserPropertyDescriptor(Details.class) {
        public String getDisplayName() {
            // this feature is only when HudsonPrivateSecurityRealm is enabled
            if(isEnabled())
                return Messages.HudsonPrivateSecurityRealm_Details_DisplayName();
            else
                return null;
        }

        public Details newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            String pwd = Util.fixEmpty(req.getParameter("user.password"));
            String pwd2= Util.fixEmpty(req.getParameter("user.password2"));

            if(!Util.fixNull(pwd).equals(Util.fixNull(pwd2)))
                throw new FormException("Please confirm the password by typing it twice","user.password2");

            String data = Protector.unprotect(pwd);
            if(data!=null) {
                String prefix = Stapler.getCurrentRequest().getSession().getId() + ':';
                if(data.startsWith(prefix))
                    return new Details(data.substring(prefix.length()));
            }
            return new Details(Util.fixNull(pwd));
        }

        public boolean isEnabled() {
            return Hudson.getInstance().getSecurityRealm() instanceof HudsonPrivateSecurityRealm;
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
                throw new UsernameNotFoundException("Password is not set: "+username);
            if(p.getUser()==null)
                throw new AssertionError();
            return p;
        }
    }

    public static final class ManageUserLinks extends ManagementLink {
        public String getIconFileName() {
            if(Hudson.getInstance().getSecurityRealm() instanceof HudsonPrivateSecurityRealm)
                return "user.gif";
            else
                return null;    // not applicable now
        }

        public String getUrlName() {
            return "securityRealm/";
        }

        public String getDisplayName() {
            return "Manage Users";
        }

        @Override
        public String getDescription() {
            return "Create/delete/modify users that can log in to this Hudson";
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
    }

    static {
        LIST.add(DescriptorImpl.INSTANCE);
        ManageUserLinks.LIST.add(new ManageUserLinks());
    }
}
