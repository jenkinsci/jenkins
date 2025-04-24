/*
 * The MIT License
 *
 * Copyright 2023 CloudBees, Inc.
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

package jenkins.util;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.init.Initializer;
import hudson.security.csrf.CrumbExclusion;
import hudson.util.PluginServletFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

/**
 * More convenient and declarative way to use {@link PluginServletFilter}.
 * Register an implementation if you wish to intercept certain HTTP requests.
 * Typical implementations will inspect {@link HttpServletRequest#getPathInfo} to determine if they should be active.
 * @since 2.406
 * @see CrumbExclusion
 */
public interface HttpServletFilter extends ExtensionPoint {

    /**
     * Potentially intercepts or otherwise modifies an HTTP request.
     * @param req as in {@link Filter#doFilter}
     * @param rsp as in {@link Filter#doFilter}
     * @return true if this request was handled; false to proceed with other handlers ({@link FilterChain})
     * @throws IOException as in {@link Filter#doFilter}
     * @throws ServletException as in {@link Filter#doFilter}
     */
    boolean handle(HttpServletRequest req, HttpServletResponse rsp) throws IOException, ServletException;

    @Restricted(DoNotUse.class)
    @Initializer
    static void register() throws ServletException {
        PluginServletFilter.addFilter(new Filter() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse rsp, FilterChain chain) throws IOException, ServletException {
                if (req instanceof HttpServletRequest && rsp instanceof HttpServletResponse) {
                    for (HttpServletFilter filter : ExtensionList.lookup(HttpServletFilter.class)) {
                        if (filter.handle((HttpServletRequest) req, (HttpServletResponse) rsp)) {
                            return;
                        }
                    }
                }
                chain.doFilter(req, rsp);
            }

            @Override
            public void init(FilterConfig filterConfig) {
            }

            @Override
            public void destroy() {
            }
        });
    }

}
