package hudson.security;

import groovy.lang.Binding;

import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.util.DescriptorList;
import hudson.util.PluginServletFilter;
import hudson.util.spring.BeanBuilder;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.ui.rememberme.RememberMeServices;
import org.acegisecurity.userdetails.UserDetailsService;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.IOException;

import com.octo.captcha.service.image.DefaultManageableImageCaptchaService;
import com.octo.captcha.service.CaptchaServiceException;

import javax.imageio.ImageIO;
import javax.servlet.Filter;
import javax.servlet.FilterConfig;

/**
 * Pluggable security realm that connects external user database to Hudson.
 *
 * <p>
 * New implementations should be registered to {@link #LIST}.
 *
 * <p>
 * If additional views/URLs need to be exposed,
 * an active {@link SecurityRealm} is bound to <tt>CONTEXT_ROOT/securityRealm/</tt>
 * through {@link Hudson#getSecurityRealm()}, so you can define additional pages and
 * operations on your {@link SecurityRealm}.
 *
 * <h2>How do I implement this class?</h2>
 * <p>
 * For compatibility reasons, there are two somewhat different ways to implement a custom SecurityRealm.
 *
 * <p>
 * One is to override the {@link #createSecurityComponents()} and create key Acegi components
 * that control the authentication process.
 * The default {@link SecurityRealm#createFilter(FilterConfig)} implementation then assembles them
 * into a chain of {@link Filter}s. All the incoming requests to Hudson go through this filter chain,
 * and when the filter chain is done, {@link SecurityContext#getAuthentication()} would tell us
 * who the current user is.
 *
 * <p>
 * If your {@link SecurityRealm} needs to touch the default {@link Filter} chain configuration
 * (e.g., adding new ones), then you can also override {@link #createFilter(FilterConfig)} to do so.
 *
 * <p>
 * This model is expected to fit most {@link SecurityRealm} implementations.
 *
 *
 * <p>
 * The other way of doing this is to ignore {@link #createSecurityComponents()} completely (by returning
 * {@link SecurityComponents} created by the default constructor) and just concentrate on {@link #createFilter(FilterConfig)}.
 * As long as the resulting filter chain properly sets up {@link Authentication} object at the end of the processing,
 * Hudson doesn't really need you to fit the standard Acegi models like {@link AuthenticationManager} and
 * {@link UserDetailsService}.
 *
 * <p>
 * This model is for those "weird" implementations.
 *
 *
 * <h2>Views</h2>
 * <dl>
 * <dt>loginLink.jelly</dt>
 * <dd>
 * This view renders the login link on the top right corner of every page, when the user
 * is anonymous. For {@link SecurityRealm}s that support user sign-up, this is a good place
 * to show a "sign up" link. See {@link HudsonPrivateSecurityRealm} implementation
 * for an example of this.
 *
 * <dt>config.jelly</dt>
 * <dd>
 * This view is used to render the configuration page in the system config screen.
 * </dl>
 *
 * @author Kohsuke Kawaguchi
 * @since 1.160
 * @see PluginServletFilter
 */
public abstract class SecurityRealm implements Describable<SecurityRealm>, ExtensionPoint {
    /**
     * Creates fully-configured {@link AuthenticationManager} that performs authentication
     * against the user realm. The implementation hides how such authentication manager
     * is configured.
     *
     * <p>
     * {@link AuthenticationManager} instantiation often depends on the user-specified parameters
     * (for example, if the authentication is based on LDAP, the user needs to specify
     * the host name of the LDAP server.) Such configuration is expected to be
     * presented to the user via <tt>config.jelly</tt> and then
     * captured as instance variables inside the {@link SecurityRealm} implementation.
     *
     * <p>
     * Your {@link SecurityRealm} may also wants to install a servlet {@link Filter}
     * through {@link PluginServletFilter} to do a part of the authentication.
     */
    public abstract SecurityComponents createSecurityComponents();

    /**
     * {@inheritDoc}
     *
     * <p>
     * {@link SecurityRealm} is a singleton resource in Hudson, and therefore
     * it's always configured through <tt>config.jelly</tt> and never with
     * <tt>global.jelly</tt>. 
     */
    public abstract Descriptor<SecurityRealm> getDescriptor();

    /**
     * Returns the URL to submit a form for the authentication.
     * There's no need to override this, except for {@link LegacySecurityRealm}.
     */
    public String getAuthenticationGatewayUrl() {
        return "j_acegi_security_check";
    }

    /**
     * Gets the target URL of the "login" link.
     * There's no need to override this, except for {@link LegacySecurityRealm}.
     * On legacy implementation this should point to "longinEntry", which
     * is protected by <tt>web.xml</tt>, so that the user can be eventually authenticated
     * by the container.
     */
    public String getLoginUrl() {
        return "login";
    }

    /**
     * Returns true if this {@link SecurityRealm} allows online sign-up.
     * This creates a hyperlink that redirects users to <tt>CONTEXT_ROOT/signUp</tt>,
     * which will be served by the <tt>signup.jelly</tt> view of this class.
     *
     * <p>
     * If the implementation needs to redirect the user to a different URL
     * for signing up, use the following jelly script as <tt>signup.jelly</tt>
     *
     * <pre><xmp>
     * <st:redirect url="http://www.sun.com/" xmlns:st="jelly:stapler"/>
     * </xmp></pre>
     */
    public boolean allowsSignup() {
        Class clz = getClass();
        return clz.getClassLoader().getResource(clz.getName().replace('.','/')+"/signup.jelly")!=null;
    }

