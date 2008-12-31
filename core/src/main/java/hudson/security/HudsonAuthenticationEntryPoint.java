package hudson.security;

import org.acegisecurity.AuthenticationException;
import org.acegisecurity.ui.webapp.AuthenticationProcessingFilterEntryPoint;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import java.io.IOException;
import java.net.URLEncoder;
import java.text.MessageFormat;

/**
 * For anonymous requests to pages that require authentication,
 * first respond with {@link HttpServletResponse#SC_FORBIDDEN},
 * then redirect browsers automatically to the login page.
 *
 * <p>
 * This is a compromise to handle programmatic access and
 * real browsers equally well.
 *
 * <p>
 * The page that programs see is entirely white, and it auto-redirects,
 * so humans wouldn't notice it. 
 *
 * @author Kohsuke Kawaguchi
 */
public class HudsonAuthenticationEntryPoint extends AuthenticationProcessingFilterEntryPoint {
    public void commence(ServletRequest request, ServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse rsp = (HttpServletResponse) response;

        String requestedWith = req.getHeader("X-Requested-With");
        if("XMLHttpRequest".equals(requestedWith)) {
            // container authentication normally relies on session attribute to
            // remember where the user came from, so concurrent AJAX requests
            // often ends up sending users back to AJAX pages after successful login.
            // this is not desirable, so don't redirect AJAX requests to the user.
            // this header value is sent from Prototype.
            rsp.sendError(SC_FORBIDDEN);
        } else {
            // give the opportunity to include the target URL
            String loginForm = req.getContextPath()+getLoginFormUrl();
            loginForm = MessageFormat.format(loginForm, URLEncoder.encode(req.getRequestURI(),"UTF-8"));
            req.setAttribute("loginForm", loginForm);

            rsp.setStatus(SC_FORBIDDEN);
            rsp.setContentType("text/html;charset=UTF-8");
            rsp.getWriter().printf(
                "<html><head>" +
                "<meta http-equiv='refresh' content='1;url=%1$s'/>" +
                "<script>window.location.replace('%1$s');</script>" +
                "</head>" +
                "<body style='background-color:white; color:white;'>" +
                "Authentication required</body></html>", loginForm
            );
        }
    }
}
