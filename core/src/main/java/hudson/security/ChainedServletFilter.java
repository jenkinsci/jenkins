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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * Servlet {@link Filter} that chains multiple {@link Filter}s.
 *
 * @author Kohsuke Kawaguchi
 */
public class ChainedServletFilter implements Filter {
    // array is assumed to be immutable once set
    protected volatile Filter[] filters;

    public ChainedServletFilter() {
        filters = new Filter[0];
    }

    public ChainedServletFilter(Filter... filters) {
        this(Arrays.asList(filters));
    }

    public ChainedServletFilter(Collection<? extends Filter> filters) {
        setFilters(filters);
    }

    public void setFilters(Collection<? extends Filter> filters) {
        this.filters = filters.toArray(new Filter[filters.size()]);
    }

    public void init(FilterConfig filterConfig) throws ServletException {
        if (LOGGER.isLoggable(Level.FINEST))
            for (Filter f : filters)
                LOGGER.finest("ChainedServletFilter contains: " + f);

        for (Filter f : filters)
            f.init(filterConfig);
    }

    public void doFilter(ServletRequest request, ServletResponse response, final FilterChain chain) throws IOException, ServletException {
        LOGGER.entering(ChainedServletFilter.class.getName(), "doFilter");

        new FilterChain() {
            private int position=0;
            // capture the array for thread-safety
            private final Filter[] filters = ChainedServletFilter.this.filters;

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
        for (Filter f : filters)
            f.destroy();
    }

    private static final Logger LOGGER = Logger.getLogger(ChainedServletFilter.class.getName());
}
