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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jenkins.security.facade.providers.rememberme.RememberMeAuthenticationToken;
import jenkins.security.facade.ui.AuthenticationDetailsSource;
import jenkins.security.facade.ui.AuthenticationDetailsSourceImpl;
import jenkins.security.facade.ui.logout.LogoutHandler;
import org.acegisecurity.Authentication;
import org.acegisecurity.ui.rememberme.RememberMeServices;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UserDetailsService;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.RequestUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.Map;

/**
 * Identifies previously remembered users by a Base-64 encoded cookie.
 * 
 * <p>
 * This implementation does not rely on an external database, so is attractive
 * for simple applications. The cookie will be valid for a specific period from
 * the date of the last
 * {@link #loginSuccess(HttpServletRequest, HttpServletResponse, Authentication)}.
 * As per the interface contract, this method will only be called when the
 * principal completes a successful interactive authentication. As such the time
 * period commences from the last authentication attempt where they furnished
 * credentials - not the time period they last logged in via remember-me. The
 * implementation will only send a remember-me token if the parameter defined by
 * {@link #setParameter(String)} is present.
 * </p>
 * 
 * <p>
 * An {@link org.acegisecurity.userdetails.UserDetailsService} is required by
 * this implementation, so that it can construct a valid
 * <code>Authentication</code> from the returned {@link
 * org.acegisecurity.userdetails.UserDetails}. This is also necessary so that
 * the user's password is available and can be checked as part of the encoded
 * cookie.
 * </p>
 * 
 * <p>
 * The cookie encoded by this implementation adopts the following form:
 * 
 * <pre>
 * username + &quot;:&quot; + expiryTime + &quot;:&quot; + Md5Hex(username + &quot;:&quot; + expiryTime + &quot;:&quot; + password + &quot;:&quot; + key)
 * </pre>
 * 
 * </p>
 * <p>
 * As such, if the user changes their password any remember-me token will be
 * invalidated. Equally, the system administrator may invalidate every
 * remember-me token on issue by changing the key. This provides some reasonable
 * approaches to recovering from a remember-me token being left on a public
 * machine (eg kiosk system, Internet cafe etc). Most importantly, at no time is
 * the user's password ever sent to the user agent, providing an important
 * security safeguard. Unfortunately the username is necessary in this
 * implementation (as we do not want to rely on a database for remember-me
 * services) and as such high security applications should be aware of this
 * occasionally undesired disclosure of a valid username.
 * </p>
 * <p>
 * This is a basic remember-me implementation which is suitable for many
 * applications. However, we recommend a database-based implementation if you
 * require a more secure remember-me approach.
 * </p>
 * <p>
 * By default the tokens will be valid for 14 days from the last successful
 * authentication attempt. This can be changed using
 * {@link #setTokenValiditySeconds(long)}.
 * </p>
 *
 * Copied from acegi-security
 */
public class TokenBasedRememberMeServices implements RememberMeServices, InitializingBean, LogoutHandler {
	// ~ Static fields/initializers
	// =====================================================================================

	public static final String ACEGI_SECURITY_HASHED_REMEMBER_ME_COOKIE_KEY = "ACEGI_SECURITY_HASHED_REMEMBER_ME_COOKIE";

	public static final String DEFAULT_PARAMETER = "_acegi_security_remember_me";

	protected static final Log logger = LogFactory.getLog(TokenBasedRememberMeServices.class);

	// ~ Instance fields
	// ================================================================================================

	protected AuthenticationDetailsSource authenticationDetailsSource = new AuthenticationDetailsSourceImpl();

	private String key;

	private String parameter = DEFAULT_PARAMETER;

	private UserDetailsService userDetailsService;

	protected long tokenValiditySeconds = 1209600; // 14 days

	private boolean alwaysRemember = false;

	private static final int DEFAULT_ORDER = Integer.MAX_VALUE; // ~ default

	private String cookieName = ACEGI_SECURITY_HASHED_REMEMBER_ME_COOKIE_KEY;

	// ~ Methods
	// ========================================================================================================

