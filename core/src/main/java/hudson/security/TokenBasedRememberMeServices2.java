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
import jenkins.model.Jenkins;
import jenkins.security.HMACConfidentialKey;
import jenkins.security.ImpersonatingUserDetailsService;
import jenkins.security.LastGrantedAuthoritiesProperty;
import org.acegisecurity.Authentication;
import org.acegisecurity.ui.rememberme.TokenBasedRememberMeServices;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UserDetailsService;
import org.apache.commons.codec.binary.Base64;
import org.springframework.util.Assert;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;

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
        String expectedTokenSignature = MAC.mac(userDetails.getUsername() + ":" + tokenExpiryTime + ":"
                + "N/A" + ":" + getKey());
        return expectedTokenSignature;
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

		long expiryTime = System.currentTimeMillis() + (tokenValiditySeconds * 1000);
		String username = ((UserDetails) successfulAuthentication.getPrincipal()).getUsername();

		String signatureValue = makeTokenSignature(expiryTime, (UserDetails)successfulAuthentication.getPrincipal());
		String tokenValue = username + ":" + expiryTime + ":" + signatureValue;
		String tokenValueBase64 = new String(Base64.encodeBase64(tokenValue.getBytes()));
		response.addCookie(makeValidCookie(tokenValueBase64, request, tokenValiditySeconds));

		if (logger.isDebugEnabled()) {
			logger.debug("Added remember-me cookie for user '" + username + "', expiry: '" + new Date(expiryTime)
							+ "'");
		}
	}

    @Override
    public Authentication autoLogin(HttpServletRequest request, HttpServletResponse response) {
        try {
            return super.autoLogin(request, response);
        } catch (Exception e) {
            cancelCookie(request, response, "Failed to handle remember-me cookie: "+Functions.printThrowable(e));
            return null;
        }
    }

	@Override
	protected Cookie makeValidCookie(String tokenValueBase64, HttpServletRequest request, long maxAge) {
		Cookie cookie = super.makeValidCookie(tokenValueBase64, request, maxAge);
        // if we can mark the cookie HTTP only, do so to protect this cookie even in case of XSS vulnerability.
		if (SET_HTTP_ONLY!=null) {
            try {
                SET_HTTP_ONLY.invoke(cookie,true);
            } catch (IllegalAccessException e) {
                // ignore
            } catch (InvocationTargetException e) {
                // ignore
            }
        }

        // if the user is running Jenkins over HTTPS, we also want to prevent the cookie from leaking in HTTP.
        // whether the login is done over HTTPS or not would be a good enough approximation of whether Jenkins runs in
        // HTTPS or not, so use that.
        if (request.isSecure())
            cookie.setSecure(true);
		return cookie;
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
