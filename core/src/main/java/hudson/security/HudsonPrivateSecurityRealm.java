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
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

/**
 * {@link SecurityRealm} that performs authentication by looking up {@link User}.
 *
 * <p>
 * Implements {@link AccessControlled} to satisfy view rendering, but in reality the access control
 * is done against the {@link Hudson} object.
 *
 * @author Kohsuke Kawaguchi
 */
public class HudsonPrivateSecurityRealm extends SecurityRealm implements ModelObject, AccessControlled {
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
     * Creates an user account. Used for self-registration.
     */
    public void doCreateAccount(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if(!allowsSignup()) {
            rsp.sendError(SC_UNAUTHORIZED,"User sign up is prohibited");
            return;
        }
        User u = createAccount(req, rsp, true, "signup.jelly");
        if(u!=null) {
            // ... and let him login
            Authentication a = u.getProperty(Details.class).createAuthentication();
            a = HudsonFilter.AUTHENTICATION_MANAGER.authenticate(a);
            SecurityContextHolder.getContext().setAuthentication(a);

            // then back to top
            req.getView(this,"success.jelly").forward(req,rsp);
        }
    }

    /**
     * Creates an user account. Used by admins.
     *
     * This version behaves differently from {@link #doCreateAccount(StaplerRequest, StaplerResponse)} in that
     * this is someone creating another user.
     */
    public void doCreateAccountByAdmin(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        checkPermission(Hudson.ADMINISTER);
        if(createAccount(req, rsp, false, "addUser.jelly")!=null) {
            rsp.sendRedirect(".");  // send the user back to the listing page
        }
    }

    /**
     * @return
     *      null if failed. The browser is already redirected to retry by the time this method returns.
     *      a valid {@link User} object if the user creation was successful.
     */
    private User createAccount(StaplerRequest req, StaplerResponse rsp, boolean selfRegistration, String formView) throws ServletException, IOException {
        // form field validation
        // this pattern needs to be generalized and moved to stapler
        SignupInfo si = new SignupInfo();
        req.bindParameters(si);

        if(selfRegistration && !validateCaptcha(si.captcha))
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
            req.getView(this, formView).forward(req,rsp);
            return null;
        }

        // register the user
        User user = User.get(si.username);
        user.addProperty(new Details(si.password1));
        user.addProperty(new Mailer.UserProperty(si.email));
        user.setFullName(si.fullname);
        user.save();
        return user;
    }

    /**
     * This is used primarily when the object is listed in the breadcrumb, in the user management screen.
     */
    public String getDisplayName() {
        return "User Database";
    }

    public ACL getACL() {
        return Hudson.getInstance().getACL();
    }

    public void checkPermission(Permission permission) {
        Hudson.getInstance().checkPermission(permission);
    }

    public boolean hasPermission(Permission permission) {
        return Hudson.getInstance().hasPermission(permission);
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

    /**
     * This is to map users under the security realm URL.
     * This in turn helps us set up the right navigation breadcrumb.
     */
    public User getUser(String id) {
        return User.get(id);
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

        /*package*/ Authentication createAuthentication() {
            return new UsernamePasswordAuthenticationToken(null, password);
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

    /**
     * Displays "manage users" link in the system config if {@link HudsonPrivateSecurityRealm}
     * is in effect.
     */
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
            return Messages.HudsonPrivateSecurityRealm_ManageUserLinks_DisplayName();
        }

        @Override
        public String getDescription() {
            return Messages.HudsonPrivateSecurityRealm_ManageUserLinks_Description();
        }
    }

    public static final class DescriptorImpl extends Descriptor<SecurityRealm> {
        public static final DescriptorImpl INSTANCE = new DescriptorImpl();

        private DescriptorImpl() {
            super(HudsonPrivateSecurityRealm.class);
        }

        public String getDisplayName() {
            return Messages.HudsonPrivateSecurityRealm_DisplayName();
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
