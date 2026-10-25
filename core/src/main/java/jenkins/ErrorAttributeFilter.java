package jenkins;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.CompatibleFilter;
import org.springframework.security.core.Authentication;

/**
 * Record the current user authentication for later impersonation if the response is 404 Not Found.
 *
 * @see Jenkins#generateNotFoundResponse(org.kohsuke.stapler.StaplerRequest2, org.kohsuke.stapler.StaplerResponse2)
 */
@Restricted(NoExternalUse.class)
public class ErrorAttributeFilter implements CompatibleFilter {

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
