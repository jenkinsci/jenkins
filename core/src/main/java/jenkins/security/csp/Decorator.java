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

package jenkins.security.csp;

import hudson.Extension;
import hudson.model.PageDecorator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

@Restricted(NoExternalUse.class)
@Extension
public class Decorator extends PageDecorator {

    private static final String REPORTING_ENDPOINT_NAME = "content-security-policy";

    public String getContentSecurityPolicyHeaderValue(HttpServletRequest req) {
        String cspDirectives = new CspBuilder().withDefaultContributions().build();
        cspDirectives += " report-to " + REPORTING_ENDPOINT_NAME + "; report-uri " + getReportingEndpoint(req);
        return cspDirectives;
    }

    public String getReportingEndpointsHeaderValue(HttpServletRequest req) {
        return REPORTING_ENDPOINT_NAME + ": " + getReportingEndpoint(req);
    }

    /* package */ static String getReportingEndpoint(HttpServletRequest req) {
        String modelObjectClass = "";
        String restOfPath = StringUtils.removeStart(req.getRequestURI(), req.getContextPath());
        final StaplerRequest2 staplerRequest2 = Stapler.getCurrentRequest2();
        if (staplerRequest2 != null) {
            final List<Ancestor> ancestors = staplerRequest2.getAncestors();
            if (!ancestors.isEmpty()) {
                final Ancestor nearest = ancestors.get(ancestors.size() - 1);
                restOfPath = nearest.getRestOfUrl();
                modelObjectClass = nearest.getObject().getClass().getName();
            }
        }
        return Jenkins.get().getRootUrl() + ReportingAction.URL + "/" + ReportingContext.encodeContext(modelObjectClass, Jenkins.getAuthentication2(), restOfPath);
    }

    /**
     * Determines the name of the HTTP header to set.
     *
     * @return the name of the HTTP header to set.
     */
    public String getContentSecurityPolicyHeaderName() {
        // Honor system property override
        final String systemProperty = SystemProperties.getString(Decorator.class.getName() + ".headerName");
        if (systemProperty != null && Arrays.stream(CspHeader.values()).anyMatch(header -> StringUtils.equals(systemProperty, header.getHeaderName()))) {
            return systemProperty;
        }

        return CspHeader.ContentSecurityPolicy.getHeaderName();
    }
}
