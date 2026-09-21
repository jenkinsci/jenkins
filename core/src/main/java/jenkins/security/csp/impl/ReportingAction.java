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
import hudson.model.InvisibleAction;
import hudson.model.UnprotectedRootAction;
import hudson.model.User;
import hudson.security.csrf.CrumbExclusion;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.csp.CspReceiver;
import jenkins.security.csp.ReportingContext;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;

/**
 * This action receives reports of Content-Security-Policy violations.
 * It needs to be an {@link hudson.model.UnprotectedRootAction} because these requests do not have cookies.
 * If we wanted to restrict submissions by unprivileged users, we'd not generate the Content-Security-Policy header
 * for them, or removed the report-uri / report-to directives.
 */
@Restricted(NoExternalUse.class)
@Extension
public class ReportingAction extends InvisibleAction implements UnprotectedRootAction {
    public static final String URL = "content-security-policy-reporting-endpoint";
    private static final Logger LOGGER = Logger.getLogger(ReportingAction.class.getName());

    // In limited testing, reports seem to be a few hundred bytes (mostly the actual policy), so this seems plenty.
    // See https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CSP#violation_reporting for an example.
    private static /* non-final for script console */ int MAX_REPORT_LENGTH = 20 * 1024;

    @Override
    public String getUrlName() {
        return URL;
    }

    @POST
    public HttpResponse doDynamic(StaplerRequest2 req) {
        final String requestRestOfPath = req.getRestOfPath();
        String restOfPath = requestRestOfPath.startsWith("/") ? requestRestOfPath.substring(1) : requestRestOfPath;

        try {
            final ReportingContext.DecodedContext context = ReportingContext.decodeContext(restOfPath);

            CspReceiver.ViewContext viewContext =
                    new CspReceiver.ViewContext(context.contextClassName(), context.restOfPath());
            final boolean[] maxReached = new boolean[1];
            try (InputStream is = req.getInputStream(); BoundedInputStream bis = BoundedInputStream.builder().setMaxCount(MAX_REPORT_LENGTH).setOnMaxCount((x, y) -> maxReached[0] = true).setInputStream(is).get()) {
                String report = IOUtils.toString(bis, req.getCharacterEncoding());
                if (maxReached[0]) {
                    LOGGER.log(Level.FINE, () -> "Report for " + viewContext + " exceeded max length of " + MAX_REPORT_LENGTH);
                    return HttpResponses.ok();
                }
                LOGGER.log(Level.FINEST, () -> "Report for " + viewContext + " length: " + report.length());
                LOGGER.log(Level.FINER, () -> viewContext + " " + report);
                JSONObject jsonObject;
                try {
                    jsonObject = JSONObject.fromObject(report);
                } catch (JSONException ex) {
                    LOGGER.log(Level.FINE, ex, () -> "Failed to parse JSON report for " + viewContext + ": " + report);
                    return HttpResponses.ok();
                }

                User user = context.userId() != null ? User.getById(context.userId(), false) : null;

                for (CspReceiver receiver :
                        ExtensionList.lookup(CspReceiver.class)) {
                    try {
                        receiver.report(viewContext, user == null ? null : context.userId(), jsonObject);
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, ex, () -> "Error reporting CSP for " + viewContext + " to " + receiver);
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.FINE, e, () -> "Failed to read request body for " + viewContext);
            }
            return HttpResponses.ok();
        } catch (RuntimeException ex) {
            LOGGER.log(Level.FINE, ex, () -> "Unexpected rest of path failed to decode: " + restOfPath);
            return HttpResponses.ok();
        }
    }

    @Extension
    public static class CrumbExclusionImpl extends CrumbExclusion {
        @Override
        public boolean process(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            String pathInfo = request.getPathInfo();
            if (pathInfo != null && pathInfo.startsWith("/" + URL + "/")) {
                chain.doFilter(request, response);
                return true;
            }
            return false;
        }
    }
}
