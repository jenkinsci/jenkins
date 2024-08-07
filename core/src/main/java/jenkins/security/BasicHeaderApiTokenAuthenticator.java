package jenkins.security;

import static java.util.logging.Level.WARNING;

import hudson.Extension;
import hudson.model.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Checks if the password given in the BASIC header matches the user's API token.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.576
 */
@Restricted(NoExternalUse.class)
@Extension
public class BasicHeaderApiTokenAuthenticator extends BasicHeaderAuthenticator {
    /**
     * Note: if the token does not exist or does not match, we do not use {@link SecurityListener#fireFailedToAuthenticate(String)}
     * because it will be done in the {@link BasicHeaderRealPasswordAuthenticator} in the case the password is not valid either
     */
    @Override
    public Authentication authenticate2(HttpServletRequest req, HttpServletResponse rsp, String username, String password) throws ServletException {
        User u = BasicApiTokenHelper.isConnectingUsingApiToken(username, password);
        if (u != null) {
            Authentication auth;
            try {
                UserDetails userDetails = u.getUserDetailsForImpersonation2();
                auth = u.impersonate(userDetails);

                SecurityListener.fireAuthenticated2(userDetails);
            } catch (UsernameNotFoundException x) {
                // The token was valid, but the impersonation failed. This token is clearly not his real password,
                // so there's no point in continuing the request processing. Report this error and abort.
                LOGGER.log(WARNING, "API token matched for user " + username + " but the impersonation failed", x);
                throw new ServletException(x);
            }

            req.setAttribute(BasicHeaderApiTokenAuthenticator.class.getName(), true);
            return auth;
        }
        return null;
    }

    private static final Logger LOGGER = Logger.getLogger(BasicHeaderApiTokenAuthenticator.class.getName());
}
