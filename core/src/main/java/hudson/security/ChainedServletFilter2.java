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

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Servlet {@link Filter} that chains multiple {@link Filter}s.
 * Thread-safe for dynamic filter updates during runtime.
 *
 * @author Kohsuke Kawaguchi
 */
public class ChainedServletFilter2 implements Filter {

    private static final Logger LOGGER = Logger.getLogger(ChainedServletFilter2.class.getName());
    private static final Pattern UNINTERESTING_URIS = Pattern.compile("/(images|jsbundles|css|scripts|adjuncts)/|/favicon[.](ico|svg)|/ajax");

    // volatile ensures atomic reads and visibility across threads without locking HTTP traffic.
    // protected access must be maintained for backwards compatibility with existing subclasses (e.g., HudsonFilter).
    protected volatile Filter[] filters;

    public ChainedServletFilter2() {
        filters = new Filter[0];
    }

    public ChainedServletFilter2(Filter... filters) {
        this(Arrays.asList(filters));
    }

    public ChainedServletFilter2(Collection<? extends Filter> filters) {
        setFilters(filters);
    }

    /**
     * Dynamically updates the filter chain. 
     * In the Jenkins ecosystem, filter lifecycles (init/destroy) are typically managed by the IoC container 
     * (e.g., Guice or the plugin manager). Therefore, this method strictly performs a lock-free array swap 
     * without invoking init() or destroy() to prevent regressions like double-initialization.
     */
    public void setFilters(Collection<? extends Filter> newFilters) {
        // Atomic swap. Since filters is volatile, this immediately becomes visible to all threads 
        // calling doFilter without needing synchronized blocks.
        this.filters = newFilters.toArray(new Filter[0]);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        if (LOGGER.isLoggable(Level.FINEST)) {
            // Snapshot for thread-safe iteration
            Filter[] snapshot = this.filters;
            for (Filter f : snapshot) {
                LOGGER.finest("ChainedServletFilter2 contains: " + f);
            }
        }

        Filter[] snapshot = this.filters;
        for (Filter f : snapshot) {
            f.init(filterConfig);
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, final FilterChain chain) 
            throws IOException, ServletException {
        
        String uri = request instanceof HttpServletRequest 
                ? ((HttpServletRequest) request).getRequestURI() 
                : "?";
        
        Level level = UNINTERESTING_URIS.matcher(uri).find() ? Level.FINER : Level.FINE;
        LOGGER.log(level, () -> "starting filter on " + uri);

        // Capture snapshot array locally. 
        // Because `filters` is volatile, this is a lock-free, atomic, thread-safe read!
        final Filter[] activeFilters = this.filters;

        new FilterChain() {
            private int position = 0;

            @Override
            public void doFilter(ServletRequest req, ServletResponse res) throws IOException, ServletException {
                if (position == activeFilters.length) {
                    LOGGER.log(level, () -> uri + " end: " + status(res));
                    chain.doFilter(req, res);
                } else {
                    Filter next = activeFilters[position++];
                    try {
                        LOGGER.log(level, () -> uri + " @" + position + " " + next + " »");
                        next.doFilter(req, res, this);
                        LOGGER.log(level, () -> uri + " @" + position + " " + next + " « success: " + status(res));
                    } catch (IOException | ServletException | RuntimeException x) {
                        LOGGER.log(level, () -> uri + " @" + position + " " + next + " « " + x + ": " + status(res));
                        throw x;
                    }
                }
            }
            
            private int status(ServletResponse res) {
                return res instanceof HttpServletResponse ? ((HttpServletResponse) res).getStatus() : 0;
            }
        }.doFilter(request, response);
    }

    @Override
    public void destroy() {
        Filter[] snapshot = this.filters;
        this.filters = new Filter[0]; // Clear the active chain

        for (Filter f : snapshot) {
            try {
                f.destroy();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error destroying filter: " + f, e);
            }
        }
    }
}