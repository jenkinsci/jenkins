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

import hudson.Functions;
import hudson.model.User;
import jenkins.model.Jenkins;
import jenkins.security.HMACConfidentialKey;
import jenkins.security.ImpersonatingUserDetailsService;
import jenkins.security.LastGrantedAuthoritiesProperty;
import jenkins.security.seed.UserSeedProperty;
import jenkins.util.SystemProperties;
import org.acegisecurity.Authentication;
import org.acegisecurity.providers.rememberme.RememberMeAuthenticationToken;
import org.acegisecurity.ui.rememberme.TokenBasedRememberMeServices;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UserDetailsService;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link TokenBasedRememberMeServices} with modification so as not to rely
 * on the user password being available.
 *
 * <p>
 * This allows remember-me to work with security realms where the password
 * is never available in clear text.
 *
 * @author Kohsuke Kawaguchi
 */
public class TokenBasedRememberMeServices2 extends TokenBasedRememberMeServices {

    private static final Logger LOGGER = Logger.getLogger(TokenBasedRememberMeServices2.class.getName());

    /**
     * Escape hatch for the check on the maximum date for the expiration duration of the remember me cookie
     */
    @Restricted(NoExternalUse.class)
    public static /* Script Console modifiable */ boolean SKIP_TOO_FAR_EXPIRATION_DATE_CHECK = 
            SystemProperties.getBoolean(TokenBasedRememberMeServices2.class.getName() + ".skipTooFarExpirationDateCheck");

    /**
     * Decorate {@link UserDetailsService} so that we can use information stored in
     * {@link LastGrantedAuthoritiesProperty}.
     *
     * We wrap by {@link ImpersonatingUserDetailsService} in other places too,
     * so this is possibly redundant, but there are many {@link AbstractPasswordBasedSecurityRealm#loadUserByUsername(String)}
     * implementations that do not do it, so doing it helps retrofit old plugins to benefit from
     * the user impersonation improvements. Plus multiple {@link ImpersonatingUserDetailsService}
     * do not incur any real performance penalty.
     */
    @Override
    public void setUserDetailsService(UserDetailsService userDetailsService) {
        super.setUserDetailsService(new ImpersonatingUserDetailsService(userDetailsService));
    }

    @Override
    protected String makeTokenSignature(long tokenExpiryTime, UserDetails userDetails) {
        String userSeed;
        if (UserSeedProperty.DISABLE_USER_SEED) {
            userSeed = "no-seed";
        } else {
            User user = User.getById(userDetails.getUsername(), false);
            if (user == null) {
                return "no-user";
            }
            UserSeedProperty userSeedProperty = user.getProperty(UserSeedProperty.class);
            if (userSeedProperty == null) {
                // if you want to filter out the user seed property, you should consider using the DISABLE_USER_SEED instead
                return "no-prop";
            }
            userSeed = userSeedProperty.getSeed();
        }
        String token = String.join(":", userDetails.getUsername(), Long.toString(tokenExpiryTime), userSeed, getKey());
        return MAC.mac(token);
    }

    @Override
    protected String retrievePassword(Authentication successfulAuthentication) {
        return "N/A";
    }

