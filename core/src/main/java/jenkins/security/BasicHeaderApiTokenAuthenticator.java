package jenkins.security;

import hudson.Extension;
import hudson.model.User;
import org.acegisecurity.Authentication;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.springframework.dao.DataAccessException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.logging.Logger;

import static java.util.logging.Level.*;

/**
 * Checks if the password given in the BASIC header matches the user's API token.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.576
 */
@Extension
public class BasicHeaderApiTokenAuthenticator extends BasicHeaderAuthenticator {
    /**
     * Note: if the token does not exist or does not match, we do not use {@link SecurityListener#fireFailedToAuthenticate(String)}
     * because it will be done in the {@link BasicHeaderRealPasswordAuthenticator} in the case the password is not valid either
     */
    @Override
    public Authentication authenticate(HttpServletRequest req, HttpServletResponse rsp, String username, String password) throws ServletException {
        User u = BasicApiTokenHelper.isConnectingUsingApiToken(username, password);
        if(u != null) {
            Authentication auth;
            try {
                UserDetails userDetails = u.getUserDetailsForImpersonation();
                auth = u.impersonate(userDetails);

                SecurityListener.fireAuthenticated(userDetails);
            } catch (UsernameNotFoundException x) {
                // The token was valid, but the impersonation failed. This token is clearly not his real password,
                // so there's no point in continuing the request processing. Report this error and abort.
                LOGGER.log(WARNING, "API token matched for user " + username + " but the impersonation failed", x);
                throw new ServletException(x);
            } catch (DataAccessException x) {
                throw new ServletException(x);
            }

            req.setAttribute(BasicHeaderApiTokenAuthenticator.class.getName(), true);
            return auth;
        }
        return null;
    }

    private static final Logger LOGGER = Logger.getLogger(BasicHeaderApiTokenAuthenticator.class.getName());
}
