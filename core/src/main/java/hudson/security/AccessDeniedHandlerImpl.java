package hudson.security;

import hudson.model.Hudson;
import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.ui.AccessDeniedHandler;
import org.kohsuke.stapler.Stapler;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;

/**
 * Handles {@link AccessDeniedException} happened during request processing.
 * Specifically, send 403 error code and the login page.
 *
 * @author Kohsuke Kawaguchi
 */
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {
    public void handle(ServletRequest request, ServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse rsp = (HttpServletResponse) response;

        rsp.setStatus(HttpServletResponse.SC_FORBIDDEN);
        req.setAttribute("exception",accessDeniedException);
        Stapler stapler = new Stapler();
        stapler.init(new ServletConfig() {
            public String getServletName() {
                throw new UnsupportedOperationException();
            }

            public ServletContext getServletContext() {
                return Hudson.getInstance().servletContext;
            }

            public String getInitParameter(String name) {
                return null;
            }

            public Enumeration getInitParameterNames() {
                return new Vector().elements();
            }
        });

        stapler.invoke(req,rsp, Hudson.getInstance(),"/accessDenied");
    }
}
