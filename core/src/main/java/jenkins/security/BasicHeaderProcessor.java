package jenkins.security;

import hudson.security.ACL;
import hudson.util.Scrambler;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.BadCredentialsException;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.ui.AuthenticationEntryPoint;
import org.acegisecurity.ui.rememberme.NullRememberMeServices;
import org.acegisecurity.ui.rememberme.RememberMeServices;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.*;

/**
 * Takes "username:password" given in the <tt>Authorization</tt> HTTP header and authenticates
 * the request.
 *
 * <p>
 * Implementations of {@link BasicHeaderAuthenticator} includes one that accepts the real password,
 * then one that checks the user's API token. We call them all from a single Filter like this,
 * as opposed to using a list of {@link Filter}s, so that multiple filters don't end up trying
 * to authenticate the same header differently and fail.
 *
 * @author Kohsuke Kawaguchi
 * @see ZD-19640
 */
public class BasicHeaderProcessor implements Filter {
    // these fields are supposed to be injected by Spring
    private AuthenticationEntryPoint authenticationEntryPoint;
    private RememberMeServices rememberMeServices = new NullRememberMeServices();

    public void init(FilterConfig filterConfig) throws ServletException {
    }

    public void setAuthenticationEntryPoint(AuthenticationEntryPoint authenticationEntryPoint) {
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    public void setRememberMeServices(RememberMeServices rememberMeServices) {
        this.rememberMeServices = rememberMeServices;
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse rsp = (HttpServletResponse) response;
        String authorization = req.getHeader("Authorization");

        if (authorization!=null && authorization.startsWith("Basic ")) {
            // authenticate the user
            String uidpassword = Scrambler.descramble(authorization.substring(6));
            int idx = uidpassword.indexOf(':');
            if (idx >= 0) {
                String username = uidpassword.substring(0, idx);
                String password = uidpassword.substring(idx+1);

                for (BasicHeaderAuthenticator a : all()) {
                    LOGGER.log(FINER, "Attempting to authenticate with {0}", a);
                    Authentication auth = a.authenticate(req, rsp, username, password);
                    if (auth!=null) {
                        LOGGER.log(FINE, "Request authenticated as {0} by {1}", new Object[]{auth,a});
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

    protected void success(HttpServletRequest req, HttpServletResponse rsp, FilterChain chain, Authentication auth) throws IOException, ServletException {
        rememberMeServices.loginSuccess(req, rsp, auth);

        SecurityContext old = ACL.impersonate(auth);
        try {
            chain.doFilter(req,rsp);
        } finally {
            SecurityContextHolder.setContext(old);
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

    public void destroy() {
    }

    private static final Logger LOGGER = Logger.getLogger(ApiTokenFilter.class.getName());
}
