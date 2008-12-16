package hudson.util;

import hudson.ExtensionPoint;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Vector;

/**
 * Servlet {@link Filter} that chains multiple {@link Filter}s, provided by plugins
 *
 * <p>
 * While this class by itself is not an extension point, I'm marking this class
 * as an extension point so that this class will be more discoverable.
 */
public class PluginServletFilter implements Filter, ExtensionPoint {

	private static final List<Filter> LIST = new Vector<Filter>();
	
	private static FilterConfig filterConfig;
    
    public PluginServletFilter() {
    }

    public void init(FilterConfig filterConfig) throws ServletException {
    	PluginServletFilter.filterConfig = filterConfig;
    	synchronized (LIST)  {
    		for (Filter f : LIST) {
    			f.init(filterConfig);
    		}
    	}
    }
    
    public static void addFilter(Filter filter) throws ServletException {
    	synchronized (LIST) {
    		if (filterConfig != null) {
    			filter.init(filterConfig);
    		}
    		LIST.add(filter);
    	}
    }

    public void doFilter(ServletRequest request, ServletResponse response, final FilterChain chain) throws IOException, ServletException {
        new FilterChain() {
            private int position=0;
            // capture the array for thread-safety
            private final Filter[] filters = LIST.toArray(new Filter[LIST.size()]);

            public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
                if(position==filters.length) {
                    // reached to the end
                    chain.doFilter(request,response);
                } else {
                    // call next
                    filters[position++].doFilter(request,response,this);
                }
            }
        }.doFilter(request,response);
    }

    public void destroy() {
        synchronized (LIST)  {
            for (Filter f : LIST)
                f.destroy();
        }
    }
}
