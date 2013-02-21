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

import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jenkins.security.HMACConfidentialKey;
import org.acegisecurity.ui.rememberme.TokenBasedRememberMeServices;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.Authentication;
import org.apache.commons.codec.binary.Base64;
import org.springframework.util.Assert;

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

    /**
     * Used to compute the token signature securely.
     */
    private static final HMACConfidentialKey MAC = new HMACConfidentialKey(TokenBasedRememberMeServices.class,"mac");
}
