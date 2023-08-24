package jenkins;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.security.core.Authentication;

/**
 * Record the current user authentication for later impersonation if the response is 404 Not Found.
 *
 * @see Jenkins#generateNotFoundResponse(org.kohsuke.stapler.StaplerRequest, org.kohsuke.stapler.StaplerResponse)
 */
@Restricted(NoExternalUse.class)
public class ErrorAttributeFilter implements Filter {

    public static final String USER_ATTRIBUTE = "jenkins.ErrorAttributeFilter.user";

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        final Authentication authentication = Jenkins.getAuthentication2();
        servletRequest.setAttribute(USER_ATTRIBUTE, authentication);
        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {
        // Otherwise the PCT fails
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Otherwise the PCT fails
    }
}
