/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.security;

import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.logging.Logger;
import static java.util.logging.Level.SEVERE;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.ui.rememberme.RememberMeServices;
import org.acegisecurity.userdetails.UserDetailsService;

/**
 * {@link Filter} that Jenkins uses to implement security support.
 *
 * <p>
 * This is the instance the servlet container creates, but
 * internally this just acts as a proxy to the real {@link Filter},
 * created by {@link SecurityRealm#createFilter(FilterConfig)}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.160
 */
public class HudsonFilter implements Filter {
    /**
     * The SecurityRealm specific filter.
     */
    private volatile Filter filter;
    
    /**
     * The {@link #init(FilterConfig)} may be called before the Jenkins instance is up (which is
     * required for initialization of the filter).  So we store the
     * filterConfig for later lazy-initialization of the filter.
     */
    private FilterConfig filterConfig;

    /**
     * {@link AuthenticationManager} proxy so that the acegi filter chain can stay the same
     * even when security setting is reconfigured.
     *
     * @deprecated in 1.271.
     * This proxy always delegate to {@code Hudson.getInstance().getSecurityRealm().getSecurityComponents().manager},
     * so use that instead.
     */
    @Deprecated
    public static final AuthenticationManagerProxy AUTHENTICATION_MANAGER = new AuthenticationManagerProxy();

    /**
     * {@link UserDetailsService} proxy so that the acegi filter chain can stay the same
     * even when security setting is reconfigured.
     *
     * @deprecated in 1.271.
     * This proxy always delegate to {@code Hudson.getInstance().getSecurityRealm().getSecurityComponents().userDetails},
     * so use that instead.
     */
    @Deprecated
    public static final UserDetailsServiceProxy USER_DETAILS_SERVICE_PROXY = new UserDetailsServiceProxy();
    
    /**
     * {@link RememberMeServices} proxy so that the acegi filter chain can stay the same
     * even when security setting is reconfigured.
     *
     * @deprecated in 1.271.
     * This proxy always delegate to {@code Hudson.getInstance().getSecurityRealm().getSecurityComponents().rememberMe},
     * so use that instead.
     */
    @Deprecated
    public static final RememberMeServicesProxy REMEMBER_ME_SERVICES_PROXY = new RememberMeServicesProxy();

    public void init(FilterConfig filterConfig) throws ServletException {
        this.filterConfig = filterConfig;
        // this is how we make us available to the rest of Hudson.
        filterConfig.getServletContext().setAttribute(HudsonFilter.class.getName(),this);
        try {
            Jenkins hudson = Jenkins.getInstance();
            if (hudson != null) {
                // looks like we are initialized after Hudson came into being. initialize it now. See #3069
                LOGGER.fine("Security wasn't initialized; Initializing it...");
                SecurityRealm securityRealm = hudson.getSecurityRealm();
                reset(securityRealm);
                LOGGER.fine("securityRealm is " + securityRealm);
                LOGGER.fine("Security initialized");
            }
        } catch (ExceptionInInitializerError e) {
            // see HUDSON-4592. In some containers this happens before
            // WebAppMain.contextInitialized kicks in, which makes
            // the whole thing fail hard before a nicer error check
            // in WebAppMain.contextInitialized. So for now,
            // just report it here, and let the WebAppMain handle the failure gracefully.
            LOGGER.log(SEVERE, "Failed to initialize Jenkins",e);
        }
    }

    /**
     * Gets the {@link HudsonFilter} created for the given {@link ServletContext}.
     */
    public static HudsonFilter get(ServletContext context) {
        return (HudsonFilter)context.getAttribute(HudsonFilter.class.getName());
    }

    /**
     * Reset the proxies and filter for a change in {@link SecurityRealm}.
     */
    public void reset(SecurityRealm securityRealm) throws ServletException {
        if (securityRealm != null) {
            SecurityRealm.SecurityComponents sc = securityRealm.getSecurityComponents();
            AUTHENTICATION_MANAGER.setDelegate(sc.manager);
            USER_DETAILS_SERVICE_PROXY.setDelegate(sc.userDetails);
            REMEMBER_ME_SERVICES_PROXY.setDelegate(sc.rememberMe);
            // make sure this.filter is always a valid filter.
            Filter oldf = this.filter;
            Filter newf = securityRealm.createFilter(this.filterConfig);
            newf.init(this.filterConfig);
            this.filter = newf;
            if(oldf!=null)
                oldf.destroy();
        } else {
            // no security related filter needed.
            AUTHENTICATION_MANAGER.setDelegate(null);
            USER_DETAILS_SERVICE_PROXY.setDelegate(null);
            REMEMBER_ME_SERVICES_PROXY.setDelegate(null);
            filter = null;
        }
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        LOGGER.entering(HudsonFilter.class.getName(), "doFilter");

        // this is not the best place to do it, but doing it here makes the patch smaller.
        ((HttpServletResponse)response).setHeader("X-Content-Type-Options", "nosniff");

        // to deal with concurrency, we need to capture the object.
        Filter f = filter;

        if(f==null) {
            // Hudson is starting up.
            chain.doFilter(request,response);
        } else {
            f.doFilter(request,response,chain);
        }
    }

    public void destroy() {
        // the filter can be null if the filter is not initialized yet.
        if(filter != null)
            filter.destroy();
    }

    private static final Logger LOGGER = Logger.getLogger(HudsonFilter.class.getName());
}
