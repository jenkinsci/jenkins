package jenkins.security;

import hudson.model.User;
import hudson.security.ACL;
import hudson.util.Scrambler;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * {@link Filter} that performs HTTP basic authentication based on API token.
 *
 * <p>
 * Normally the filter chain would also contain another filter that handles BASIC
 * auth with the real password. Care must be taken to ensure that this doesn't
 * interfere with the other.
 *
 * @author Kohsuke Kawaguchi
 */
public class ApiTokenFilter implements Filter {
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse rsp = (HttpServletResponse) response;
        String authorization = req.getHeader("Authorization");

        if (authorization!=null) {
            // authenticate the user
            String uidpassword = Scrambler.descramble(authorization.substring(6));
            int idx = uidpassword.indexOf(':');
            if (idx >= 0) {
                String username = uidpassword.substring(0, idx);
                String password = uidpassword.substring(idx+1);

                // attempt to authenticate as API token
                User u = User.get(username);
                ApiTokenProperty t = u.getProperty(ApiTokenProperty.class);
                if (t!=null && t.matchesPassword(password)) {
                    // even if we fail to match the password, we aren't rejecting it.
                    // as the user might be passing in a real password.
                    SecurityContext oldContext = ACL.impersonate(u.impersonate());
                    try {
                        request.setAttribute(ApiTokenProperty.class.getName(), u);
                        chain.doFilter(request,response);
                        return;
                    } finally {
                        SecurityContextHolder.setContext(oldContext);
                    }
                }
            }
        }

        chain.doFilter(request,response);
    }

    public void destroy() {
    }
}
