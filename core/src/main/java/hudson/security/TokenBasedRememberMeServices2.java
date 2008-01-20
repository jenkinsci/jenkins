package hudson.security;

import org.acegisecurity.ui.rememberme.TokenBasedRememberMeServices;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.Authentication;
import org.apache.commons.codec.digest.DigestUtils;

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
    protected String makeTokenSignature(long tokenExpiryTime, UserDetails userDetails) {
        String expectedTokenSignature = DigestUtils.md5Hex(userDetails.getUsername() + ":" + tokenExpiryTime + ":"
                + "N/A" + ":" + getKey());
        return expectedTokenSignature;
    }

    protected String retrievePassword(Authentication successfulAuthentication) {
        return "N/A";
    }
}
