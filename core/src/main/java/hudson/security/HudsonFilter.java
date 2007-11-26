package hudson.security;

import hudson.model.Hudson;
import hudson.util.spring.BeanBuilder;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

/**
 * {@link Filter} that Hudson uses to implement security support.
 *
 * <p>
 * This is the instance the servlet container creates, but
 * internally this is just a dispatcher that delegates the request
 * to the appropriate filter pipeline based on the current
 * configuration.
 *
 * @author Kohsuke Kawaguchi
 */
public class HudsonFilter implements Filter {
    /**
     * To be used with {@link SecurityMode#LEGACY}.
     */
    private Filter legacy;
    /**
     * To be used with {@link SecurityMode#SECURED}.
     */
    private Filter acegi;

    public void init(FilterConfig filterConfig) throws ServletException {
        legacy = new BasicAuthenticationFilter();
        legacy.init(filterConfig);

        BeanBuilder builder = new BeanBuilder();
        builder.parse(getClass().getResourceAsStream("Filters.groovy"));
        acegi = (Filter) builder.createApplicationContext().getBean("filter");
        acegi.init(filterConfig);
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        Hudson h = Hudson.getInstance();
        if(h==null) {
            // Hudson is starting up.
            chain.doFilter(request,response);
            return;
        }
        switch (h.getSecurity()) {
        case LEGACY:
            legacy.doFilter(request,response,chain);
            break;
        case SECURED:
            acegi.doFilter(request,response,chain);
            break;
        case UNSECURED:
            chain.doFilter(request,response);
            break;
        }
    }

    public void destroy() {
        legacy.destroy();
        acegi.destroy();
    }
}
