package hudson.security;

import org.apache.commons.jelly.JellyTagException;
import org.acegisecurity.AcegiSecurityException;
import org.acegisecurity.ui.ExceptionTranslationFilter;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.FilterChain;
import java.io.IOException;

/**
 * If {@link AcegiSecurityException} caused {@link JellyTagException},
 * rethrow it accordingly so that {@link ExceptionTranslationFilter}
 * can pick it up and initiate the redirection.
 * 
 * @author Kohsuke Kawaguchi
 */
public class UnwrapSecurityExceptionFilter implements Filter {
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            chain.doFilter(request,response);
        } catch (ServletException e) {
            Throwable t = e.getRootCause();
            if (t instanceof JellyTagException) {
                JellyTagException jte = (JellyTagException) t;
                Throwable cause = jte.getCause();
                if (cause instanceof AcegiSecurityException) {
                    AcegiSecurityException se = (AcegiSecurityException) cause;
                    throw new ServletException(se);
                }
            }
            throw e;
        }
    }

    public void destroy() {
    }
}
