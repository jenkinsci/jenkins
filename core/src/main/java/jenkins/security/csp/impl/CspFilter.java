/*
 * The MIT License
 *
 * Copyright (c) 2025, CloudBees, Inc.
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

package jenkins.security.csp.impl;

import hudson.ExtensionList;
import hudson.Functions;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.ResourceDomainConfiguration;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
public class CspFilter implements Filter {

    private static final Logger LOGGER = Logger.getLogger(CspFilter.class.getName());

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest req) || !(response instanceof HttpServletResponse rsp)) {
            chain.doFilter(request, response);
            return;
        }

        if (!Functions.isExtensionsAvailable()) {
            // TODO Implement CSP protection while extensions are not available
            LOGGER.log(Level.FINER, "Extensions are not available, so skipping CSP enforcement for: " + req.getRequestURI());
            chain.doFilter(request, response);
            return;
        }

        CspDecorator cspDecorator = ExtensionList.lookupSingleton(CspDecorator.class);
        final String headerName = cspDecorator.getContentSecurityPolicyHeaderName();
        final boolean headerShouldBeSet = headerName != null;

        // This is the preliminary value outside Stapler request handling (and providing a context object)
        final String headerValue = cspDecorator.getContentSecurityPolicyHeaderValue(req);

        final boolean isResourceRequest = ResourceDomainConfiguration.isResourceRequest(req);

        if (headerShouldBeSet && !isResourceRequest) {
            // The Filter/Decorator approach needs us to "set" headers rather than "add", so no additional endpoints are supported at the moment.
            final String reportingEndpoints = cspDecorator.getReportingEndpointsHeaderValue(req);
            if (reportingEndpoints != null) {
                rsp.setHeader("Reporting-Endpoints", reportingEndpoints);
            }

            rsp.setHeader(headerName, headerValue);
        }
        try {
            chain.doFilter(req, rsp);
        } finally {
            if (headerShouldBeSet) {
                try {
                    final String actualHeader = rsp.getHeader(headerName);
                    if (!isResourceRequest && hasUnexpectedDifference(headerValue, actualHeader)) {
                        LOGGER.log(Level.FINE, "CSP header has unexpected differences: Expected '" + headerValue + "' but got '" + actualHeader + "'");
                    }
                } catch (RuntimeException e) {
                    // Be defensive just in case
                    LOGGER.log(Level.FINER, "Error checking CSP header after request processing", e);
                }
            }
        }
    }

    private static boolean hasUnexpectedDifference(String headerByFilter, String actualHeader) {
        if (actualHeader == null) {
            return true;
        }
        String expectedPrefix = headerByFilter.substring(0, headerByFilter.indexOf(" report-uri ")); // cf. CspDecorator
        if (!actualHeader.contains(" report-uri ")) {
            return true;
        }
        String actualPrefix = actualHeader.substring(0, actualHeader.indexOf(" report-uri "));
        return !expectedPrefix.equals(actualPrefix);
    }
}
