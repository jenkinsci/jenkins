package jenkins.security;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.CompatibleFilter;

@Restricted(NoExternalUse.class)
public class SuspiciousRequestFilter implements CompatibleFilter {

    /** System property name set to true or false to indicate whether or not semicolons should be allowed in URL paths. */
    public static final String ALLOW_SEMICOLONS_IN_PATH = SuspiciousRequestFilter.class.getName() + ".allowSemicolonsInPath";

    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Accessible via System Groovy Scripts")
    public static boolean allowSemicolonsInPath = SystemProperties.getBoolean(ALLOW_SEMICOLONS_IN_PATH, false);
    private static final Logger LOGGER = Logger.getLogger(SuspiciousRequestFilter.class.getName());

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        if (!allowSemicolonsInPath && httpRequest.getRequestURI().contains(";")) {
            LOGGER.warning(() -> "Denying HTTP " + httpRequest.getMethod() + " to " + httpRequest.getRequestURI() +
                    " as it has an illegal semicolon in the path. This behavior can be overridden by setting the system property " +
                    ALLOW_SEMICOLONS_IN_PATH + " to true. For more information, see https://www.jenkins.io/redirect/semicolons-in-urls");
            httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "Semicolons are not allowed in the request URI");
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void destroy() {
    }
}
