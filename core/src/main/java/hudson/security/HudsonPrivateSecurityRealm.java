/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, David Calavera, Seiji Sogabe
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.security;

import hudson.Util;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.ManagementLink;
import hudson.model.ModelObject;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
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
import org.acegisecurity.providers.encoding.PasswordEncoder;
import org.acegisecurity.providers.encoding.ShaPasswordEncoder;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UserDetailsService;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.ServletException;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import groovy.lang.Binding;

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

    /**
     * This implementation doesn't support groups.
     */
    @Override
    public GroupDetails loadGroupByGroupname(String groupname) throws UsernameNotFoundException, DataAccessException {
        throw new UsernameNotFoundException(groupname);
    }

    @Override
    public SecurityComponents createSecurityComponents() {
        Binding binding = new Binding();
        binding.setVariable("passwordEncoder", PASSWORD_ENCODER);

        BeanBuilder builder = new BeanBuilder();
        builder.parse(Hudson.getInstance().servletContext.getResourceAsStream("/WEB-INF/security/HudsonPrivateSecurityRealm.groovy"),binding);
        WebApplicationContext context = builder.createApplicationContext();
        return new SecurityComponents(
                findBean(AuthenticationManager.class, context),
                findBean(UserDetailsService.class, context));
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
            Authentication a = new UsernamePasswordAuthenticationToken(u.getId(),req.getParameter("password1"));
            a = this.getSecurityComponents().manager.authenticate(a);
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

        if(si.password1 != null && !si.password1.equals(si.password2))
            si.errorMessage = "Password didn't match";

        if(!(si.password1 != null && si.password1.length() != 0))
            si.errorMessage = "Password is required";

        if(si.username==null || si.username.length()==0)
            si.errorMessage = "User name is required";
        else {
            User user = User.get(si.username);
            if(user.getProperty(Details.class)!=null)
                si.errorMessage = "User name is already taken. Did you forget the password?";
        }

        if(si.fullname==null || si.fullname.length()==0)
            si.fullname = si.username;

        if(si.email==null || !si.email.contains("@"))
            si.errorMessage = "Invalid e-mail address";

        if(si.errorMessage!=null) {
            // failed. ask the user to try again.
            req.setAttribute("data",si);
            req.getView(this, formView).forward(req,rsp);
            return null;
        }

        // register the user
        User user = createAccount(si.username,si.password1);
        user.addProperty(new Mailer.UserProperty(si.email));
        user.setFullName(si.fullname);
        user.save();
        return user;
    }

    /**
     * Creates a new user account by registering a password to the user.
     */
    public User createAccount(String userName, String password) throws IOException {
        User user = User.get(userName);
        user.addProperty(Details.fromPlainPassword(password));
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
        Collections.sort(r);
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
    private static final GrantedAuthority[] TEST_AUTHORITY = {AUTHENTICATED_AUTHORITY};

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
         * Hashed password.
         */
        private /*almost final*/ String passwordHash;

        /**
         * @deprecated Scrambled password.
         * Field kept here to load old (pre 1.283) user records,
         * but now marked transient so field is no longer saved.
         */
        private transient String password;

        private Details(String passwordHash) {
            this.passwordHash = passwordHash;
        }

        static Details fromHashedPassword(String hashed) {
            return new Details(hashed);
        }

        static Details fromPlainPassword(String rawPassword) {
            return new Details(PASSWORD_ENCODER.encodePassword(rawPassword,null));
        }

        public GrantedAuthority[] getAuthorities() {
            // TODO
            return TEST_AUTHORITY;
        }

        public String getPassword() {
            return passwordHash;
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

        private Object readResolve() {
            // If we are being read back in from an older version
            if (password!=null && passwordHash==null)
                passwordHash = PASSWORD_ENCODER.encodePassword(Scrambler.descramble(password),null);
            return this;
        }

        @Extension
        public static final class DescriptorImpl extends UserPropertyDescriptor {
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
                        return Details.fromHashedPassword(data.substring(prefix.length()));
                }
                return Details.fromPlainPassword(Util.fixNull(pwd));
            }

            public boolean isEnabled() {
                return Hudson.getInstance().getSecurityRealm() instanceof HudsonPrivateSecurityRealm;
            }

            public UserProperty newInstance(User user) {
                return null;
            }
        }
    }

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
    @Extension
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

    /**
     * {@link PasswordEncoder} based on SHA-256 and random salt generation.
     *
     * <p>
     * The salt is prepended to the hashed password and returned. So the encoded password is of the form
     * <tt>SALT ':' hash(PASSWORD,SALT)</tt>.
     *
     * <p>
     * This abbreviates the need to store the salt separately, which in turn allows us to hide the salt handling
     * in this little class. The rest of the Acegi thinks that we are not using salt.
     */
    public static final PasswordEncoder PASSWORD_ENCODER = new PasswordEncoder() {
        private final PasswordEncoder passwordEncoder = new ShaPasswordEncoder(256);

        public String encodePassword(String rawPass, Object _) throws DataAccessException {
            return hash(rawPass);
        }

        public boolean isPasswordValid(String encPass, String rawPass, Object _) throws DataAccessException {
            // pull out the sale from the encoded password
            int i = encPass.indexOf(':');
            if(i<0) return false;
            String salt = encPass.substring(0,i);
            return encPass.substring(i+1).equals(passwordEncoder.encodePassword(rawPass,salt));
        }

        /**
         * Creates a hashed password by generating a random salt.
         */
        private String hash(String password) {
            String salt = generateSalt();
            return salt+':'+passwordEncoder.encodePassword(password,salt);
        }

        /**
         * Generates random salt.
         */
        private String generateSalt() {
            StringBuilder buf = new StringBuilder();
            SecureRandom sr = new SecureRandom();
            for( int i=0; i<6; i++ ) {// log2(52^6)=34.20... so, this is about 32bit strong.
                boolean upper = sr.nextBoolean();
                char ch = (char)(sr.nextInt(26) + 'a');
                if(upper)   ch=Character.toUpperCase(ch);
                buf.append(ch);
            }
            return buf.toString();
        }
    };

    @Extension
    public static final class DescriptorImpl extends Descriptor<SecurityRealm> {
        public String getDisplayName() {
            return Messages.HudsonPrivateSecurityRealm_DisplayName();
        }

        public String getHelpFile() {
            return "/help/security/private-realm.html"; 
        }
    }
}