	public void afterPropertiesSet() throws Exception {
		Assert.hasLength(key);
		Assert.hasLength(parameter);
		Assert.hasLength(cookieName);
		Assert.notNull(userDetailsService);
	}

	/**
	 * Introspects the <code>Applicationcontext</code> for the single instance
	 * of {@link AccessDeniedHandler}. If found invoke
	 * setAccessDeniedHandler(AccessDeniedHandler accessDeniedHandler) method by
	 * providing the found instance of accessDeniedHandler as a method
	 * parameter. If more than one instance of <code>AccessDeniedHandler</code>
	 * is found, the method throws <code>IllegalStateException</code>.
	 * 
	 * @param applicationContext to locate the instance
	 */
	private void autoDetectAndUseAnyUserDetailsService(ApplicationContext applicationContext) {
		Map map = applicationContext.getBeansOfType(UserDetailsService.class);
		if (map.size() > 1) {
			throw new IllegalArgumentException(
					"More than one UserDetailsService beans detected please refer to the one using "
							+ " [ principalRepositoryBeanRef  ] " + "attribute");
		}
		else if (map.size() == 1) {
			setUserDetailsService((UserDetailsService) map.values().iterator().next());
		}
	}

	public Authentication autoLogin(HttpServletRequest request, HttpServletResponse response) {
		Cookie[] cookies = request.getCookies();

		if ((cookies == null) || (cookies.length == 0)) {
			return null;
		}

		for (int i = 0; i < cookies.length; i++) {
			if (cookieName.equals(cookies[i].getName())) {
				String cookieValue = cookies[i].getValue();

				for (int j = 0; j < cookieValue.length() % 4; j++) {
					cookieValue = cookieValue + "=";
				}

				if (Base64.isArrayByteBase64(cookieValue.getBytes())) {
					if (logger.isDebugEnabled()) {
						logger.debug("Remember-me cookie detected");
					}

					// Decode token from Base64
					// format of token is:
					// username + ":" + expiryTime + ":" +
					// Md5Hex(username + ":" + expiryTime + ":" + password + ":"
					// + key)
					String cookieAsPlainText = new String(Base64.decodeBase64(cookieValue.getBytes()));
					String[] cookieTokens = StringUtils.delimitedListToStringArray(cookieAsPlainText, ":");

					if (cookieTokens.length == 3) {

						long tokenExpiryTime;

						try {
							//PATCH: unbox to prevent DM_BOXED_PRIMITIVE_FOR_PARSING
							tokenExpiryTime = Long.parseLong(cookieTokens[1]);
						}
						catch (NumberFormatException nfe) {
							cancelCookie(request, response,
									"Cookie token[1] did not contain a valid number (contained '" + cookieTokens[1]
											+ "')");

							return null;
						}

						if (isTokenExpired(tokenExpiryTime)) {
							cancelCookie(request, response, "Cookie token[1] has expired (expired on '"
									+ new Date(tokenExpiryTime) + "'; current time is '" + new Date() + "')");

							return null;
						}

						// Check the user exists
						// Defer lookup until after expiry time checked, to
						// possibly avoid expensive lookup
						UserDetails userDetails = loadUserDetails(request, response, cookieTokens);

						if (userDetails == null) {
							cancelCookie(request, response, "Cookie token[0] contained username '" + cookieTokens[0]
									+ "' but was not found");
							return null;
						}

						if (!isValidUserDetails(request, response, userDetails, cookieTokens)) {
							return null;
						}

						// Check signature of token matches remaining details
						// Must do this after user lookup, as we need the
						// DAO-derived password
						// If efficiency was a major issue, just add in a
						// UserCache implementation,
						// but recall this method is usually only called one per
						// HttpSession
						// (as if the token is valid, it will cause
						// SecurityContextHolder population, whilst
						// if invalid, will cause the cookie to be cancelled)
						String expectedTokenSignature = makeTokenSignature(tokenExpiryTime, userDetails);

						if (!expectedTokenSignature.equals(cookieTokens[2])) {
							cancelCookie(request, response, "Cookie token[2] contained signature '" + cookieTokens[2]
									+ "' but expected '" + expectedTokenSignature + "'");

							return null;
						}

						// By this stage we have a valid token
						if (logger.isDebugEnabled()) {
							logger.debug("Remember-me cookie accepted");
						}

						RememberMeAuthenticationToken auth = new RememberMeAuthenticationToken(this.key, userDetails,
								userDetails.getAuthorities());
						auth.setDetails(authenticationDetailsSource.buildDetails((HttpServletRequest) request));

						return auth;
					}
					else {
						cancelCookie(request, response, "Cookie token did not contain 3 tokens; decoded value was '"
								+ cookieAsPlainText + "'");

						return null;
					}
				}
				else {
					cancelCookie(request, response, "Cookie token was not Base64 encoded; value was '" + cookieValue
							+ "'");

					return null;
				}
			}
		}

		return null;
	}

