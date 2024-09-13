/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.cli.CLICommand;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.security.FederatedLoginService.FederatedIdentity;
import hudson.security.captcha.CaptchaSupport;
import hudson.util.DescriptorList;
import hudson.util.PluginServletFilter;
import io.jenkins.servlet.FilterConfigWrapper;
import io.jenkins.servlet.FilterWrapper;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.IdStrategy;
import jenkins.model.Jenkins;
import jenkins.security.AcegiSecurityExceptionFilter;
import jenkins.security.AuthenticationSuccessHandler;
import jenkins.security.BasicHeaderProcessor;
import jenkins.security.stapler.StaplerNotDispatchable;
import jenkins.util.SystemProperties;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.rememberme.AbstractRememberMeServices;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/**
 * Pluggable security realm that connects external user database to Hudson.
 *
 * <p>
 * If additional views/URLs need to be exposed,
 * an active {@link SecurityRealm} is bound to {@code CONTEXT_ROOT/securityRealm/}
 * through {@link jenkins.model.Jenkins#getSecurityRealm()}, so you can define additional pages and
 * operations on your {@link SecurityRealm}.
 *
 * <h2>How do I implement this class?</h2>
 * <p>
 * For compatibility reasons, there are two somewhat different ways to implement a custom SecurityRealm.
 *
 * <p>
 * One is to override the {@link #createSecurityComponents()} and create key Spring Security components
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
 * Jenkins doesn't really need you to fit the standard Spring Security models like {@link AuthenticationManager} and
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
public abstract class SecurityRealm extends AbstractDescribableImpl<SecurityRealm> implements ExtensionPoint {
    /**
     * Captcha Support to be used with this SecurityRealm for User Signup
     */
    private CaptchaSupport captchaSupport;

    /**
     * Creates fully-configured {@link AuthenticationManager} that performs authentication
     * against the user realm. The implementation hides how such authentication manager
     * is configured.
     *
     * <p>
     * {@link AuthenticationManager} instantiation often depends on the user-specified parameters
     * (for example, if the authentication is based on LDAP, the user needs to specify
     * the host name of the LDAP server.) Such configuration is expected to be
     * presented to the user via {@code config.jelly} and then
     * captured as instance variables inside the {@link SecurityRealm} implementation.
     *
     * <p>
     * Your {@link SecurityRealm} may also wants to alter {@link Filter} set up by
     * overriding {@link #createFilter(FilterConfig)}.
     */
    public abstract SecurityComponents createSecurityComponents();

    /**
     * Returns the {@link IdStrategy} that should be used for turning
     * {@link UserDetails#getUsername()} into an ID.
     * Mostly this should be {@link IdStrategy.CaseInsensitive} but there may be occasions when either
     * {@link IdStrategy.CaseSensitive} or {@link IdStrategy.CaseSensitiveEmailAddress} are the correct approach.
     *
     * @return the {@link IdStrategy} that should be used for turning
     *         {@link UserDetails#getUsername()} into an ID.
     * @since 1.566
     */
    public IdStrategy getUserIdStrategy() {
        return IdStrategy.CASE_INSENSITIVE;
    }

    /**
     * Returns the {@link IdStrategy} that should be used for turning {@link hudson.security.GroupDetails#getName()}
     * into an ID.
     * Note: Mostly this should be the same as {@link #getUserIdStrategy()} but some security realms may have legitimate
     * reasons for a different strategy.
     *
     * @return the {@link IdStrategy} that should be used for turning {@link hudson.security.GroupDetails#getName()}
     *         into an ID.
     * @since 1.566
     */
    public IdStrategy getGroupIdStrategy() {
        return getUserIdStrategy();
    }

    /**
     * @deprecated No longer used.
     */
    @Deprecated
    public CliAuthenticator createCliAuthenticator(final CLICommand command) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * {@link SecurityRealm} is a singleton resource in Hudson, and therefore
     * it's always configured through {@code config.jelly} and never with
     * {@code global.jelly}.
     */
    @Override
    public Descriptor<SecurityRealm> getDescriptor() {
        return super.getDescriptor();
    }

    /**
     * Returns the URL to submit a form for the authentication.
     * There's no need to override this, except for {@link LegacySecurityRealm}.
     * @see AuthenticationProcessingFilter2
     */
    public String getAuthenticationGatewayUrl() {
        // Default as of Spring Security 3: https://stackoverflow.com/a/62552368/12916
        // Cannot use the 4+ default of /login since that would clash with Jenkins/login.jelly which would be activated even for GET requests,
        // and which cannot trivially be renamed since it is a fairly well-known URL sometimes used e.g. for K8s liveness checks.
        return "j_spring_security_check";
    }

    /**
     * Gets the target URL of the "login" link.
     * There's no need to override this, except for {@link LegacySecurityRealm}.
     * On legacy implementation this should point to {@code loginEntry}, which
     * is protected by {@code web.xml}, so that the user can be eventually authenticated
     * by the container.
     *
     * <p>
     * Path is relative from the context root of the Hudson application.
     * The URL returned by this method will get the "from" query parameter indicating
     * the page that the user was at.
     */
    public String getLoginUrl() {
        return "login";
    }

    /**
     * Returns true if this {@link SecurityRealm} supports explicit logout operation.
     *
     * <p>
     * If the method returns false, "logout" link will not be displayed. This is useful
     * when authentication doesn't require an explicit login activity (such as NTLM authentication
     * or Kerberos authentication, where Hudson has no ability to log off the current user.)
     *
     * <p>
     * By default, this method returns true.
     *
     * @since 1.307
     */
    public boolean canLogOut() {
        return true;
    }

    /**
     * Controls where the user is sent to after a logout. By default, it's the top page
     * of Hudson, but you can return arbitrary URL.
     *
     * @param req
     *      {@link StaplerRequest2} that represents the current request. Primarily so that
     *      you can get the context path. By the time this method is called, the session
     *      is already invalidated. Never null.
     * @param auth
     *      The {@link Authentication} object that represents the user that was logging in.
     *      This parameter allows you to redirect people to different pages depending on who they are.
     * @return
     *      never null.
     * @since TODO
     * @see #doLogout(StaplerRequest2, StaplerResponse2)
     */
    protected String getPostLogOutUrl2(StaplerRequest2 req, Authentication auth) {
        if (Util.isOverridden(SecurityRealm.class, getClass(), "getPostLogOutUrl2", StaplerRequest.class, Authentication.class)) {
            return getPostLogOutUrl2(StaplerRequest.fromStaplerRequest2(req), auth);
        } else {
            return getPostLogOutUrl2Impl(req, auth);
        }
    }

    /**
     * @deprecated use {@link #getPostLogOutUrl2(StaplerRequest2, Authentication)}
     * @since 2.266
     */
    @Deprecated
    protected String getPostLogOutUrl2(StaplerRequest req, Authentication auth) {
        return getPostLogOutUrl2Impl(StaplerRequest.toStaplerRequest2(req), auth);
    }

    private String getPostLogOutUrl2Impl(StaplerRequest2 req, Authentication auth) {
        if (Util.isOverridden(SecurityRealm.class, getClass(), "getPostLogOutUrl", StaplerRequest.class, org.acegisecurity.Authentication.class) && !insideGetPostLogOutUrl.get()) {
            insideGetPostLogOutUrl.set(true);
            try {
                return getPostLogOutUrl(StaplerRequest.fromStaplerRequest2(req), org.acegisecurity.Authentication.fromSpring(auth));
            } finally {
                insideGetPostLogOutUrl.set(false);
            }
        }
        return req.getContextPath() + "/";
    }

    private static final ThreadLocal<Boolean> insideGetPostLogOutUrl = ThreadLocal.withInitial(() -> false);

    /**
     * @deprecated use {@link #getPostLogOutUrl2}
     * @since 1.314
     */
    @Deprecated
    protected String getPostLogOutUrl(StaplerRequest req, org.acegisecurity.Authentication auth) {
        return getPostLogOutUrl2(StaplerRequest.toStaplerRequest2(req), auth.toSpring());
    }

    public CaptchaSupport getCaptchaSupport() {
        return captchaSupport;
    }

    public void setCaptchaSupport(CaptchaSupport captchaSupport) {
        this.captchaSupport = captchaSupport;
    }

    public List<Descriptor<CaptchaSupport>> getCaptchaSupportDescriptors() {
        return CaptchaSupport.all();
    }

    /**
     * Handles the logout processing.
     *
     * <p>
     * The default implementation erases the session and do a few other clean up, then
     * redirect the user to the URL specified by {@link #getPostLogOutUrl2(StaplerRequest2, Authentication)}.
     *
     * @since TODO
     */
    public void doLogout(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        if (Util.isOverridden(SecurityRealm.class, getClass(), "doLogout", StaplerRequest.class, StaplerResponse.class)) {
            try {
                doLogout(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        } else {
            doLogoutImpl(req, rsp);
        }
    }

    /**
     * @deprecated use {@link #doLogout(StaplerRequest2, StaplerResponse2)}
     * @since 1.314
     */
    @Deprecated
    @StaplerNotDispatchable
    public void doLogout(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            doLogoutImpl(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    void doLogoutImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        HttpSession session = req.getSession(false);
        if (session != null)
            session.invalidate();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.clearContext();

        String contextPath = !req.getContextPath().isEmpty() ? req.getContextPath() : "/";
        resetRememberMeCookie(req, rsp, contextPath);
        clearStaleSessionCookies(req, rsp, contextPath);

        rsp.sendRedirect2(getPostLogOutUrl2(req, auth));
    }

    private void resetRememberMeCookie(StaplerRequest2 req, StaplerResponse2 rsp, String contextPath) {
        Cookie cookie = new Cookie(AbstractRememberMeServices.SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY, "");
        cookie.setMaxAge(0);
        cookie.setSecure(req.isSecure());
        cookie.setHttpOnly(true);
        cookie.setPath(contextPath);
        rsp.addCookie(cookie);
    }

    private void clearStaleSessionCookies(StaplerRequest2 req, StaplerResponse2 rsp, String contextPath) {
        /* While "executableWar.jetty.sessionIdCookieName" and
         * "executableWar.jetty.disableCustomSessionIdCookieName"
         * <https://github.com/jenkinsci/extras-executable-war/blob/6558df699d1366b18d045d2ffda3e970df377873/src/main/java/Main.java#L79-L97>
         * can influence the current running behavior of the generated session cookie, we aren't interested
         * in either of them at all.
         *
         * What matters to us are any stale cookies.
         * Those cookies would have been created by this jenkins in a different incarnation, when it
         * could, perhaps, have had different configuration flags, including for those configurables.
         *
         * Thus, we unconditionally zap all JSESSIONID. cookies.
         * a new cookie will be generated by sendRedirect2(...)
         *
         * We don't care about JSESSIONID cookies outside our path because it's the browser's
         * responsibility not to send them to us in the first place.
         */
        final String cookieName = "JSESSIONID.";
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().startsWith(cookieName)) {
                    LOGGER.log(Level.FINE, "Removing cookie {0} during logout", cookie.getName());
                    // one reason users log out is to clear their session(s)
                    // so tell the browser to drop all old sessions
                    cookie.setMaxAge(0);
                    cookie.setValue("");
                    rsp.addCookie(cookie);
                }
            }
        }
    }

    /**
     * Returns true if this {@link SecurityRealm} allows online sign-up.
     * This creates a hyperlink that redirects users to {@code CONTEXT_ROOT/signUp},
     * which will be served by the {@code signup.jelly} view of this class.
     *
     * <p>
     * If the implementation needs to redirect the user to a different URL
     * for signing up, use the following jelly script as {@code signup.jelly}
     *
     * <pre>{@code <xmp>
     * <st:redirect url="http://www.sun.com/" xmlns:st="jelly:stapler"/>
     * </xmp>}</pre>
     */
    public boolean allowsSignup() {
        Class clz = getClass();
        return clz.getClassLoader().getResource(clz.getName().replace('.', '/') + "/signup.jelly") != null;
    }

    /**
     * Shortcut for {@link UserDetailsService#loadUserByUsername(String)}.
     *
     * @return
     *      never null.
     * @throws UserMayOrMayNotExistException2
     *      If the security realm cannot even tell if the user exists or not.
     * @since 2.266
     */
    public UserDetails loadUserByUsername2(String username) throws UsernameNotFoundException {
        if (Util.isOverridden(SecurityRealm.class, getClass(), "loadUserByUsername", String.class)) {
            try {
                return loadUserByUsername(username).toSpring();
            } catch (org.acegisecurity.AcegiSecurityException x) {
                throw x.toSpring();
            } catch (org.springframework.dao.DataAccessException x) {
                throw x.toSpring();
            }
        } else {
            return getSecurityComponents().userDetails2.loadUserByUsername(username);
        }
    }

    /**
     * @deprecated use {@link #loadUserByUsername2}
     */
    @Deprecated
    public org.acegisecurity.userdetails.UserDetails loadUserByUsername(String username) throws org.acegisecurity.userdetails.UsernameNotFoundException, org.springframework.dao.DataAccessException {
        try {
            return org.acegisecurity.userdetails.UserDetails.fromSpring(loadUserByUsername2(username));
        } catch (AuthenticationException x) {
            throw org.acegisecurity.AuthenticationException.fromSpring(x);
        }
    }

    /**
     * If this {@link SecurityRealm} supports a look up of {@link GroupDetails} by their names, override this method
     * to provide the look up.
     * <p>
     * This information, when available, can be used by {@link AuthorizationStrategy}s to improve the UI and
     * error diagnostics for the user.
     *
     * @param groupname    the name of the group to fetch
     * @param fetchMembers if {@code true} then try and fetch the members of the group if it exists. Trying does not
     *                     imply that the members will be fetched and {@link hudson.security.GroupDetails#getMembers()}
     *                     may still return {@code null}
     * @throws UserMayOrMayNotExistException2 if no conclusive result could be determined regarding the group existence.
     * @throws UsernameNotFoundException     if the group does not exist.
     * @since 2.266
     */
    public GroupDetails loadGroupByGroupname2(String groupname, boolean fetchMembers)
            throws UsernameNotFoundException {
        if (Util.isOverridden(SecurityRealm.class, getClass(), "loadGroupByGroupname", String.class)) {
            try {
                return loadGroupByGroupname(groupname);
            } catch (org.acegisecurity.AcegiSecurityException x) {
                throw x.toSpring();
            } catch (org.springframework.dao.DataAccessException x) {
                throw x.toSpring();
            }
        } else if (Util.isOverridden(SecurityRealm.class, getClass(), "loadGroupByGroupname", String.class, boolean.class)) {
            try {
                return loadGroupByGroupname(groupname, fetchMembers);
            } catch (org.acegisecurity.AcegiSecurityException x) {
                throw x.toSpring();
            } catch (org.springframework.dao.DataAccessException x) {
                throw x.toSpring();
            }
        } else {
            throw new UserMayOrMayNotExistException2(groupname);
        }
    }

    /**
     * @deprecated use {@link #loadGroupByGroupname2}
     */
    @Deprecated
    public GroupDetails loadGroupByGroupname(String groupname) throws org.acegisecurity.userdetails.UsernameNotFoundException, org.springframework.dao.DataAccessException {
        try {
            return loadGroupByGroupname2(groupname, false);
        } catch (AuthenticationException x) {
            throw org.acegisecurity.AuthenticationException.fromSpring(x);
        }
    }

    /**
     * @deprecated use {@link #loadGroupByGroupname2}
     * @since 1.549
     */
    @Deprecated
    public GroupDetails loadGroupByGroupname(String groupname, boolean fetchMembers) throws org.acegisecurity.userdetails.UsernameNotFoundException, org.springframework.dao.DataAccessException {
        try {
            return loadGroupByGroupname2(groupname, fetchMembers);
        } catch (AuthenticationException x) {
            throw org.acegisecurity.AuthenticationException.fromSpring(x);
        }
    }

    /**
     * Starts the user registration process for a new user that has the given verified identity.
     *
     * <p>
     * If the user logs in through a {@link FederatedLoginService}, verified that the current user
     * owns an {@linkplain FederatedIdentity identity}, but no existing user account has claimed that identity,
     * then this method is invoked.
     *
     * <p>
     * The expected behaviour is to confirm that the user would like to create a new account, and
     * associate this federated identity to the newly created account (via {@link FederatedIdentity#addToCurrentUser()}.
     *
     * @throws UnsupportedOperationException
     *      If this implementation doesn't support the signup through this mechanism.
     *      This is the default implementation.
     *
     * @since 1.394
     */
    public HttpResponse commenceSignup(FederatedIdentity identity) {
        throw new UnsupportedOperationException();
    }

    /**
     * Generates a captcha image.
     */
    public final void doCaptcha(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        if (captchaSupport != null) {
            String id = req.getSession().getId();
            rsp.setContentType("image/png");
            // source: https://stackoverflow.com/a/3414217
            rsp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            rsp.setHeader("Pragma", "no-cache");
            rsp.setHeader("Expires", "0");
            captchaSupport.generateImage(id, rsp.getOutputStream());
        }
    }

    /**
     * Validates the captcha.
     */
    protected final boolean validateCaptcha(String text) {
        if (captchaSupport != null) {
            String id = Stapler.getCurrentRequest2().getSession().getId();
            return captchaSupport.validateCaptcha(id, text);
        }

        // If no Captcha Support then bogus validation always returns true
        return true;
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
     * Creates {@link Filter} that all the incoming HTTP requests will go through
     * for authentication.
     *
     * <p>
     * The default implementation uses {@link #getSecurityComponents()} and builds
     * a standard filter chain.
     * But subclasses can override this to completely change the filter sequence.
     *
     * <p>
     * For other plugins that want to contribute {@link Filter}, see
     * {@link PluginServletFilter}.
     *
     * @since TODO
     */
    public Filter createFilter(FilterConfig filterConfig) {
        if (Util.isOverridden(SecurityRealm.class, getClass(), "createFilter", javax.servlet.FilterConfig.class)) {
            return FilterWrapper.toJakartaFilter(createFilter(
                    filterConfig != null ? FilterConfigWrapper.fromJakartaFilterConfig(filterConfig) : null));
        } else {
            return createFilterImpl(filterConfig);
        }
    }

    /**
     * @deprecated use {@link #createFilter(FilterConfig)}
     * @since 1.271
     */
    @Deprecated
    public javax.servlet.Filter createFilter(javax.servlet.FilterConfig filterConfig) {
        return FilterWrapper.fromJakartaFilter(createFilterImpl(
                filterConfig != null ? FilterConfigWrapper.toJakartaFilterConfig(filterConfig) : null));
    }

    private Filter createFilterImpl(FilterConfig filterConfig) {
        LOGGER.entering(SecurityRealm.class.getName(), "createFilterImpl");

        SecurityComponents sc = getSecurityComponents();
        List<Filter> filters = new ArrayList<>();
        {
            HttpSessionSecurityContextRepository httpSessionSecurityContextRepository = new HttpSessionSecurityContextRepository();
            httpSessionSecurityContextRepository.setAllowSessionCreation(false);
            filters.add(new HttpSessionContextIntegrationFilter2(httpSessionSecurityContextRepository));
        }
        { // if any "Authorization: Basic xxx:yyy" is sent this is the filter that processes it
            BasicHeaderProcessor bhp = new BasicHeaderProcessor();
            // if basic authentication fails (which only happens incorrect basic auth credential is sent),
            // respond with 401 with basic auth request, instead of redirecting the user to the login page,
            // since users of basic auth tends to be a program and won't see the redirection to the form
            // page as a failure
            BasicAuthenticationEntryPoint basicAuthenticationEntryPoint = new BasicAuthenticationEntryPoint();
            basicAuthenticationEntryPoint.setRealmName("Jenkins");
            bhp.setAuthenticationEntryPoint(basicAuthenticationEntryPoint);
            bhp.setRememberMeServices(sc.rememberMe2);
            filters.add(bhp);
        }
        {
            AuthenticationProcessingFilter2 apf = new AuthenticationProcessingFilter2(getAuthenticationGatewayUrl());
            apf.setAuthenticationManager(sc.manager2);
            if (SystemProperties.getInteger(SecurityRealm.class.getName() + ".sessionFixationProtectionMode", 1) == 1) {
                // By default, use the 'canonical' protection from Spring Security; see AuthenticationProcessingFilter2#successfulAuthentication for alternative
                apf.setSessionAuthenticationStrategy(new SessionFixationProtectionStrategy());
            }
            apf.setRememberMeServices(sc.rememberMe2);
            final AuthenticationSuccessHandler successHandler = new AuthenticationSuccessHandler();
            successHandler.setTargetUrlParameter("from");
            apf.setAuthenticationSuccessHandler(successHandler);
            apf.setAuthenticationFailureHandler(new SimpleUrlAuthenticationFailureHandler("/loginError"));
            filters.add(apf);
        }
        filters.add(new RememberMeAuthenticationFilter(sc.manager2, sc.rememberMe2));
        filters.addAll(commonFilters());
        return new ChainedServletFilter2(filters);
    }

    protected final List<Filter> commonFilters() {
        // like Jenkins.ANONYMOUS:
        AnonymousAuthenticationFilter apf = new AnonymousAuthenticationFilter("anonymous", "anonymous", List.of(new SimpleGrantedAuthority("anonymous")));
        ExceptionTranslationFilter etf = new ExceptionTranslationFilter(new HudsonAuthenticationEntryPoint("/" + getLoginUrl() + "?from={0}"));
        etf.setAccessDeniedHandler(new AccessDeniedHandlerImpl());
        UnwrapSecurityExceptionFilter usef = new UnwrapSecurityExceptionFilter();
        AcegiSecurityExceptionFilter asef = new AcegiSecurityExceptionFilter();
        return Arrays.asList(apf, etf, usef, asef);
    }

    /**
     * Singleton constant that represents "no authentication."
     */
    public static final SecurityRealm NO_AUTHENTICATION = new None();

    /**
     * Perform a calculation where we should go back after successful login
     *
     * @return Encoded URI where we should go back after successful login
     *         or "/" if no way back or an issue occurred
     *
     * @since 2.4
     */
    @Restricted(DoNotUse.class)
    public static String getFrom() {
        String from = null;
        final StaplerRequest2 request = Stapler.getCurrentRequest2();

        // Try to obtain a return point from the query parameter
        if (request != null) {
            from = request.getParameter("from");
        }

        // On the 404 error page, use the session attribute it sets
        if (request != null && request.getRequestURI().equals(request.getContextPath() + "/404")) {
            final HttpSession session = request.getSession(false);
            if (session != null) {
                final Object attribute = session.getAttribute("from");
                if (attribute != null) {
                    from = attribute.toString();
                }
            }
        }

        // If entry point was not found, try to deduce it from the request URI
        // except pages related to login process and the 404 error page
        if (from == null
                && request != null
                && request.getRequestURI() != null
                // The custom login page makes the next two lines obsolete, but safer to have them.
                && !request.getRequestURI().equals(request.getContextPath() + "/loginError")
                && !request.getRequestURI().equals(request.getContextPath() + "/login")
                && !request.getRequestURI().equals(request.getContextPath() + "/404")) {
            from = request.getRequestURI();
        }

        // If deduced entry point isn't deduced yet or the content is a blank value
        // use the root web point "/" as a fallback
        if (from == null || from.isBlank()) {
            from = "/";
        }
        from = from.trim();

        // Encode the return value
        String returnValue = URLEncoder.encode(from, StandardCharsets.UTF_8);

        // Return encoded value or at least "/" in the case exception occurred during encode()
        // or if the encoded content is blank value
        return returnValue == null || returnValue.isBlank() ? "/" : returnValue;
    }

    private static class None extends SecurityRealm {
        @Override
        public SecurityComponents createSecurityComponents() {
            return new SecurityComponents(new AuthenticationManager() {
                @Override
                public Authentication authenticate(Authentication authentication) {
                    return authentication;
                }
            }, new UserDetailsService() {
                @Override
                public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
                    throw new UsernameNotFoundException(username);
                }
            });
        }

        /**
         * There's no group.
         */
        @Override
        public GroupDetails loadGroupByGroupname2(String groupname, boolean fetchMembers) throws UsernameNotFoundException {
            throw new UsernameNotFoundException(groupname);
        }

        /**
         * We don't need any filter for this {@link SecurityRealm}.
         */
        @Override
        public Filter createFilter(FilterConfig filterConfig) {
            return new ChainedServletFilter2();
        }

        /**
         * Maintain singleton semantics.
         */
        private Object readResolve() {
            return NO_AUTHENTICATION;
        }

        @Extension(ordinal = -100)
        @Symbol("none")
        public static class DescriptorImpl extends Descriptor<SecurityRealm> {

            @NonNull
            @Override
            public String getDisplayName() {
                return Messages.NoneSecurityRealm_DisplayName();
            }

            @Override
            public SecurityRealm newInstance(StaplerRequest2 req, JSONObject formData) throws Descriptor.FormException {
                return NO_AUTHENTICATION;
            }
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
        /**
         * @since 2.266
         */
        public final AuthenticationManager manager2;
        /**
         * @deprecated use {@link #manager2}
         */
        @Deprecated
        public final org.acegisecurity.AuthenticationManager manager;
        /**
         * @since 2.266
         */
        public final UserDetailsService userDetails2;
        /**
         * @deprecated use {@link #userDetails2}
         */
        @Deprecated
        public final org.acegisecurity.userdetails.UserDetailsService userDetails;
        /**
         * @since 2.266
         */
        public final RememberMeServices rememberMe2;
        /**
         * @deprecated use {@link #rememberMe2}
         */
        @Deprecated
        public final org.acegisecurity.ui.rememberme.RememberMeServices rememberMe;

        public SecurityComponents() {
            // we use AuthenticationManagerProxy here just as an implementation that fails all the time,
            // not as a proxy. No one is supposed to use this as a proxy.
            this(new AuthenticationManagerProxy());
        }

        /**
         * @since 2.266
         */
        public SecurityComponents(AuthenticationManager manager) {
            // we use UserDetailsServiceProxy here just as an implementation that fails all the time,
            // not as a proxy. No one is supposed to use this as a proxy.
            this(manager, new UserDetailsServiceProxy());
        }

        /**
         * @deprecated use {@link #SecurityComponents(AuthenticationManager)}
         */
        @Deprecated
        public SecurityComponents(org.acegisecurity.AuthenticationManager manager) {
            this(manager.toSpring());
        }

        /**
         * @since 2.266
         */
        public SecurityComponents(AuthenticationManager manager, UserDetailsService userDetails) {
            this(manager, userDetails, createRememberMeService(userDetails));
        }

        /**
         * @deprecated use {@link #SecurityComponents(AuthenticationManager, UserDetailsService)}
         */
        @Deprecated
        public SecurityComponents(org.acegisecurity.AuthenticationManager manager, org.acegisecurity.userdetails.UserDetailsService userDetails) {
            this(manager.toSpring(), userDetails.toSpring());
        }

        /**
         * @since 2.266
         */
        public SecurityComponents(AuthenticationManager manager, UserDetailsService userDetails, RememberMeServices rememberMe) {
            assert manager != null && userDetails != null && rememberMe != null;
            this.manager2 = manager;
            this.userDetails2 = userDetails;
            this.rememberMe2 = rememberMe;
            this.manager = org.acegisecurity.AuthenticationManager.fromSpring(manager);
            this.userDetails = org.acegisecurity.userdetails.UserDetailsService.fromSpring(userDetails);
            this.rememberMe = org.acegisecurity.ui.rememberme.RememberMeServices.fromSpring(rememberMe);
        }

        /**
         * @deprecated use {@link #SecurityComponents(AuthenticationManager, UserDetailsService, RememberMeServices)}
         */
        @Deprecated
        public SecurityComponents(org.acegisecurity.AuthenticationManager manager, org.acegisecurity.userdetails.UserDetailsService userDetails, org.acegisecurity.ui.rememberme.RememberMeServices rememberMe) {
            this(manager.toSpring(), userDetails.toSpring(), rememberMe.toSpring());
        }

        private static RememberMeServices createRememberMeService(UserDetailsService uds) {
            // create our default TokenBasedRememberMeServices, which depends on the availability of the secret key
            TokenBasedRememberMeServices2 rms = new TokenBasedRememberMeServices2(uds);
            rms.setParameter("remember_me"); // this is the form field name in login.jelly
            return rms;
        }
    }

    /**
     * All registered {@link SecurityRealm} implementations.
     *
     * @deprecated as of 1.286
     *      Use {@link #all()} for read access, and use {@link Extension} for registration.
     */
    @Deprecated
    public static final DescriptorList<SecurityRealm> LIST = new DescriptorList<>(SecurityRealm.class);

    /**
     * Returns all the registered {@link SecurityRealm} descriptors.
     */
    public static DescriptorExtensionList<SecurityRealm, Descriptor<SecurityRealm>> all() {
        return Jenkins.get().getDescriptorList(SecurityRealm.class);
    }


    private static final Logger LOGGER = Logger.getLogger(SecurityRealm.class.getName());

    /**
     * {@link GrantedAuthority} that represents the built-in "authenticated" role, which is granted to
     * anyone non-anonymous.
     * @since 2.266
     */
    public static final GrantedAuthority AUTHENTICATED_AUTHORITY2 = new SimpleGrantedAuthority("authenticated");

    /**
     * @deprecated use {@link #AUTHENTICATED_AUTHORITY2}
     */
    @Deprecated
    public static final org.acegisecurity.GrantedAuthority AUTHENTICATED_AUTHORITY = new org.acegisecurity.GrantedAuthorityImpl("authenticated");

}
