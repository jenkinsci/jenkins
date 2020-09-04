/*
 * The MIT License
 *
 * Copyright 2019 CloudBees, Inc.
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
package jenkins.security;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.util.PluginServletFilter;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Prohibit requests to Jenkins coming through a resource domain URL configured with
 * {@link ResourceDomainConfiguration}, except anything going to {@link ResourceDomainRootAction}.
 *
 * @since 2.200
 */
@Restricted(NoExternalUse.class)
public class ResourceDomainFilter implements Filter {

    private static final Logger LOGGER = Logger.getLogger(ResourceDomainFilter.class.getName());

    private static final Set<String> ALLOWED_PATHS = new HashSet<>(Arrays.asList("/" + ResourceDomainRootAction.URL, "/favicon.ico", "/robots.txt"));
    public static final String ERROR_RESPONSE = "Jenkins serves only static files on this domain.";

    @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED)
    public static void init() throws ServletException {
        PluginServletFilter.addFilter(new ResourceDomainFilter());
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        if (servletRequest instanceof HttpServletRequest) {
            HttpServletRequest httpServletRequest = (HttpServletRequest)servletRequest;
            HttpServletResponse httpServletResponse = (HttpServletResponse)servletResponse;
            if (ResourceDomainConfiguration.isResourceRequest(httpServletRequest)) {
                String path = httpServletRequest.getPathInfo();
                if (!path.startsWith("/" + ResourceDomainRootAction.URL + "/") && !ALLOWED_PATHS.contains(path)) {
                    LOGGER.fine(() -> "Rejecting request to " + httpServletRequest.getRequestURL() + " from " + httpServletRequest.getRemoteAddr() + " on resource domain");
                    httpServletResponse.sendError(404, ERROR_RESPONSE);
                    return;
                }
                LOGGER.finer(() -> "Accepting request to " + httpServletRequest.getRequestURL() + " from " + httpServletRequest.getRemoteAddr() + " on resource domain");
            }
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void destroy() {

    }
}
