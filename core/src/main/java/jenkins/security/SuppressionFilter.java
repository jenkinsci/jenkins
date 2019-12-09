package jenkins.security;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.util.PluginServletFilter;
import jenkins.model.Jenkins;
import org.acegisecurity.AccessDeniedException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.Stapler;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

@Restricted(NoExternalUse.class)
public class SuppressionFilter implements Filter {

    private static final Logger LOGGER = Logger.getLogger(SuppressionFilter.class.getName());

    @Initializer(after = InitMilestone.STARTED)
    public static void init() throws ServletException {
        HttpResponses.setErrorDetailsFilter((code, cause) -> showStackTrace());
        PluginServletFilter.addFilter(new SuppressionFilter());
    }

    public void init(FilterConfig filterConfig) throws ServletException {
        // no-op
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            chain.doFilter(request,response);
        } catch (Exception e) {
            if (containsAccessDeniedException(e)) {
                throw e;
            }
            if (Stapler.isSocketException(e)) {
                return;
            }

            if (showStackTrace()) {
                if (e instanceof IOException || e instanceof ServletException || e instanceof RuntimeException) {
                    // thing we can throw without wrapping
                    throw e;
                }
                throwServletException(e);
            } else if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
                String errorId = logException((HttpServletRequest) request,e);

                respondWithSimpleErrorPage((HttpServletResponse) response, errorId);
            }
        }
    }

    private void respondWithSimpleErrorPage(HttpServletResponse response, String errorId) throws ServletException {
        try {
            response.setStatus(SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/html;charset=UTF-8");
            response.setHeader("Cache-Control","no-cache,must-revalidate");
            PrintWriter w;
            try {
                w = response.getWriter();
            } catch (IllegalStateException x) {
                w = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
            }
            w.println("<html><head><title>"+ Messages.SuppressionFilter_Title()+"</title><body>");
            w.println("<p>"+Messages.SuppressionFilter_ContactAdmin(errorId)+"</p>");
            w.println("</body></html>");
            w.close();
        } catch (Error error) {
            throw error;
        } catch (Throwable x) {
            // if we fail to report this error, bail out
            throw new ServletException(Messages.SuppressionFilter_ContactAdmin(errorId)); // not chaining x since it might contain something
        }
    }

    private void throwServletException(Exception e) throws ServletException {
        // ServletException chaining is messed up. We go extra mile here to really
        // chain the cause.
        ServletException servletException = new ServletException("Request processing failed", e);
        try {
            if (servletException.getCause() == null) {
                servletException.initCause(e);
            }
        } catch (IllegalStateException ise) {
            // just in case.
        }

        throw servletException;
    }

    /**
     * Report an error and then generate a unique ID for that error.
     */
    private String logException(HttpServletRequest req, Throwable e) {
        String id = UUID.randomUUID().toString();
        LOGGER.log(Level.WARNING, "Request processing failed. URI=" + req.getRequestURI() + " clientIP=" + req.getRemoteAddr() + " ErrorID=" + id, e);
        return id;
    }

    private static boolean showStackTrace() {
        return Jenkins.get().hasPermission(Jenkins.ADMINISTER);
    }

    public void destroy() {
        // no-op
    }

    private boolean containsAccessDeniedException(Exception exception) {
        Throwable currentException = exception;
        do {
            if (currentException instanceof AccessDeniedException) {
                return true;
            }
            currentException = currentException.getCause();
        } while (currentException != null);
        return false;
    }

}