    /**
     * {@link DefaultManageableImageCaptchaService} holder to defer initialization.
     */
    private static final class CaptchaService {
        private static final DefaultManageableImageCaptchaService INSTANCE = new DefaultManageableImageCaptchaService();
    }

    /**
     * Generates a captcha image.
     */
    public final void doCaptcha(StaplerRequest req, StaplerResponse rsp) throws IOException {
        String id = req.getSession().getId();
        rsp.setContentType("image/png");
        rsp.addHeader("Cache-Control","no-cache");
        ImageIO.write( CaptchaService.INSTANCE.getImageChallengeForID(id), "PNG", rsp.getOutputStream() );
    }

    /**
     * Validates the captcha.
     */
    protected final boolean validateCaptcha(String text) {
        try {
            String id = Stapler.getCurrentRequest().getSession().getId();
            Boolean b = CaptchaService.INSTANCE.validateResponseForID(id, text);
            return b!=null && b;
        } catch (CaptchaServiceException e) {
            LOGGER.log(Level.INFO, "Captcha validation had a problem",e);
            return false;
        }
    }

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
     * Holder for the SecurityComponents.
     */
    private transient SecurityComponents securityComponents;

    /**
     * Use this function to get the security components, without necessarily
     * recreating them.
     */
    public synchronized SecurityComponents getSecurityComponents() {
        if (this.securityComponents == null) {
            this.securityComponents = this.createSecurityComponents();
        }
        return this.securityComponents;
    }

    /**
     * Filter to run for this SecurityRealm.  The default is the 
     * ChainServletFilter "filter" from 
     * /WEB-INF/security/SecurityFilters.groovy.
     * Subclasses can override this to completely change the filter sequence.
     * For merely adding filters, it may be easier to use 
     * {@link PluginServletFilter}.
     */
    public Filter createFilter(FilterConfig filterConfig) {
        Binding binding = new Binding();
        SecurityComponents sc = this.getSecurityComponents();
        binding.setVariable("authenticationManagerProxy", sc.manager);
        binding.setVariable("userDetailsServiceProxy", sc.userDetails);
        binding.setVariable("rememberMeServicesProxy", sc.rememberMe);
        BeanBuilder builder = new BeanBuilder();
        builder.parse(filterConfig.getServletContext().getResourceAsStream("/WEB-INF/security/SecurityFilters.groovy"),binding);
        WebApplicationContext context = builder.createApplicationContext();
        return (Filter) context.getBean("filter");
    }

    /**
     * Singleton constant that represents "no authentication."
     */
    public static final SecurityRealm NO_AUTHENTICATION = new None();

    private static class None extends SecurityRealm {
        public SecurityComponents createSecurityComponents() {
            return new SecurityComponents(new AuthenticationManager() {
                public Authentication authenticate(Authentication authentication) {
                    return authentication;
                }
            });
        }

        /**
         * This special instance is not configurable explicitly,
         * so it doesn't have a descriptor.
         */
        public Descriptor<SecurityRealm> getDescriptor() {
            return null;
        }

        /**
         * We don't need any filter for this {@link SecurityRealm}.
         */
        @Override
        public Filter createFilter(FilterConfig filterConfig) {
            return new ChainedServletFilter();
        }

        /**
         * Maintain singleton semantics.
         */
        private Object readResolve() {
            return NO_AUTHENTICATION;
        }
    }

    /**
     * Just a tuple so that we can create various inter-related security related objects and
     * return them all at once.
     *
     * <p>
     * None of the fields are ever null.
     *
     * @see SecurityRealm#createSecurityComponents() 
     */
    public static final class SecurityComponents {
        public final AuthenticationManager manager;
        public final UserDetailsService userDetails;
        public final RememberMeServices rememberMe;

        public SecurityComponents() {
            // we use AuthenticationManagerProxy here just as an implementation that fails all the time,
            // not as a proxy. No one is supposed to use this as a proxy.
            this(new AuthenticationManagerProxy());
        }

        public SecurityComponents(AuthenticationManager manager) {
            // we use UserDetailsServiceProxy here just as an implementation that fails all the time,
            // not as a proxy. No one is supposed to use this as a proxy.
            this(manager,new UserDetailsServiceProxy());
        }

        public SecurityComponents(AuthenticationManager manager, UserDetailsService userDetails) {
            this(manager,userDetails,createRememberMeService(userDetails));
        }

        public SecurityComponents(AuthenticationManager manager, UserDetailsService userDetails, RememberMeServices rememberMe) {
            assert manager!=null && userDetails!=null && rememberMe!=null;
            this.manager = manager;
            this.userDetails = userDetails;
            this.rememberMe = rememberMe;
        }

        private static RememberMeServices createRememberMeService(UserDetailsService uds) {
            // create our default TokenBasedRememberMeServices, which depends on the availability of the secret key
            TokenBasedRememberMeServices2 rms = new TokenBasedRememberMeServices2();
            rms.setUserDetailsService(uds);
            rms.setKey(Hudson.getInstance().getSecretKey());
            rms.setParameter("remember_me"); // this is the form field name in login.jelly
            return rms;
        }
    }

    /**
     * All registered {@link SecurityRealm} implementations.
     */
    public static final DescriptorList<SecurityRealm> LIST = new DescriptorList<SecurityRealm>();

    static {
        LIST.load(LegacySecurityRealm.class);
        LIST.load(HudsonPrivateSecurityRealm.class);
        LIST.load(LDAPSecurityRealm.class);
    }

    private static final Logger LOGGER = Logger.getLogger(SecurityRealm.class.getName());
}
