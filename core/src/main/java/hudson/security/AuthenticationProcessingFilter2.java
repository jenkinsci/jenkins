package hudson.security;

import java.util.Properties;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.acegisecurity.AuthenticationException;
import org.acegisecurity.ui.webapp.AuthenticationProcessingFilter;

/**
 * {@link AuthenticationProcessingFilter} with a change for Hudson so that
 * we can pick up the hidden "from" form field defined in <tt>login.jelly</tt>
 * to send the user back to where he came from, after a successful authentication.
 * 
 * @author Kohsuke Kawaguchi
 */
public class AuthenticationProcessingFilter2 extends AuthenticationProcessingFilter {
    @Override
    protected String determineTargetUrl(HttpServletRequest request) {
        String targetUrl = request.getParameter("from");
        request.getSession().setAttribute("from", targetUrl);

        if (targetUrl == null)
            return getDefaultTargetUrl();

        // URL returned from determineTargetUrl() is resolved against the context path,
        // whereas the "from" URL is resolved against the top of the website, so adjust this.
        if(targetUrl.startsWith(request.getContextPath()))
            return targetUrl.substring(request.getContextPath().length());

        // not sure when this happens, but apparently this happens in some case.
        // see #1274
        return targetUrl;
    }

    /**
     * @see org.acegisecurity.ui.AbstractProcessingFilter#determineFailureUrl(javax.servlet.http.HttpServletRequest, org.acegisecurity.AuthenticationException)
     */
    @Override
    protected String determineFailureUrl(HttpServletRequest request, AuthenticationException failed) {
        Properties excMap = getExceptionMappings();
		String failedClassName = failed.getClass().getName();
		String whereFrom = request.getParameter("from");
		request.getSession().setAttribute("from", whereFrom);
		return excMap.getProperty(failedClassName, getAuthenticationFailureUrl());
    }

    /**
     * Leave the information about login failure.
     *
     * <p>
     * Otherwise it seems like Acegi doesn't really leave the detail of the failure anywhere.
     */
    @Override
    protected void onUnsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException {
        super.onUnsuccessfulAuthentication(request, response, failed);
        LOGGER.log(Level.INFO, "Login attempt failed", failed);
    }

    private static final Logger LOGGER = Logger.getLogger(AuthenticationProcessingFilter2.class.getName());
}
