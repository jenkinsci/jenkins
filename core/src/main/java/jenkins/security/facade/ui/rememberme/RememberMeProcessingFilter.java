/* Copyright 2004, 2005, 2006 Acegi Technology Pty Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jenkins.security.facade.ui.rememberme;

import jenkins.security.facade.event.authentication.InteractiveAuthenticationSuccessEvent;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.ui.rememberme.RememberMeServices;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.util.Assert;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


/**
 * Detects if there is no <code>Authentication</code> object in the <code>SecurityContext</code>, and populates it
 * with a remember-me authentication token if a {@link org.acegisecurity.ui.rememberme.RememberMeServices}
 * implementation so requests.<p>Concrete <code>RememberMeServices</code> implementations will have their {@link
 * org.acegisecurity.ui.rememberme.RememberMeServices#autoLogin(HttpServletRequest, HttpServletResponse)} method
 * called by this filter. The <code>Authentication</code> or <code>null</code> returned by that method will be placed
 * into the <code>SecurityContext</code>. The <code>AuthenticationManager</code> will be used, so that any concurrent
 * session management or other authentication-specific behaviour can be achieved. This is the same pattern as with
 * other authentication mechanisms, which call the <code>AuthenticationManager</code> as part of their contract.</p>
 *  <p>If authentication is successful, an {@link
 * org.acegisecurity.event.authentication.InteractiveAuthenticationSuccessEvent} will be published to the application
 * context. No events will be published if authentication was unsuccessful, because this would generally be recorded
 * via an <code>AuthenticationManager</code>-specific application event.</p>
 *  <p><b>Do not use this class directly.</b> Instead configure <code>web.xml</code> to use the {@link
 * org.acegisecurity.util.FilterToBeanProxy}.</p>
 *
 * Copied from acegi-security
 * PATCH: only used in SecurityFilters.groovy
 */
public class RememberMeProcessingFilter implements Filter, InitializingBean, ApplicationEventPublisherAware {
    //~ Static fields/initializers =====================================================================================

    private static final Log logger = LogFactory.getLog(RememberMeProcessingFilter.class);

    //~ Instance fields ================================================================================================

    private ApplicationEventPublisher eventPublisher;
    private AuthenticationManager authenticationManager;
    private RememberMeServices rememberMeServices = new NullRememberMeServices();

    //~ Methods ========================================================================================================

    public void afterPropertiesSet() throws Exception {
		Assert.notNull(authenticationManager, "authenticationManager must be specified");
		Assert.notNull(this.rememberMeServices);
	}

    /**
     * Does nothing - we rely on IoC lifecycle services instead.
     */
    public void destroy() {}

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest)) {
            throw new ServletException("Can only process HttpServletRequest");
        }

        if (!(response instanceof HttpServletResponse)) {
            throw new ServletException("Can only process HttpServletResponse");
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            Authentication rememberMeAuth = rememberMeServices.autoLogin(httpRequest, httpResponse);

            if (rememberMeAuth != null) {
                // Attempt authenticaton via AuthenticationManager
                try {
                	rememberMeAuth = authenticationManager.authenticate(rememberMeAuth);

                    // Store to SecurityContextHolder
                    SecurityContextHolder.getContext().setAuthentication(rememberMeAuth);

                    if (logger.isDebugEnabled()) {
                        logger.debug("SecurityContextHolder populated with remember-me token: '"
                            + SecurityContextHolder.getContext().getAuthentication() + "'");
                    }

                    // Fire event
                    if (this.eventPublisher != null) {
                        eventPublisher.publishEvent(new InteractiveAuthenticationSuccessEvent(
                                SecurityContextHolder.getContext().getAuthentication(), this.getClass()));
                    }
                } catch (AuthenticationException authenticationException) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("SecurityContextHolder not populated with remember-me token, as "
                                + "AuthenticationManager rejected Authentication returned by RememberMeServices: '"
                                + rememberMeAuth + "'; invalidating remember-me token", authenticationException);
                    }

                    rememberMeServices.loginFail(httpRequest, httpResponse);
                }
            }

            chain.doFilter(request, response);
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("SecurityContextHolder not populated with remember-me token, as it already contained: '"
                    + SecurityContextHolder.getContext().getAuthentication() + "'");
            }

            chain.doFilter(request, response);
        }
    }

    public RememberMeServices getRememberMeServices() {
        return rememberMeServices;
    }

    /**
     * Does nothing - we rely on IoC lifecycle services instead.
     *
     * @param ignored not used
     *
     * @throws ServletException DOCUMENT ME!
     */
    public void init(FilterConfig ignored) throws ServletException {}

    public void setApplicationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void setAuthenticationManager(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    public void setRememberMeServices(RememberMeServices rememberMeServices) {
        this.rememberMeServices = rememberMeServices;
    }

}
