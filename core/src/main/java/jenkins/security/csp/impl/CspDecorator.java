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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.PageDecorator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.security.csp.CspBuilder;
import jenkins.security.csp.CspHeader;
import jenkins.security.csp.CspHeaderDecider;
import jenkins.security.csp.ReportingContext;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

@Restricted(NoExternalUse.class)
@Extension
public class CspDecorator extends PageDecorator {

    private static final String REPORTING_ENDPOINT_NAME = "content-security-policy";
    private static final Logger LOGGER = Logger.getLogger(CspDecorator.class.getName());

    public String getContentSecurityPolicyHeaderValue(HttpServletRequest req) {
        String cspDirectives = new CspBuilder().withDefaultContributions().build();

        final String reportingEndpoint = getReportingEndpoint(req);
        if (reportingEndpoint != null) {
            cspDirectives += " report-to " + REPORTING_ENDPOINT_NAME + "; report-uri " + reportingEndpoint;
        }
        return cspDirectives;
    }

    // Also used in httpHeaders.jelly
    @CheckForNull
    public String getReportingEndpointsHeaderValue(HttpServletRequest req) {
        final String reportingEndpoint = getReportingEndpoint(req);
        if (reportingEndpoint == null) {
            return null;
        }
        return REPORTING_ENDPOINT_NAME + ": " + reportingEndpoint;
    }

    @CheckForNull
    /* package */ static String getReportingEndpoint(HttpServletRequest req) {
        Class<?> modelObjectClass = null;
        String restOfPath = StringUtils.removeStart(req.getRequestURI(), req.getContextPath());
        final StaplerRequest2 staplerRequest2 = Stapler.getCurrentRequest2();
        if (staplerRequest2 != null) {
            final List<Ancestor> ancestors = staplerRequest2.getAncestors();
            if (!ancestors.isEmpty()) {
                final Ancestor nearest = ancestors.get(ancestors.size() - 1);
                restOfPath = nearest.getRestOfUrl();
                modelObjectClass = nearest.getObject().getClass();
            }
        }

        String rootUrl;
        try {
            rootUrl = Jenkins.get().getRootUrlFromRequest();
        } catch (IllegalStateException e) {
            LOGGER.log(Level.FINEST, "Cannot get root URL from request", e);
            // Outside a Stapler request, probably in the CspFilter, so get configured root URL
            try {
                rootUrl = Jenkins.get().getRootUrl();
            } catch (IllegalStateException ise) {
                LOGGER.log(Level.FINEST, "Cannot get root URL from configuration", ise);
                return null;
            }
        }
        if (rootUrl == null) {
            return null;
        }
        return rootUrl + ReportingAction.URL + "/" + ReportingContext.encodeContext(modelObjectClass, Jenkins.getAuthentication2(), restOfPath);
    }

    /**
     * Determines the name of the HTTP header to set.
     *
     * @return the name of the HTTP header to set.
     */
    public String getContentSecurityPolicyHeaderName() {
        final Optional<CspHeaderDecider> decider = CspHeaderDecider.getCurrentDecider();
        if (decider.isPresent()) {
            final CspHeaderDecider presentDecider = decider.get();
            LOGGER.log(Level.FINEST, "Choosing header from decider " + presentDecider.getClass().getName());
            final Optional<CspHeader> decision = presentDecider.decide();
            if (decision.isPresent()) {
                return decision.get().getHeaderName();
            }
            LOGGER.log(Level.FINE, "Decider changed its mind after selection: " + presentDecider.getClass().getName());
        }

        LOGGER.log(Level.WARNING, "Failed to find a CspHeaderDecider, falling back to default");
        return CspHeader.ContentSecurityPolicyReportOnly.getHeaderName();
    }
}
