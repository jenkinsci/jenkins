package hudson.security;

import groovy.lang.Binding;
import hudson.model.Hudson;
import hudson.util.spring.BeanBuilder;
import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.userdetails.UserDetailsService;
import org.springframework.web.context.WebApplicationContext;

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
 * @since 1.160
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

    /**
     * {@link AuthenticationManager} proxy so that the acegi filter chain can stay the same
     * even when security setting is reconfigured.
     */
    public static final AuthenticationManagerProxy AUTHENTICATION_MANAGER = new AuthenticationManagerProxy();

    /**
     * {@link UserDetailsService} proxy so that the acegi filter chain can stay the same
     * even when security setting is reconfigured.
     */
    public static final UserDetailsServiceProxy USER_DETAILS_SERVICE_PROXY = new UserDetailsServiceProxy();

    public static final RememberMeServicesProxy REMEMBER_ME_SERVICES_PROXY = new RememberMeServicesProxy();

    public void init(FilterConfig filterConfig) throws ServletException {
        Binding binding = new Binding();
        binding.setVariable("authenticationManagerProxy", AUTHENTICATION_MANAGER);
        binding.setVariable("userDetailsServiceProxy", USER_DETAILS_SERVICE_PROXY);
        binding.setVariable("rememberMeServicesProxy", REMEMBER_ME_SERVICES_PROXY);
        // on some containers this is not ready yet
        // binding.setVariable("app", Hudson.getInstance());
        BeanBuilder builder = new BeanBuilder();
        builder.parse(filterConfig.getServletContext().getResourceAsStream("/WEB-INF/security/SecurityFilters.groovy"),binding);

        WebApplicationContext context = builder.createApplicationContext();
        
        acegi = (Filter) context.getBean("filter");
        acegi.init(filterConfig);

        legacy = (Filter) context.getBean("legacy");
        legacy.init(filterConfig);
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
        // these fields can be null if HudsonFilter.init() fails in the middle
        if(legacy!=null)
            legacy.destroy();
        if(acegi!=null)
            acegi.destroy();
    }
}
