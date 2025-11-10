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

import hudson.Extension;
import hudson.ExtensionList;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import jenkins.security.ResourceDomainConfiguration;
import jenkins.util.HttpServletFilter;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
@Extension
public class CspFilter implements HttpServletFilter {
    @Override
    public boolean handle(HttpServletRequest req, HttpServletResponse rsp) throws IOException, ServletException {
        final CspDecorator cspDecorator = ExtensionList.lookupSingleton(CspDecorator.class);
        final String header = cspDecorator.getContentSecurityPolicyHeaderName();
        if (rsp.getHeader(header) == null && !ResourceDomainConfiguration.isResourceRequest(req)) {
            // The Filter/Decorator approach needs us to "set" headers rather than "add", so no additional endpoints are supported at the moment.
            rsp.setHeader("Reporting-Endpoints", cspDecorator.getReportingEndpointsHeaderValue(req));

            // This is the preliminary value outside Stapler request handling (and providing a context object)
            rsp.setHeader(header, cspDecorator.getContentSecurityPolicyHeaderValue(req));
        }
        return false;
    }
}
