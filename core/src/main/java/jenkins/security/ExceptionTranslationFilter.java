/* Copyright 2004, 2005, 2006 Acegi Technology Pty Limited
 * Copyright (c) 2020 CloudBees, Inc.
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
package jenkins.security;

import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.AcegiSecurityException;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.AuthenticationTrustResolver;
import org.acegisecurity.AuthenticationTrustResolverImpl;
import org.acegisecurity.InsufficientAuthenticationException;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.ui.AbstractProcessingFilter;
import org.acegisecurity.ui.AccessDeniedHandler;
import org.acegisecurity.ui.AccessDeniedHandlerImpl;
import org.acegisecurity.ui.AuthenticationEntryPoint;
import org.acegisecurity.ui.savedrequest.SavedRequest;
import org.acegisecurity.util.PortResolver;
import org.acegisecurity.util.PortResolverImpl;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles any {@code AccessDeniedException} and {@code AuthenticationException} thrown within the
 * filter chain.
 * <p>
 * This filter is necessary because it provides the bridge between Java exceptions and HTTP responses.
 * It is solely concerned with maintaining the user interface. This filter does not do any actual security enforcement.
 * </p>
 * <p>
 * If an {@link AuthenticationException} is detected, the filter will launch the {@code authenticationEntryPoint}.
 * This allows common handling of authentication failures originating from any subclass of
 * {@code AbstractSecurityInterceptor}.
 * </p>
 * <p>
 * If an {@link AccessDeniedException} is detected, the filter will determine whether or not the user is an anonymous
 * user. If they are an anonymous user, the {@code authenticationEntryPoint} will be launched. If they are not
 * an anonymous user, the filter will delegate to the {@code AccessDeniedHandler}.
 * By default the filter will use {@code AccessDeniedHandlerImpl}.
 * </p>
 * <p>
 * To use this filter, it is necessary to specify the following properties:
 * </p>
 * <ul>
 * <li>{@code authenticationEntryPoint} indicates the handler that
 * should commence the authentication process if an
 * {@code AuthenticationException} is detected. Note that this may also
 * switch the current protocol from http to https for an SSL login.</li>
 * <li>{@code portResolver} is used to determine the "real" port that a
 * request was received on.</li>
 * </ul>
 * <P>
 * <B>Do not use this class directly.</B> Instead configure
 * {@code web.xml} to use the {@code FilterToBeanProxy}.
 * </p>
 *
 * @author Ben Alex
 * @author colin sampaleanu
 * @version $Id: ExceptionTranslationFilter.java 2134 2007-09-19 16:41:06Z luke_t $
 */
public class ExceptionTranslationFilter implements Filter, InitializingBean {

    //~ Static fields/initializers =====================================================================================

	private static final Logger LOGGER = Logger.getLogger(ExceptionTranslationFilter.class.getName());

	//~ Instance fields ================================================================================================

    private AccessDeniedHandler accessDeniedHandler = new AccessDeniedHandlerImpl();
	private AuthenticationEntryPoint authenticationEntryPoint;
	private AuthenticationTrustResolver authenticationTrustResolver = new AuthenticationTrustResolverImpl();
	private PortResolver portResolver = new PortResolverImpl();
	private boolean createSessionAllowed = true;

	//~ Methods ========================================================================================================

	public void afterPropertiesSet() throws Exception {
		Assert.notNull(authenticationEntryPoint, "authenticationEntryPoint must be specified");
		Assert.notNull(portResolver, "portResolver must be specified");
		Assert.notNull(authenticationTrustResolver, "authenticationTrustResolver must be specified");
	}

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
            ServletException {
		if (!(request instanceof HttpServletRequest)) {
			throw new ServletException("HttpServletRequest required");
		}

		if (!(response instanceof HttpServletResponse)) {
			throw new ServletException("HttpServletResponse required");
		}

		try {
			chain.doFilter(request, response);

			LOGGER.finer("Chain processed normally");
		}
		catch (AuthenticationException | AccessDeniedException ex) {
			handleException(request, response, chain, ex);
		} catch (ServletException ex) {
			if (ex.getRootCause() instanceof AuthenticationException || ex.getRootCause() instanceof AccessDeniedException) {
				handleException(request, response, chain, (AcegiSecurityException) ex.getRootCause());
			}
			else {
				throw ex;
			}
		}
		catch (IOException ex) {
			throw ex;
		}
	}

