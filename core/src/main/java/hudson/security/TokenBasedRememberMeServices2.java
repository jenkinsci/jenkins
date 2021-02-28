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

import com.google.common.annotations.VisibleForTesting;
import hudson.model.User;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jenkins.model.Jenkins;
import jenkins.security.HMACConfidentialKey;
import jenkins.security.ImpersonatingUserDetailsService2;
import jenkins.security.LastGrantedAuthoritiesProperty;
import jenkins.security.seed.UserSeedProperty;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.security.authentication.RememberMeAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

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
@Restricted(NoExternalUse.class)
public class TokenBasedRememberMeServices2 extends TokenBasedRememberMeServices {

    private static final Logger LOGGER = Logger.getLogger(TokenBasedRememberMeServices2.class.getName());

    /**
     * Escape hatch for the check on the maximum date for the expiration duration of the remember me cookie
     */
    public static /* Script Console modifiable */ boolean SKIP_TOO_FAR_EXPIRATION_DATE_CHECK = 
            SystemProperties.getBoolean(TokenBasedRememberMeServices2.class.getName() + ".skipTooFarExpirationDateCheck");

    /**
     * Decorate {@link UserDetailsService} so that we can use information stored in
     * {@link LastGrantedAuthoritiesProperty}.
     * <p>
     * We wrap by {@link ImpersonatingUserDetailsService2} in other places too,
     * so this is possibly redundant, but there are many {@link AbstractPasswordBasedSecurityRealm#loadUserByUsername2(String)}
     * implementations that do not do it, so doing it helps retrofit old plugins to benefit from
     * the user impersonation improvements. Plus multiple {@link ImpersonatingUserDetailsService2}
     * do not incur any real performance penalty.
     * <p>
     * {@link TokenBasedRememberMeServices} needs to be used in conjunction with {@link RememberMeAuthenticationProvider}
     * (see {@link AbstractPasswordBasedSecurityRealm#createSecurityComponents})
     * and both need to use the same key and various security plugins need to do the same.
     */
    @SuppressWarnings("deprecation")
    public TokenBasedRememberMeServices2(UserDetailsService userDetailsService) {
        super(Jenkins.get().getSecretKey(), new ImpersonatingUserDetailsService2(userDetailsService));
    }

    @Override
    protected String makeTokenSignature(long tokenExpiryTime, String username, String password) {
        String userSeed;
        if (UserSeedProperty.DISABLE_USER_SEED) {
            userSeed = "no-seed";
        } else {
            User user = User.getById(username, true);
            UserSeedProperty userSeedProperty = user.getProperty(UserSeedProperty.class);
            if (userSeedProperty == null) {
                // if you want to filter out the user seed property, you should consider using the DISABLE_USER_SEED instead
                return "no-prop";
            }
            userSeed = userSeedProperty.getSeed();
        }
        String token = String.join(":", username, Long.toString(tokenExpiryTime), userSeed, getKey());
        return MAC.mac(token);
    }

    @Override
    protected String retrievePassword(Authentication successfulAuthentication) {
        return "N/A";
    }

    @Override
	public void onLoginSuccess(HttpServletRequest request, HttpServletResponse response,
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

        // TODO is it really still necessary to reimplement all of the below, or could we simply override rememberMeRequested?

        Objects.requireNonNull(successfulAuthentication.getPrincipal());
        UserDetails.class.cast(successfulAuthentication.getPrincipal());

		long expiryTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(getTokenValiditySeconds());
		String username = ((UserDetails) successfulAuthentication.getPrincipal()).getUsername();

		String signatureValue = makeTokenSignature(expiryTime, username, ((UserDetails) successfulAuthentication.getPrincipal()).getPassword());
		int tokenLifetime = calculateLoginLifetime(request, successfulAuthentication);
		setCookie(new String[] { username, Long.toString(expiryTime), signatureValue },
				tokenLifetime, request, response);

		if (logger.isDebugEnabled()) {
			logger.debug("Added remember-me cookie for user '" + username + "', expiry: '" + new Date(expiryTime)
							+ "'");
		}
	}

    @Override
    protected UserDetails processAutoLoginCookie(String[] cookieTokens, HttpServletRequest request, HttpServletResponse response) {
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j == null) {
            // as this filter could be called during restart, this corrects at least the symptoms
            throw new InvalidCookieException("Jenkins is not yet running");
        }
        if (j.isDisableRememberMe()) {
            cancelCookie(request, response);
            throw new InvalidCookieException("rememberMe is disabled");
        } else {
            return super.processAutoLoginCookie(cookieTokens, request, response);
        }
    }

    @Override
    protected Authentication createSuccessfulAuthentication(HttpServletRequest request, UserDetails userDetails) {
        Authentication auth = super.createSuccessfulAuthentication(request, userDetails);

        // Ensure this session is linked to the user's seed
        if (!UserSeedProperty.DISABLE_USER_SEED) {
            User user = User.get2(auth);
            UserSeedProperty userSeed = user.getProperty(UserSeedProperty.class);
            String sessionSeed = userSeed.getSeed();
            request.getSession().setAttribute(UserSeedProperty.USER_SESSION_SEED, sessionSeed);
        }

        return auth;
    }

    /**
     * In addition to the expiration requested by the super class, we also check the expiration is not too far in the future.
     * Especially to detect maliciously crafted cookie.
     */
    @Override
    protected boolean isTokenExpired(long tokenExpiryTimeMs) {
        long nowMs = System.currentTimeMillis();
        long maxExpirationMs = TimeUnit.SECONDS.toMillis(getTokenValiditySeconds()) + nowMs;
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

    @VisibleForTesting
    @Override
    protected int getTokenValiditySeconds() {
        return super.getTokenValiditySeconds();
    }

    @VisibleForTesting
    @Override
    protected String getCookieName() {
        return super.getCookieName();
    }

    /**
     * Used to compute the token signature securely.
     */
    private static final HMACConfidentialKey MAC = new HMACConfidentialKey(TokenBasedRememberMeServices.class,"mac");

}
