package jenkins.security;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINER;

import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.SecurityRealm;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.CompatibleFilter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.NullRememberMeServices;
import org.springframework.security.web.authentication.RememberMeServices;

/**
 * Takes "username:password" given in the {@code Authorization} HTTP header and authenticates
 * the request.
 *
 * <p>
 * Implementations of {@link BasicHeaderAuthenticator} includes one that accepts the real password,
 * then one that checks the user's API token. We call them all from a single Filter like this,
 * as opposed to using a list of {@link Filter}s, so that multiple filters don't end up trying
 * to authenticate the same header differently and fail.
 *
 * @author Kohsuke Kawaguchi
 */
@Restricted(NoExternalUse.class)
public class BasicHeaderProcessor implements CompatibleFilter {
    private AuthenticationEntryPoint authenticationEntryPoint;
    private RememberMeServices rememberMeServices = new NullRememberMeServices();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    public void setAuthenticationEntryPoint(AuthenticationEntryPoint authenticationEntryPoint) {
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    public void setRememberMeServices(RememberMeServices rememberMeServices) {
        this.rememberMeServices = rememberMeServices;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse rsp = (HttpServletResponse) response;
        String authorization = req.getHeader("Authorization");

        if (authorization != null && authorization.toLowerCase(Locale.ROOT).startsWith("Basic ".toLowerCase(Locale.ROOT))) {
            // authenticate the user
            String uidpassword = null;
            try {
                uidpassword = new String(Base64.getDecoder().decode(authorization.substring(6).getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ex) {
                LOGGER.log(Level.FINE, ex, () -> "Failed to decode authentication from: " + authorization);
                uidpassword = "";
            }
            int idx = uidpassword.indexOf(':');
            if (idx >= 0) {
                String username = uidpassword.substring(0, idx);
                String password = uidpassword.substring(idx + 1);

                if (!authenticationIsRequired(username)) {
                    chain.doFilter(request, response);
                    return;
                }

                for (BasicHeaderAuthenticator a : all()) {
                    LOGGER.log(FINER, "Attempting to authenticate with {0}", a);
                    Authentication auth = a.authenticate2(req, rsp, username, password);
                    if (auth != null) {
                        LOGGER.log(FINE, "Request authenticated as {0} by {1}", new Object[]{auth, a});
                        success(req, rsp, chain, auth);
                        return;
                    }
                }

                fail(req, rsp, new BadCredentialsException("Invalid password/token for user: " + username));
            } else {
                fail(req, rsp, new BadCredentialsException("Malformed HTTP basic Authorization header"));
            }
        } else {
            // not something we care
            chain.doFilter(request, response);
        }
    }

    /**
     * If the request is already authenticated to the same user that the Authorization header claims,
     * for example through the HTTP session, then there's no need to re-authenticate the Authorization header,
     * so we skip that. This avoids stressing {@link SecurityRealm}.
     *
     * This method returns false if we can take this short-cut.
     */
    // taken from BasicProcessingFilter.java
    protected boolean authenticationIsRequired(String username) {
        // Only reauthenticate if username doesn't match SecurityContextHolder and user isn't authenticated
        // (see SEC-53)
        Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();

        if (existingAuth == null || !existingAuth.isAuthenticated()) {
            return true;
        }

        // Limit username comparison to providers which use usernames (ie UsernamePasswordAuthenticationToken)
        // (see SEC-348)

        if (existingAuth instanceof UsernamePasswordAuthenticationToken && !existingAuth.getName().equals(username)) {
            return true;
        }

        // Handle unusual condition where an AnonymousAuthenticationToken is already present
        // This shouldn't happen very often, as BasicProcessingFilter is meant to be earlier in the filter
        // chain than AnonymousProcessingFilter. Nevertheless, presence of both an AnonymousAuthenticationToken
        // together with a BASIC authentication request header should indicate reauthentication using the
        // BASIC protocol is desirable. This behaviour is also consistent with that provided by form and digest,
        // both of which force re-authentication if the respective header is detected (and in doing so replace
        // any existing AnonymousAuthenticationToken). See SEC-610.
        return existingAuth instanceof AnonymousAuthenticationToken;
    }

    protected void success(HttpServletRequest req, HttpServletResponse rsp, FilterChain chain, Authentication auth) throws IOException, ServletException {
        rememberMeServices.loginSuccess(req, rsp, auth);

        try (ACLContext ctx = ACL.as2(auth)) {
            chain.doFilter(req, rsp);
        }
    }

    protected void fail(HttpServletRequest req, HttpServletResponse rsp, BadCredentialsException failure) throws IOException, ServletException {
        LOGGER.log(FINE, "Authentication of BASIC header failed");

        rememberMeServices.loginFail(req, rsp);

        authenticationEntryPoint.commence(req, rsp, failure);
    }

    protected List<? extends BasicHeaderAuthenticator> all() {
        return BasicHeaderAuthenticator.all();
    }

    @Override
    public void destroy() {
    }

    private static final Logger LOGGER = Logger.getLogger(BasicHeaderProcessor.class.getName());
}
