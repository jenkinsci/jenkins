package jenkins;

import org.kohsuke.stapler.framework.adjunct.AdjunctManager;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 *
 * <p>
 * We used to have YUI2 under /scripts/yui/ normally accessed via "/static/12345678/scripts/yui/..."
 * but we moved them into adjuncts and upgraded to YUI3 + 2in3. This filter detects access to these JavaScript
 * in the old location and redirects them to the new location (and new name)
 *
 * @author Kohsuke Kawaguchi
 * @since 1.454
 */
public class YUILegacyRedirectFilter implements Filter {
    private ServletContext context;

    public void init(FilterConfig filterConfig) throws ServletException {
        context = filterConfig.getServletContext();
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest req = (HttpServletRequest) request;
            String uri = req.getRequestURI();
            int idx = uri.indexOf(MAGIC);
            if (idx>=0) {
                // foo/foo-min.js -> yui2-foo/yui2-foo-min.js
                String path = req.getContextPath()+"/"+AdjunctManager.get(context).rootURL+"/yui3"+uri.substring(idx+MAGIC.length()-1).replace("/","/yui2-");
                ((HttpServletResponse)response).sendRedirect(path);
                return;
            }
        }
        
        chain.doFilter(request,response);
    }

    public void destroy() {
    }

    private static final String MAGIC = "/scripts/yui/";
}