    @Override
	public void loginSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication successfulAuthentication) {
		// Exit if the principal hasn't asked to be remembered
		if (!rememberMeRequested(request, getParameter())) {
			if (logger.isDebugEnabled()) {
				logger.debug("Did not send remember-me cookie (principal did not set parameter '" +
						getParameter() + "')");
			}

			return;
		}

		Jenkins j = Jenkins.getInstanceOrNull();
		if (j != null && j.isDisableRememberMe()) {
			if (logger.isDebugEnabled()) {
				logger.debug("Did not send remember-me cookie because 'Remember Me' is disabled in " +
						"security configuration (principal did set parameter '" + getParameter() + "')");
			}
			// TODO log warning when receiving remember-me request despite the feature being disabled?
			return;
		}

		Assert.notNull(successfulAuthentication.getPrincipal());
		Assert.notNull(successfulAuthentication.getCredentials());
		Assert.isInstanceOf(UserDetails.class, successfulAuthentication.getPrincipal());

		long expiryTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(tokenValiditySeconds);
		String username = ((UserDetails) successfulAuthentication.getPrincipal()).getUsername();

		String signatureValue = makeTokenSignature(expiryTime, (UserDetails)successfulAuthentication.getPrincipal());
		String tokenValue = username + ":" + expiryTime + ":" + signatureValue;
		String tokenValueBase64 = Base64.getEncoder().encodeToString(tokenValue.getBytes());
		response.addCookie(makeValidCookie(tokenValueBase64, request, tokenValiditySeconds));

		if (logger.isDebugEnabled()) {
			logger.debug("Added remember-me cookie for user '" + username + "', expiry: '" + new Date(expiryTime)
							+ "'");
		}
	}

    @Override
    public Authentication autoLogin(HttpServletRequest request, HttpServletResponse response) {
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j == null) {
            // as this filter could be called during restart, this corrects at least the symptoms
            return null;
        }
        if (j.isDisableRememberMe()) {
            cancelCookie(request, response, null);
            return null;
        } else {
            try {
                // we use a patched version of the super.autoLogin
                String rememberMeValue = findRememberMeCookieValue(request, response);
                if (rememberMeValue == null) {
                    return null;
                }
                return retrieveAuthFromCookie(request, response, rememberMeValue);
            } catch (Exception e) {
                cancelCookie(request, response, "Failed to handle remember-me cookie: " + Functions.printThrowable(e));
                return null;
            }
        }
    }

    /**
     * Patched version of the super.autoLogin with a time-independent equality check for the token validation
     */
    private String findRememberMeCookieValue(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();

        if ((cookies == null) || (cookies.length == 0)) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (ACEGI_SECURITY_HASHED_REMEMBER_ME_COOKIE_KEY.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    // Taken from TokenBasedRememberMeService with slight modification
    // around the token equality check to avoid timing attack
    // and reducing drastically the information provided in the log to avoid potential disclosure
    private @CheckForNull Authentication retrieveAuthFromCookie(HttpServletRequest request, HttpServletResponse response, String cookieValueBase64){
        String cookieValue = decodeCookieBase64(cookieValueBase64);
        if (cookieValue == null) {
            String reason = "Cookie token was not Base64 encoded; value was '" + cookieValueBase64 + "'";
            cancelCookie(request, response, reason);
            return null;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Remember-me cookie detected");
        }

        String[] cookieTokens = StringUtils.delimitedListToStringArray(cookieValue, ":");

        if (cookieTokens.length != 3) {
            cancelCookie(request, response, "Cookie token did not contain 3 tokens separated by [:]");
            return null;
        }

        long tokenExpiryTime;

        try {
            tokenExpiryTime = Long.parseLong(cookieTokens[1]);
        }
        catch (NumberFormatException nfe) {
            cancelCookie(request, response, "Cookie token[1] did not contain a valid number");
            return null;
        }

        if (isTokenExpired(tokenExpiryTime)) {
            cancelCookie(request, response, "Cookie token[1] has expired");
            return null;
        }

        // Check the user exists
        // Defer lookup until after expiry time checked, to
        // possibly avoid expensive lookup
        UserDetails userDetails = loadUserDetails(request, response, cookieTokens);

        if (userDetails == null) {
            cancelCookie(request, response, "Cookie token[0] contained a username without user associated");
            return null;
        }

        if (!isValidUserDetails(request, response, userDetails, cookieTokens)) {
            return null;
        }

        String receivedTokenSignature = cookieTokens[2];
        String expectedTokenSignature = makeTokenSignature(tokenExpiryTime, userDetails);

        boolean tokenValid = MessageDigest.isEqual(
                expectedTokenSignature.getBytes(StandardCharsets.US_ASCII),
                receivedTokenSignature.getBytes(StandardCharsets.US_ASCII)
        );
        if (!tokenValid) {
            cancelCookie(request, response, "Cookie token[2] contained invalid signature");
            return null;
        }

        // By this stage we have a valid token
        if (logger.isDebugEnabled()) {
            logger.debug("Remember-me cookie accepted");
        }

        RememberMeAuthenticationToken auth = new RememberMeAuthenticationToken(this.getKey(), userDetails,
                userDetails.getAuthorities());
        auth.setDetails(authenticationDetailsSource.buildDetails(request));

        // Ensure this session is linked to the user's seed
        if (!UserSeedProperty.DISABLE_USER_SEED) {
            User user = User.get(auth);
            UserSeedProperty userSeed = user.getProperty(UserSeedProperty.class);
            String sessionSeed = userSeed.getSeed();
            request.getSession().setAttribute(UserSeedProperty.USER_SESSION_SEED, sessionSeed);
        }

        return auth;
    }

    /**
     * @return the decoded base64 of the cookie or {@code null} if the value was not correctly encoded
     */
    private @CheckForNull String decodeCookieBase64(@NonNull String base64EncodedValue){
        StringBuilder base64EncodedValueBuilder = new StringBuilder(base64EncodedValue);
        for (int j = 0; j < base64EncodedValueBuilder.length() % 4; j++) {
            base64EncodedValueBuilder.append("=");
        }
        base64EncodedValue = base64EncodedValueBuilder.toString();

        try {
            // any charset should be fine but better safe than sorry
            byte[] decodedPlainValue = Base64.getDecoder().decode(base64EncodedValue.getBytes(StandardCharsets.UTF_8));
            return new String(decodedPlainValue, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    protected Cookie makeValidCookie(String tokenValueBase64, HttpServletRequest request, long maxAge) {
        Cookie cookie = super.makeValidCookie(tokenValueBase64, request, maxAge);
        secureCookie(cookie, request);
        return cookie;
    }

    @Override 
    protected Cookie makeCancelCookie(HttpServletRequest request) {
        Cookie cookie = super.makeCancelCookie(request);
        secureCookie(cookie, request);
        return cookie;
    }
    
    /**
     * Force always the http-only flag and depending on the request, put also the secure flag.
     */
    private void secureCookie(Cookie cookie, HttpServletRequest request){
        // if we can mark the cookie HTTP only, do so to protect this cookie even in case of XSS vulnerability.
        if (SET_HTTP_ONLY!=null) {
            try {
                SET_HTTP_ONLY.invoke(cookie,true);
            } catch (IllegalAccessException | InvocationTargetException e) {
                // ignore
            }
        }

        // if the user is running Jenkins over HTTPS, we also want to prevent the cookie from leaking in HTTP.
        // whether the login is done over HTTPS or not would be a good enough approximation of whether Jenkins runs in
        // HTTPS or not, so use that.
        cookie.setSecure(request.isSecure());
    }

    /**
     * In addition to the expiration requested by the super class, we also check the expiration is not too far in the future.
     * Especially to detect maliciously crafted cookie.
     */
    @Override
    protected boolean isTokenExpired(long tokenExpiryTimeMs) {
        long nowMs = System.currentTimeMillis();
        long maxExpirationMs = TimeUnit.SECONDS.toMillis(tokenValiditySeconds) + nowMs;
        if(!SKIP_TOO_FAR_EXPIRATION_DATE_CHECK && tokenExpiryTimeMs > maxExpirationMs){
            // attempt to use a cookie that has more than the maximum allowed expiration duration
            // was either created before a change of configuration or maliciously crafted
            long diffMs = tokenExpiryTimeMs - maxExpirationMs;
            LOGGER.log(Level.WARNING, "Attempt to use a cookie with an expiration duration larger than the one configured (delta of: {0} ms)", diffMs);
            return true;
        }
        // Check it has not expired
        if (tokenExpiryTimeMs < nowMs) {
            return true;
        }
        return false;
    }

    /**
     * Used to compute the token signature securely.
     */
    private static final HMACConfidentialKey MAC = new HMACConfidentialKey(TokenBasedRememberMeServices.class,"mac");

    private static final Method SET_HTTP_ONLY;

	static {
		Method m = null;
		try {
			m = Cookie.class.getMethod("setHttpOnly", boolean.class);
		} catch (NoSuchMethodException x) { // 3.0+
		}
        SET_HTTP_ONLY = m;
	}
}
