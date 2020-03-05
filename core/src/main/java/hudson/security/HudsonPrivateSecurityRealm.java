/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, David Calavera, Seiji Sogabe
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

import com.thoughtworks.xstream.converters.UnmarshallingContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.diagnosis.OldDataMonitor;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import hudson.model.ManagementLink;
import hudson.model.ModelObject;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.security.FederatedLoginService.FederatedIdentity;
import hudson.security.captcha.CaptchaSupport;
import hudson.util.PluginServletFilter;
import hudson.util.Protector;
import hudson.util.Scrambler;
import hudson.util.XStream2;
import jenkins.security.SecurityListener;
import jenkins.util.SystemProperties;
import jenkins.security.seed.UserSeedProperty;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.BadCredentialsException;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.providers.encoding.PasswordEncoder;
import org.acegisecurity.providers.encoding.ShaPasswordEncoder;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.ForwardToView;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.dao.DataAccessException;

import javax.annotation.Nonnull;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.security.SecureRandom;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * {@link SecurityRealm} that performs authentication by looking up {@link User}.
 *
 * <p>
 * Implements {@link AccessControlled} to satisfy view rendering, but in reality the access control
 * is done against the {@link jenkins.model.Jenkins} object.
 *
 * @author Kohsuke Kawaguchi
 */
public class HudsonPrivateSecurityRealm extends AbstractPasswordBasedSecurityRealm implements ModelObject, AccessControlled {
    private static /* not final */ String ID_REGEX = System.getProperty(HudsonPrivateSecurityRealm.class.getName() + ".ID_REGEX");
    
    /**
     * Default REGEX for the user ID check in case the ID_REGEX is not set
     * It allows A-Za-z0-9 + "_-"
     * in Java {@code \w} is equivalent to {@code [A-Za-z0-9_]} (take care of "_")
     */
    private static final String DEFAULT_ID_REGEX = "^[\\w-]+$";
    
    /**
     * If true, sign up is not allowed.
     * <p>
     * This is a negative switch so that the default value 'false' remains compatible with older installations.
     */
    private final boolean disableSignup;

    /**
     * If true, captcha will be enabled.
     */
    private final boolean enableCaptcha;

    @Deprecated
    public HudsonPrivateSecurityRealm(boolean allowsSignup) {
        this(allowsSignup, false, (CaptchaSupport) null);
    }

    @DataBoundConstructor
    public HudsonPrivateSecurityRealm(boolean allowsSignup, boolean enableCaptcha, CaptchaSupport captchaSupport) {
        this.disableSignup = !allowsSignup;
        this.enableCaptcha = enableCaptcha;
        setCaptchaSupport(captchaSupport);
        if(!allowsSignup && !hasSomeUser()) {
            // if Hudson is newly set up with the security realm and there's no user account created yet,
            // insert a filter that asks the user to create one
            try {
                PluginServletFilter.addFilter(CREATE_FIRST_USER_FILTER);
            } catch (ServletException e) {
                throw new AssertionError(e); // never happen because our Filter.init is no-op
            }
        }
    }

    @Override
    public boolean allowsSignup() {
        return !disableSignup;
    }

    @Restricted(NoExternalUse.class) // Jelly
    public boolean getAllowsSignup() {
        return allowsSignup();
    }

    /**
     * Checks if captcha is enabled on user signup.
     *
     * @return true if captcha is enabled on signup.
     */
    public boolean isEnableCaptcha() {
        return enableCaptcha;
    }

    /**
     * Computes if this Hudson has some user accounts configured.
     *
     * <p>
     * This is used to check for the initial
     */
    private static boolean hasSomeUser() {
        for (User u : User.getAll())
            if(u.getProperty(Details.class)!=null)
                return true;
        return false;
    }

    /**
     * This implementation doesn't support groups.
     */
    @Override
    public GroupDetails loadGroupByGroupname(String groupname) throws UsernameNotFoundException, DataAccessException {
        throw new UsernameNotFoundException(groupname);
    }

    @Override
    public Details loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException {
        User u = User.getById(username, false);
        Details p = u!=null ? u.getProperty(Details.class) : null;
        if(p==null)
            throw new UsernameNotFoundException("Password is not set: "+username);
        if(p.getUser()==null)
            throw new AssertionError();
        return p;
    }