	/**
	 * @param tokenExpiryTime
	 * @param userDetails
	 * @return
	 */
	//PATCH: the MD5 is not used as it's overrided in the really used class
	@SuppressFBWarnings(value = "WEAK_MESSAGE_DIGEST_MD5", justification = "The implementation used overrides this method and use HmacSHA256 instead.")
	protected String makeTokenSignature(long tokenExpiryTime, UserDetails userDetails) {
		String expectedTokenSignature = DigestUtils.md5Hex(userDetails.getUsername() + ":" + tokenExpiryTime + ":"
				+ userDetails.getPassword() + ":" + this.key);
		return expectedTokenSignature;
	}

	protected boolean isValidUserDetails(HttpServletRequest request, HttpServletResponse response,
                                         UserDetails userDetails, String[] cookieTokens) {
		// Immediately reject if the user is not allowed to
		// login
		if (!userDetails.isAccountNonExpired() || !userDetails.isCredentialsNonExpired() || !userDetails.isEnabled()) {
			cancelCookie(request, response, "Cookie token[0] contained username '" + cookieTokens[0]
					+ "' but account has expired, credentials have expired, or user is disabled");

			return false;
		}
		return true;
	}

	protected UserDetails loadUserDetails(HttpServletRequest request, HttpServletResponse response,
                                          String[] cookieTokens) {
		UserDetails userDetails = null;

		try {
			userDetails = this.userDetailsService.loadUserByUsername(cookieTokens[0]);
		}
		catch (UsernameNotFoundException notFound) {
			cancelCookie(request, response, "Cookie token[0] contained username '" + cookieTokens[0]
					+ "' but was not found");

			return null;
		}
		return userDetails;
	}

	protected boolean isTokenExpired(long tokenExpiryTime) {
		// Check it has not expired
		if (tokenExpiryTime < System.currentTimeMillis()) {
			return true;
		}
		return false;
	}

	protected void cancelCookie(HttpServletRequest request, HttpServletResponse response, String reasonForLog) {
		if ((reasonForLog != null) && logger.isDebugEnabled()) {
			logger.debug("Cancelling cookie for reason: " + reasonForLog);
		}

		response.addCookie(makeCancelCookie(request));
	}

	public String getKey() {
		return key;
	}

	public String getParameter() {
		return parameter;
	}

	public long getTokenValiditySeconds() {
		return tokenValiditySeconds;
	}

	public UserDetailsService getUserDetailsService() {
		return userDetailsService;
	}

	public void loginFail(HttpServletRequest request, HttpServletResponse response) {
		cancelCookie(request, response, "Interactive authentication attempt was unsuccessful");
	}

	protected boolean rememberMeRequested(HttpServletRequest request, String parameter) {
		if (alwaysRemember) {
			return true;
		}

		return RequestUtils.getBooleanParameter(request, parameter, false);
	}

	//PATCH: the MD5 is not used as it's overrided in the really used class
	@SuppressFBWarnings(value = "WEAK_MESSAGE_DIGEST_MD5", justification = "The implementation used overrides this method and use HmacSHA256 instead.")
	public void loginSuccess(HttpServletRequest request, HttpServletResponse response,
                             Authentication successfulAuthentication) {
		// Exit if the principal hasn't asked to be remembered
		if (!rememberMeRequested(request, parameter)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Did not send remember-me cookie (principal did not set parameter '" + this.parameter
						+ "')");
			}

			return;
		}