	public AuthenticationEntryPoint getAuthenticationEntryPoint() {
		return authenticationEntryPoint;
	}

	public AuthenticationTrustResolver getAuthenticationTrustResolver() {
		return authenticationTrustResolver;
	}

	public PortResolver getPortResolver() {
		return portResolver;
	}

	private void handleException(ServletRequest request, ServletResponse response, FilterChain chain,
			AcegiSecurityException exception) throws IOException, ServletException {
		if (exception instanceof AuthenticationException) {
			LOGGER.log(Level.FINER, "Authentication exception occurred; redirecting to authentication entry point", exception);

			sendStartAuthentication(request, response, chain, (AuthenticationException) exception);
		}
		else if (exception instanceof AccessDeniedException) {
			if (authenticationTrustResolver.isAnonymous(SecurityContextHolder.getContext().getAuthentication())) {
					LOGGER.log(Level.FINER, "Access is denied (user is anonymous); redirecting to authentication entry point",
						exception);

				sendStartAuthentication(request, response, chain, new InsufficientAuthenticationException(
						"Full authentication is required to access this resource",exception));
			}
			else {
				LOGGER.log(Level.FINER, "Access is denied (user is not anonymous); delegating to AccessDeniedHandler",
						exception);

				accessDeniedHandler.handle(request, response, (AccessDeniedException) exception);
			}
		}
	}

	/**
	 * If {@code true}, indicates that {@code SecurityEnforcementFilter} is permitted to store the target
	 * URL and exception information in the {@link HttpSession} (the default).
     * In situations where you do not wish to unnecessarily create {@link HttpSession}s - because the user agent
     * will know the failed URL, such as with BASIC or Digest authentication - you may wish to
	 * set this property to {@code false}. Remember to also set the
	 * {@link org.acegisecurity.context.HttpSessionContextIntegrationFilter#allowSessionCreation}
	 * to {@code false} if you set this property to {@code false}.
	 *
	 * @return {@code true} if the {@link HttpSession} will be
	 * used to store information about the failed request, {@code false}
	 * if the {@link HttpSession} will not be used
	 */
	public boolean isCreateSessionAllowed() {
		return createSessionAllowed;
	}

	protected void sendStartAuthentication(ServletRequest request, ServletResponse response, FilterChain chain,
			AuthenticationException reason) throws ServletException, IOException {
		HttpServletRequest httpRequest = (HttpServletRequest) request;

		SavedRequest savedRequest = new SavedRequest(httpRequest, portResolver);

		LOGGER.finer("Authentication entry point being called; SavedRequest added to Session: " + savedRequest);

		if (createSessionAllowed) {
			// Store the HTTP request itself. Used by AbstractProcessingFilter
			// for redirection after successful authentication (SEC-29)
			httpRequest.getSession().setAttribute(AbstractProcessingFilter.ACEGI_SAVED_REQUEST_KEY, savedRequest);
		}

		// SEC-112: Clear the SecurityContextHolder's Authentication, as the
		// existing Authentication is no longer considered valid
		SecurityContextHolder.getContext().setAuthentication(null);

		authenticationEntryPoint.commence(httpRequest, response, reason);
	}

	public void setAccessDeniedHandler(AccessDeniedHandler accessDeniedHandler) {
		Assert.notNull(accessDeniedHandler, "AccessDeniedHandler required");
		this.accessDeniedHandler = accessDeniedHandler;
	}

	public void setAuthenticationEntryPoint(AuthenticationEntryPoint authenticationEntryPoint) {
		this.authenticationEntryPoint = authenticationEntryPoint;
	}

	public void setAuthenticationTrustResolver(AuthenticationTrustResolver authenticationTrustResolver) {
		this.authenticationTrustResolver = authenticationTrustResolver;
	}

	public void setCreateSessionAllowed(boolean createSessionAllowed) {
		this.createSessionAllowed = createSessionAllowed;
	}

	public void setPortResolver(PortResolver portResolver) {
		this.portResolver = portResolver;
	}

    public void init(FilterConfig filterConfig) throws ServletException {
    }

    public void destroy() {
    }

}