    @Override
    protected Details authenticate(String username, String password) throws AuthenticationException {
        Details u = loadUserByUsername(username);
        if (!u.isPasswordCorrect(password)) {
            String message;
            try {
                message = ResourceBundle.getBundle("org.acegisecurity.messages").getString("AbstractUserDetailsAuthenticationProvider.badCredentials");
            } catch (MissingResourceException x) {
                message = "Bad credentials";
            }
            throw new BadCredentialsException(message);
        }
        return u;
    }

    /**
     * Show the sign up page with the data from the identity.
     */
    @Override
    public HttpResponse commenceSignup(final FederatedIdentity identity) {
        // store the identity in the session so that we can use this later
        Stapler.getCurrentRequest().getSession().setAttribute(FEDERATED_IDENTITY_SESSION_KEY,identity);
        return new ForwardToView(this,"signupWithFederatedIdentity.jelly") {
            @Override
            public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
                SignupInfo si = new SignupInfo(identity);
                si.errorMessage = Messages.HudsonPrivateSecurityRealm_WouldYouLikeToSignUp(identity.getPronoun(),identity.getIdentifier());
                req.setAttribute("data", si);
                super.generateResponse(req, rsp, node);
            }
        };
    }

    /**
     * Creates an account and associates that with the given identity. Used in conjunction
     * with {@link #commenceSignup}.
     */
    @RequirePOST
    public User doCreateAccountWithFederatedIdentity(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        User u = _doCreateAccount(req,rsp,"signupWithFederatedIdentity.jelly");
        if (u!=null)
            ((FederatedIdentity)req.getSession().getAttribute(FEDERATED_IDENTITY_SESSION_KEY)).addTo(u);
        return u;
    }

    private static final String FEDERATED_IDENTITY_SESSION_KEY = HudsonPrivateSecurityRealm.class.getName()+".federatedIdentity";

    /**
     * Creates an user account. Used for self-registration.
     */
    @RequirePOST
    public User doCreateAccount(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        return _doCreateAccount(req, rsp, "signup.jelly");
    }

    private User _doCreateAccount(StaplerRequest req, StaplerResponse rsp, String formView) throws ServletException, IOException {
        if(!allowsSignup())
            throw HttpResponses.error(SC_UNAUTHORIZED,new Exception("User sign up is prohibited"));

        boolean firstUser = !hasSomeUser();
        User u = createAccount(req, rsp, enableCaptcha, formView);
        if(u!=null) {
            if(firstUser)
                tryToMakeAdmin(u);  // the first user should be admin, or else there's a risk of lock out
            loginAndTakeBack(req, rsp, u);
        }
        return u;
    }

    /**
     * Lets the current user silently login as the given user and report back accordingly.
     */
    @SuppressWarnings("ACL.impersonate")
    private void loginAndTakeBack(StaplerRequest req, StaplerResponse rsp, User u) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session != null) {
            // avoid session fixation
            session.invalidate();
        }
        req.getSession(true);
        
        // ... and let him login
        Authentication a = new UsernamePasswordAuthenticationToken(u.getId(),req.getParameter("password1"));
        a = this.getSecurityComponents().manager.authenticate(a);
        SecurityContextHolder.getContext().setAuthentication(a);

        SecurityListener.fireLoggedIn(u.getId());

        // then back to top
        req.getView(this,"success.jelly").forward(req,rsp);
    }

    /**
     * Creates a user account. Used by admins.
     *
     * This version behaves differently from {@link #doCreateAccount(StaplerRequest, StaplerResponse)} in that
     * this is someone creating another user.
     */
    @RequirePOST
    public void doCreateAccountByAdmin(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        createAccountByAdmin(req, rsp, "addUser.jelly", "."); // send the user back to the listing page on success
    }

    /**
     * Creates a user account. Requires {@link Jenkins#ADMINISTER}
     */
    @Restricted(NoExternalUse.class)
    public User createAccountByAdmin(StaplerRequest req, StaplerResponse rsp, String addUserView, String successView) throws IOException, ServletException {
        checkPermission(Jenkins.ADMINISTER);
        User u = createAccount(req, rsp, false, addUserView);
        if (u != null && successView != null) {
            rsp.sendRedirect(successView);
        }
        return u;
    }

    /**
     * Creates a user account. Intended to be called from the setup wizard.
     * Note that this method does not check whether it is actually called from
     * the setup wizard. This requires the {@link Jenkins#ADMINISTER} permission.
     *
     * @param req the request to retrieve input data from
     * @return the created user account, never null
     * @throws AccountCreationFailedException if account creation failed due to invalid form input
     */
    @Restricted(NoExternalUse.class)
    public User createAccountFromSetupWizard(StaplerRequest req) throws IOException, AccountCreationFailedException {
        checkPermission(Jenkins.ADMINISTER);
        SignupInfo si = validateAccountCreationForm(req, false);
        if (!si.errors.isEmpty()) {
            String messages = getErrorMessages(si);
            throw new AccountCreationFailedException(messages);
        } else {
            return createAccount(si);
        }
    }

    private String getErrorMessages(SignupInfo si) {
        StringBuilder messages = new StringBuilder();
        for (String message : si.errors.values()) {
            messages.append(message).append(" | ");
        }
        return messages.toString();
    }

    /**
     * Creates a first admin user account.
     *
     * <p>
     * This can be run by anyone, but only to create the very first user account.
     */
    @RequirePOST
    public void doCreateFirstAccount(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if(hasSomeUser()) {
            rsp.sendError(SC_UNAUTHORIZED,"First user was already created");
            return;
        }
        User u = createAccount(req, rsp, false, "firstUser.jelly");
        if (u!=null) {
            tryToMakeAdmin(u);
            loginAndTakeBack(req, rsp, u);
        }
    }

    /**
     * Try to make this user a super-user
     */
    private void tryToMakeAdmin(User u) {
        AuthorizationStrategy as = Jenkins.get().getAuthorizationStrategy();
        for (PermissionAdder adder : ExtensionList.lookup(PermissionAdder.class)) {
            if (adder.add(as, u, Jenkins.ADMINISTER)) {
                return;
            }
        }
    }

    /**
     * @param req the request to get the form data from (is also used for redirection)
     * @param rsp the response to use for forwarding if the creation fails
     * @param validateCaptcha whether to attempt to validate a captcha in the request
     * @param formView the view to redirect to if creation fails
     *
     * @return
     *      null if failed. The browser is already redirected to retry by the time this method returns.
     *      a valid {@link User} object if the user creation was successful.
     */
    private User createAccount(StaplerRequest req, StaplerResponse rsp, boolean validateCaptcha, String formView) throws ServletException, IOException {
        SignupInfo si = validateAccountCreationForm(req, validateCaptcha);

        if (!si.errors.isEmpty()) {
            // failed. ask the user to try again.
            req.getView(this, formView).forward(req, rsp);
            return null;
        }

        return createAccount(si);
    }

    /**
     * @param req              the request to process
     * @param validateCaptcha  whether to attempt to validate a captcha in the request
     *
     * @return a {@link SignupInfo#SignupInfo(StaplerRequest) SignupInfo from given request}, with {@link
     * SignupInfo#errors} containing errors (keyed by field name), if any of the supported fields are invalid
     */
    private SignupInfo validateAccountCreationForm(StaplerRequest req, boolean validateCaptcha) {
        // form field validation
        // this pattern needs to be generalized and moved to stapler
        SignupInfo si = new SignupInfo(req);

        if (validateCaptcha && !validateCaptcha(si.captcha)) {
            si.errors.put("captcha", Messages.HudsonPrivateSecurityRealm_CreateAccount_TextNotMatchWordInImage());
        }

        if (si.username == null || si.username.length() == 0) {
            si.errors.put("username", Messages.HudsonPrivateSecurityRealm_CreateAccount_UserNameRequired());
        } else if(!containsOnlyAcceptableCharacters(si.username)) {
            if (ID_REGEX == null) {
                si.errors.put("username", Messages.HudsonPrivateSecurityRealm_CreateAccount_UserNameInvalidCharacters());
            } else {
                si.errors.put("username", Messages.HudsonPrivateSecurityRealm_CreateAccount_UserNameInvalidCharactersCustom(ID_REGEX));
            }
        } else {
            // do not create the user - we just want to check if the user already exists but is not a "login" user.
            User user = User.getById(si.username, false);
            if (null != user)
                // Allow sign up. SCM people has no such property.
                if (user.getProperty(Details.class) != null)
                    si.errors.put("username", Messages.HudsonPrivateSecurityRealm_CreateAccount_UserNameAlreadyTaken());
        }

        if (si.password1 != null && !si.password1.equals(si.password2)) {
            si.errors.put("password1", Messages.HudsonPrivateSecurityRealm_CreateAccount_PasswordNotMatch());
        }

        if (!(si.password1 != null && si.password1.length() != 0)) {
            si.errors.put("password1", Messages.HudsonPrivateSecurityRealm_CreateAccount_PasswordRequired());
        }

        if (si.fullname == null || si.fullname.length() == 0) {
            si.fullname = si.username;
        }

        if (isMailerPluginPresent() && (si.email == null || !si.email.contains("@"))) {
            si.errors.put("email", Messages.HudsonPrivateSecurityRealm_CreateAccount_InvalidEmailAddress());
        }

        if (!User.isIdOrFullnameAllowed(si.username)) {
            si.errors.put("username", hudson.model.Messages.User_IllegalUsername(si.username));
        }

        if (!User.isIdOrFullnameAllowed(si.fullname)) {
            si.errors.put("fullname", hudson.model.Messages.User_IllegalFullname(si.fullname));
        }
        req.setAttribute("data", si); // for error messages in the view
        return si;
    }

    /**
     * Creates a new account from a valid signup info. A signup info is valid if its {@link SignupInfo#errors}
     * field is empty.
     *
     * @param si the valid signup info to create an account from
     * @return a valid {@link User} object created from given signup info
     * @throws IllegalArgumentException if an invalid signup info is passed
     */
    private User createAccount(SignupInfo si) throws IOException {
        if (!si.errors.isEmpty()) {
            String messages = getErrorMessages(si);
            throw new IllegalArgumentException("invalid signup info passed to createAccount(si): " + messages);
        }
        // register the user
        User user = createAccount(si.username, si.password1);
        user.setFullName(si.fullname);
        if (isMailerPluginPresent()) {
            try {
                // legacy hack. mail support has moved out to a separate plugin
                Class<?> up = Jenkins.get().pluginManager.uberClassLoader.loadClass("hudson.tasks.Mailer$UserProperty");
                Constructor<?> c = up.getDeclaredConstructor(String.class);
                user.addProperty((UserProperty) c.newInstance(si.email));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
        user.save();
        return user;
    }

    private boolean containsOnlyAcceptableCharacters(@Nonnull String value){
        if(ID_REGEX == null){
            return value.matches(DEFAULT_ID_REGEX);
        }else{
            return value.matches(ID_REGEX);
        }
    }

    @Restricted(NoExternalUse.class) // _entryForm.jelly and signup.jelly
    public boolean isMailerPluginPresent() {
        try {
            // mail support has moved to a separate plugin
            return null != Jenkins.get().pluginManager.uberClassLoader.loadClass("hudson.tasks.Mailer$UserProperty");
        } catch (ClassNotFoundException e) {
            LOGGER.finer("Mailer plugin not present");
        }
        return false;
    }

    /**
     * Creates a new user account by registering a password to the user.
     */
    public User createAccount(String userName, String password) throws IOException {
        User user = User.getById(userName, true);
        user.addProperty(Details.fromPlainPassword(password));
        SecurityListener.fireUserCreated(user.getId());
        return user;
    }

    /**
     * Creates a new user account by registering a JBCrypt Hashed password with the user.
     *
     * @param userName The user's name
     * @param hashedPassword A hashed password, must begin with {@code #jbcrypt:}
     */
    public User createAccountWithHashedPassword(String userName, String hashedPassword) throws IOException {
        if (!PASSWORD_ENCODER.isPasswordHashed(hashedPassword)) {
            throw new IllegalArgumentException("this method should only be called with a pre-hashed password");
        }
        User user = User.getById(userName, true);
        user.addProperty(Details.fromHashedPassword(hashedPassword));
        SecurityListener.fireUserCreated(user.getId());
        return user;
    }


    /**
     * This is used primarily when the object is listed in the breadcrumb, in the user management screen.
     */
    public String getDisplayName() {
        return Messages.HudsonPrivateSecurityRealm_DisplayName();
    }

    public ACL getACL() {
        return Jenkins.get().getACL();
    }

    public void checkPermission(Permission permission) {
        Jenkins.get().checkPermission(permission);
    }

    public boolean hasPermission(Permission permission) {
        return Jenkins.get().hasPermission(permission);
    }


    /**
     * All users who can login to the system.
     */
    public List<User> getAllUsers() {
        List<User> r = new ArrayList<>();
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
    @Restricted(NoExternalUse.class)
    public User getUser(String id) {
        return User.getById(id, User.ALLOW_USER_CREATION_VIA_URL && hasPermission(Jenkins.ADMINISTER));
    }

    // TODO
    private static final GrantedAuthority[] TEST_AUTHORITY = {AUTHENTICATED_AUTHORITY};

    public static final class SignupInfo {
        public String username,password1,password2,fullname,email,captcha;

        /**
         * To display a general error message, set it here.
         *
         */
        public String errorMessage;

        /**
         * Add field-specific error messages here.
         * Keys are field names (e.g. {@code password2}), values are the messages.
         */
        // TODO i18n?
        public HashMap<String, String> errors = new HashMap<>();

        public SignupInfo() {
        }

        public SignupInfo(StaplerRequest req) {
            req.bindParameters(this);
        }

        public SignupInfo(FederatedIdentity i) {
            this.username = i.getNickname();
            this.fullname = i.getFullName();
            this.email = i.getEmailAddress();
        }
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
        @Deprecated
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

        public boolean isPasswordCorrect(String candidate) {
            return PASSWORD_ENCODER.isPasswordValid(getPassword(),candidate,null);
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

        public static class ConverterImpl extends XStream2.PassthruConverter<Details> {
            public ConverterImpl(XStream2 xstream) { super(xstream); }
            @Override protected void callback(Details d, UnmarshallingContext context) {
                // Convert to hashed password and report to monitor if we load old data
                if (d.password!=null && d.passwordHash==null) {
                    d.passwordHash = PASSWORD_ENCODER.encodePassword(Scrambler.descramble(d.password),null);
                    OldDataMonitor.report(context, "1.283");
                }
            }
        }

        @Extension @Symbol("password")
        public static final class DescriptorImpl extends UserPropertyDescriptor {
            @Override
            public String getDisplayName() {
                return Messages.HudsonPrivateSecurityRealm_Details_DisplayName();
            }

            @Override
            public Details newInstance(StaplerRequest req, JSONObject formData) throws FormException {
                if (req == null) {
                    // Should never happen, see newInstance() Javadoc
                    throw new FormException("Stapler request is missing in the call", "staplerRequest");
                }
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

                User user = Util.getNearestAncestorOfTypeOrThrow(req, User.class);
                // the UserSeedProperty is not touched by the configure page
                UserSeedProperty userSeedProperty = user.getProperty(UserSeedProperty.class);
                if (userSeedProperty != null) {
                    userSeedProperty.renewSeed();
                }

                return Details.fromPlainPassword(Util.fixNull(pwd));
            }

            @Override
            public boolean isEnabled() {
                // this feature is only when HudsonPrivateSecurityRealm is enabled
                return Jenkins.get().getSecurityRealm() instanceof HudsonPrivateSecurityRealm;
            }

            public UserProperty newInstance(User user) {
                return null;
            }
        }
    }

    /**
     * Displays "manage users" link in the system config if {@link HudsonPrivateSecurityRealm}
     * is in effect.
     */
    @Extension @Symbol("localUsers")
    public static final class ManageUserLinks extends ManagementLink {
        public String getIconFileName() {
            if(Jenkins.get().getSecurityRealm() instanceof HudsonPrivateSecurityRealm)
                return "user.png";
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

        @Nonnull
        @Override
        public String getCategory() {
            return Category.SECURITY.toString();
        }
    }

    /**
     * {@link PasswordEncoder} based on SHA-256 and random salt generation.
     *
     * <p>
     * The salt is prepended to the hashed password and returned. So the encoded password is of the form
     * {@code SALT ':' hash(PASSWORD,SALT)}.
     *
     * <p>
     * This abbreviates the need to store the salt separately, which in turn allows us to hide the salt handling
     * in this little class. The rest of the Acegi thinks that we are not using salt.
     */
    /*package*/ static final PasswordEncoder CLASSIC = new PasswordEncoder() {
        private final PasswordEncoder passwordEncoder = new ShaPasswordEncoder(256);

        public String encodePassword(String rawPass, Object obj) throws DataAccessException {
            return hash(rawPass);
        }

        public boolean isPasswordValid(String encPass, String rawPass, Object obj) throws DataAccessException {
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

    /**
     * {@link PasswordEncoder} that uses jBCrypt.
     */
    private static class JBCryptEncoder implements PasswordEncoder {
        // in jBCrypt the maximum is 30, which takes ~22h with laptop late-2017
        // and for 18, it's "only" 20s
        @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Accessible via System Groovy Scripts")
        @Restricted(NoExternalUse.class)
        private static int MAXIMUM_BCRYPT_LOG_ROUND = SystemProperties.getInteger(HudsonPrivateSecurityRealm.class.getName() + ".maximumBCryptLogRound", 18);

        private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2a\\$([0-9]{2})\\$.{53}$");

        public String encodePassword(String rawPass, Object obj) throws DataAccessException {
            return BCrypt.hashpw(rawPass,BCrypt.gensalt());
        }

        public boolean isPasswordValid(String encPass, String rawPass, Object obj) throws DataAccessException {
            return BCrypt.checkpw(rawPass,encPass);
        }

        /**
         * Returns true if the supplied hash looks like a bcrypt encoded hash value, based off of the
         * implementation defined in jBCrypt and: https://en.wikipedia.org/wiki/Bcrypt.
         *
         */
        public boolean isHashValid(String hash) {
            Matcher matcher = BCRYPT_PATTERN.matcher(hash);
            if (matcher.matches()) {
                String logNumOfRound = matcher.group(1);
                // no number format exception due to the expression
                int logNumOfRoundInt = Integer.parseInt(logNumOfRound);
                if (logNumOfRoundInt > 0 && logNumOfRoundInt <= MAXIMUM_BCRYPT_LOG_ROUND) {
                    return true;
                }
            }
            return false;
        }
    }

    /* package */ static final JBCryptEncoder JBCRYPT_ENCODER = new JBCryptEncoder();

    /**
     * Combines {@link #JBCRYPT_ENCODER} and {@link #CLASSIC} into one so that we can continue
     * to accept {@link #CLASSIC} format but new encoding will always done via {@link #JBCRYPT_ENCODER}.
     */
    /* package */ static class MultiPasswordEncoder implements PasswordEncoder {
        /**
         * Magic header used to detect if a password is bcrypt hashed.
         */
        private static final String JBCRYPT_HEADER = "#jbcrypt:";

        /*
            CLASSIC encoder outputs "salt:hash" where salt is [a-z]+, so we use unique prefix '#jbcyrpt"
            to designate JBCRYPT-format hash.

            '#' is neither in base64 nor hex, which makes it a good choice.
         */
        public String encodePassword(String rawPass, Object salt) throws DataAccessException {
            return JBCRYPT_HEADER+JBCRYPT_ENCODER.encodePassword(rawPass,salt);
        }

        public boolean isPasswordValid(String encPass, String rawPass, Object salt) throws DataAccessException {
            if (isPasswordHashed(encPass)) {
                return JBCRYPT_ENCODER.isPasswordValid(encPass.substring(JBCRYPT_HEADER.length()), rawPass, salt);
            } else {
                return CLASSIC.isPasswordValid(encPass, rawPass, salt);
            }
        }

        /**
         * Returns true if the supplied password starts with a prefix indicating it is already hashed.
         */
        public boolean isPasswordHashed(String password) {
            if (password == null) {
                return false;
            }
            return password.startsWith(JBCRYPT_HEADER) && JBCRYPT_ENCODER.isHashValid(password.substring(JBCRYPT_HEADER.length()));
        }

    }

    public static final MultiPasswordEncoder PASSWORD_ENCODER = new MultiPasswordEncoder();

    @Extension @Symbol("local")
    public static final class DescriptorImpl extends Descriptor<SecurityRealm> {
        public String getDisplayName() {
            return Messages.HudsonPrivateSecurityRealm_DisplayName();
        }
    }

    private static final Filter CREATE_FIRST_USER_FILTER = new Filter() {
        public void init(FilterConfig config) throws ServletException {
        }

        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            HttpServletRequest req = (HttpServletRequest) request;

            /* allow signup from the Jenkins home page, or /manage, which is where a /configureSecurity form redirects to */
            if(req.getRequestURI().equals(req.getContextPath()+"/") || req.getRequestURI().equals(req.getContextPath() + "/manage")) {
                if (needsToCreateFirstUser()) {
                    ((HttpServletResponse)response).sendRedirect("securityRealm/firstUser");
                } else {// the first user already created. the role of this filter is over.
                    PluginServletFilter.removeFilter(this);
                    chain.doFilter(request,response);
                }
            } else
                chain.doFilter(request,response);
        }

        private boolean needsToCreateFirstUser() {
            return !hasSomeUser()
                && Jenkins.get().getSecurityRealm() instanceof HudsonPrivateSecurityRealm;
        }

        public void destroy() {
        }
    };

    private static final Logger LOGGER = Logger.getLogger(HudsonPrivateSecurityRealm.class.getName());
}