		// Determine username and password, ensuring empty strings
		Assert.notNull(successfulAuthentication.getPrincipal());
		Assert.notNull(successfulAuthentication.getCredentials());

		String username = retrieveUserName(successfulAuthentication);
		String password = retrievePassword(successfulAuthentication);

		// If unable to find a username and password, just abort as
		// TokenBasedRememberMeServices unable to construct a valid token in
		// this case
		if (!StringUtils.hasLength(username) || !StringUtils.hasLength(password)) {
			return;
		}

		long expiryTime = System.currentTimeMillis() + (tokenValiditySeconds * 1000);

		// construct token to put in cookie; format is:
		// username + ":" + expiryTime + ":" + Md5Hex(username + ":" +
		// expiryTime + ":" + password + ":" + key)
		String signatureValue = DigestUtils.md5Hex(username + ":" + expiryTime + ":" + password + ":" + key);
		String tokenValue = username + ":" + expiryTime + ":" + signatureValue;
		String tokenValueBase64 = new String(Base64.encodeBase64(tokenValue.getBytes()));
		response.addCookie(makeValidCookie(tokenValueBase64, request, tokenValiditySeconds));

		if (logger.isDebugEnabled()) {
			logger
					.debug("Added remember-me cookie for user '" + username + "', expiry: '" + new Date(expiryTime)
							+ "'");
		}
	}

	public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
		cancelCookie(request, response, "Logout of user "
				+ (authentication == null ? "Unknown" : authentication.getName()));
	}

	protected String retrieveUserName(Authentication successfulAuthentication) {
		if (isInstanceOfUserDetails(successfulAuthentication)) {
			return ((UserDetails) successfulAuthentication.getPrincipal()).getUsername();
		}
		else {
			return successfulAuthentication.getPrincipal().toString();
		}
	}

	protected String retrievePassword(Authentication successfulAuthentication) {
		if (isInstanceOfUserDetails(successfulAuthentication)) {
			return ((UserDetails) successfulAuthentication.getPrincipal()).getPassword();
		}
		else {
			return successfulAuthentication.getCredentials().toString();
		}
	}

	private boolean isInstanceOfUserDetails(Authentication authentication) {
		return authentication.getPrincipal() instanceof UserDetails;
	}

	protected Cookie makeCancelCookie(HttpServletRequest request) {
		Cookie cookie = new Cookie(cookieName, null);
		cookie.setMaxAge(0);
		cookie.setPath(StringUtils.hasLength(request.getContextPath()) ? request.getContextPath() : "/");

		return cookie;
	}

	protected Cookie makeValidCookie(String tokenValueBase64, HttpServletRequest request, long maxAge) {
		Cookie cookie = new Cookie(cookieName, tokenValueBase64);
		cookie.setMaxAge(new Long(maxAge).intValue());
		cookie.setPath(StringUtils.hasLength(request.getContextPath()) ? request.getContextPath() : "/");

		return cookie;
	}

	public void setAuthenticationDetailsSource(AuthenticationDetailsSource authenticationDetailsSource) {
		Assert.notNull(authenticationDetailsSource, "AuthenticationDetailsSource required");
		this.authenticationDetailsSource = authenticationDetailsSource;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public void setParameter(String parameter) {
		this.parameter = parameter;
	}

	public void setCookieName(String cookieName) {
		this.cookieName = cookieName;
	}

	public void setTokenValiditySeconds(long tokenValiditySeconds) {
		this.tokenValiditySeconds = tokenValiditySeconds;
	}

	public void setUserDetailsService(UserDetailsService userDetailsService) {
		this.userDetailsService = userDetailsService;
	}

	public boolean isAlwaysRemember() {
		return alwaysRemember;
	}

	public void setAlwaysRemember(boolean alwaysRemember) {
		this.alwaysRemember = alwaysRemember;
	}

	public String getCookieName() {
		return cookieName;
	}

}
