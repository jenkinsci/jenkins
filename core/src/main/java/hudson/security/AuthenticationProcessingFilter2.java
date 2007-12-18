package hudson.security;

import org.acegisecurity.ui.webapp.AuthenticationProcessingFilter;

import javax.servlet.http.HttpServletRequest;

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

        if (targetUrl == null) {
            targetUrl = getDefaultTargetUrl();
        }

        return targetUrl;
    }

}
