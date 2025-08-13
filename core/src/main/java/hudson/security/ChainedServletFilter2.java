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
 *
 * @author Kohsuke Kawaguchi
 */
public class ChainedServletFilter2 implements Filter {
    // array is assumed to be immutable once set
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

    public void setFilters(Collection<? extends Filter> filters) {
        this.filters = filters.toArray(new Filter[0]);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        if (LOGGER.isLoggable(Level.FINEST))
            for (Filter f : filters)
                LOGGER.finest("ChainedServletFilter2 contains: " + f);

        for (Filter f : filters)
            f.init(filterConfig);
    }

    private static final Pattern UNINTERESTING_URIS = Pattern.compile("/(images|jsbundles|css|scripts|adjuncts)/|/favicon[.](ico|svg)|/ajax");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, final FilterChain chain) throws IOException, ServletException {
        String uri = request instanceof HttpServletRequest ? ((HttpServletRequest) request).getRequestURI() : "?";
        Level level = UNINTERESTING_URIS.matcher(uri).find() ? Level.FINER : Level.FINE;
        LOGGER.log(level, () -> "starting filter on " + uri);

        new FilterChain() {
            private int position = 0;
            // capture the array for thread-safety
            private final Filter[] filters = ChainedServletFilter2.this.filters;

            @Override
            public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
                if (position == filters.length) {
                    LOGGER.log(level, () -> uri + " end: " + status());
                    chain.doFilter(request, response);
                } else {
                    Filter next = filters[position++];
                    try {
                        LOGGER.log(level, () -> uri + " @" + position + " " + next + " »");
                        next.doFilter(request, response, this);
                        LOGGER.log(level, () -> uri + " @" + position + " " + next + " « success: " + status());
                    } catch (IOException | ServletException | RuntimeException x) {
                        LOGGER.log(level, () -> uri + " @" + position + " " + next + " « " + x + ": " + status());
                        throw x;
                    }
                }
            }

            private int status() {
                return response instanceof HttpServletResponse ? ((HttpServletResponse) response).getStatus() : 0;
            }
        }.doFilter(request, response);

    }

    @Override
    public void destroy() {
        for (Filter f : filters)
            f.destroy();
    }

    private static final Logger LOGGER = Logger.getLogger(ChainedServletFilter2.class.getName());
}
