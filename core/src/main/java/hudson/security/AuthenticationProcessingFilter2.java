package hudson.security;

import org.acegisecurity.ui.webapp.AuthenticationProcessingFilter;
import org.kohsuke.stapler.Stapler;

import javax.servlet.http.HttpServletRequest;

import hudson.model.Hudson;

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

        if (targetUrl == null)
            return getDefaultTargetUrl();
        else
            // URL returned from determineTargetUrl() is resolved against the context path,
            // whereas the "from" URL is resolved against the top of the website, so adjust this. 
            return targetUrl.substring(request.getContextPath().length());
    }

}
