package hudson.security;

import org.acegisecurity.context.HttpSessionContextIntegrationFilter;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.Authentication;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Erases the {@link SecurityContext} persisted in {@link HttpSession}
 * if {@link InvalidatableUserDetails#isInvalid()} returns true.
 *
 * @see InvalidatableUserDetails
 */
public class HttpSessionContextIntegrationFilter2 extends HttpSessionContextIntegrationFilter {
    public HttpSessionContextIntegrationFilter2() throws ServletException {
    }

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpSession session = ((HttpServletRequest) req).getSession(false);
        if(session!=null) {
            SecurityContext o = (SecurityContext)session.getAttribute(ACEGI_SECURITY_CONTEXT_KEY);
            if(o!=null) {
                Authentication a = o.getAuthentication();
                if(a!=null) {
                    if (a.getPrincipal() instanceof InvalidatableUserDetails) {
                        InvalidatableUserDetails ud = (InvalidatableUserDetails) a.getPrincipal();
                        if(ud.isInvalid())
                            // don't let Acegi see invalid security context
                            session.setAttribute(ACEGI_SECURITY_CONTEXT_KEY,null);
                    }
                }
            }
        }

        super.doFilter(req, res, chain);
    }
}
