package hudson.security;

import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.util.DescriptorList;
import hudson.util.PluginServletFilter;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.userdetails.UserDetailsService;
import org.springframework.context.ApplicationContext;
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

/**
 * Pluggable security realm that connects external user database to Hudson.
 *
 * <p>
 * New implementations should be registered to {@link #LIST}.
 *
 * <p>
 * If additional views/URLs need to be exposed,
 * an active {@link SecurityRealm} is bound to <tt>CONTEXT_ROOT/securityRealm/</tt>
 * through {@link Hudson#getSecurityRealm()}. 
 *
 * <h3>Supported Views</h3>
 * <dl>
 * <dt>loginLink.jelly</dt>
 * <dd>
 * This view renders the login link on the top right corner of every page, when the user
 * is anonymous. For {@link SecurityRealm}s that support user sign-up, this is a good place
 * to show a "sign up" link. See {@link HudsonPrivateSecurityRealm} implementation
 * for an example of this.
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
     * {@link AuthenticationManager} instantiation often depends on the user configuration
     * (for example, if the authentication is based on LDAP, the host name of the LDAP server
     * depends on the user configuration), and such configuration is expected to be
     * captured as instance variables of {@link SecurityRealm} implementation.
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
     * @see SecurityRealm#createSecurityComponents() 
     */
    public static final class SecurityComponents {
        public AuthenticationManager manager;
        public UserDetailsService userDetails;

        public SecurityComponents() {}

        public SecurityComponents(AuthenticationManager manager) {
            this.manager = manager;
        }

        public SecurityComponents(AuthenticationManager manager, UserDetailsService userDetails) {
            this.manager = manager;
            this.userDetails = userDetails;
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
