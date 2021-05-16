package jenkins.security;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @since TODO
 */
@Restricted(NoExternalUse.class)
public class FromRetainingAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    public static final Logger LOGGER = Logger.getLogger(FromRetainingAuthenticationFailureHandler.class.getName());
    private String baseUrl;

    public FromRetainingAuthenticationFailureHandler(String baseUrl) {
        super(baseUrl);
        this.baseUrl = baseUrl;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        final String referer = request.getHeader("Referer");
        if (referer != null) {
            final String path;
            try {
                path = new URL(referer).getPath();
                final String expectedLoginFormUrl = StringUtils.removeEnd(request.getContextPath(), "/") + "/login";
                // We're coming from login, so go back there
                final String query = new URL(referer).getQuery();
                String from = Arrays.stream(query.split("&")).filter(s -> s.startsWith("from=")).map(s -> s.substring(5)).findAny().orElse("%2f");

                final DefaultRedirectStrategy defaultRedirectStrategy = new DefaultRedirectStrategy();
                response.sendRedirect(baseUrl + "?from=" + from);
                return;
            } catch (MalformedURLException e) {
                LOGGER.log(Level.FINE, e, () -> "Failed to parse referer as URL");
            }
        }
        super.onAuthenticationFailure(request, response, exception);
    }
}
