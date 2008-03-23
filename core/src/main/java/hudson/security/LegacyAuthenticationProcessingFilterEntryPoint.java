package hudson.security;

import org.acegisecurity.ui.webapp.AuthenticationProcessingFilterEntryPoint;
import org.acegisecurity.AuthenticationException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * {@link AuthenticationProcessingFilterEntryPoint} for
 * {@link LegacySecurityRealm}, which puts the 'from' query parameter
 * into the request, so that the user will be brought back to where
 * he came from, after the authentication.
 *
 * @see LegacySecurityRealm
 * @author Kohsuke Kawaguchi
 */
public class LegacyAuthenticationProcessingFilterEntryPoint extends AuthenticationProcessingFilterEntryPoint {

    public void commence(ServletRequest request, ServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        String requestedWith = ((HttpServletRequest) request).getHeader("X-Requested-With");
        if("XMLHttpRequest".equals(requestedWith)) {
            // container authentication normally relies on session attribute to
            // remember where the user came from, so concurrent AJAX requests
            // often ends up sending users back to AJAX pages after successful login.
            // this is not desirable, so don't redirect AJAX requests to the user.
            // this header value is sent from Prototype.
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_FORBIDDEN);
        } else {
            super.commence(request, response, authException);
        }
    }

    @Override
    protected String determineUrlToUseForThisRequest(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {
        try {
            return getLoginFormUrl()+"?from="+URLEncoder.encode(request.getRequestURI(),"UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(); // impossible
        }
    }
}
