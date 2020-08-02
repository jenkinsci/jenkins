/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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

import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.model.RootAction;
import hudson.model.UnprotectedRootAction;
import hudson.security.csrf.CrumbExclusion;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import net.sf.json.JsonConfig;
import net.sf.json.util.JavaIdentifierTransformer;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Extension
@Symbol("CSP")
public class CspRootAction implements RootAction {

    private boolean reporterOnly = true;
    private boolean withApprovedHashes = true;
    private String cspRules = "default-src 'self'; child-src 'none' ; style-src 'self' 'unsafe-inline';"; // style-src is just there to simplify a bit the PoC
    private String desiredPayload = "payload<script>console.warn(123)</script>injected";

    // contain only sha256-xxxxx stuff
    private Set<String> approvedHashes = new HashSet<>();
    Set<SubmitReport> reports = new HashSet<>();

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return "csp-poc";
    }

    public boolean isReporterOnly() {
        return reporterOnly;
    }

    @VisibleForTesting void setReporterOnly(boolean reporterOnly) {
        this.reporterOnly = reporterOnly;
    }

    public boolean isWithApprovedHashes() {
        return withApprovedHashes;
    }

    public String getCspRules() {
        return cspRules;
    }

    public String getFullCspRules() {
        if (withApprovedHashes && !approvedHashes.isEmpty()) {
            // self = js file loaded from the application
            String formattedApprovedHashes = approvedHashes.stream()
                    .map(s -> "'" + s + "'")
                    .sorted()
                    .collect(Collectors.joining(" "));
            return cspRules + "; script-src 'self' " + formattedApprovedHashes;
        } else {
            return cspRules;
        }
    }

    public String getFullCspRulesWithReportUrl() {
        return getFullCspRules() + "; report-uri " + getAbsoluteUrlToReport();
    }

    public String getDesiredPayload() {
        return this.desiredPayload;
    }

    @VisibleForTesting void setDesiredPayload(String desiredPayload) {
        this.desiredPayload = desiredPayload;
    }

    public String getApprovedHashes() {
        return String.join("\n", this.approvedHashes);
    }

    public String getAbsoluteUrlToReport() {
        return Jenkins.get().getRootUrl() + "csp-poc-report/submitReport/test";
    }

    @RequirePOST
    public void doSubmitPayload(StaplerRequest request, StaplerResponse response) throws IOException, ServletException {
        SubmitPayload submitPayload = (SubmitPayload) request.getSubmittedForm().toBean(SubmitPayload.class);
        this.desiredPayload = submitPayload.desiredPayload;
        this.reporterOnly = submitPayload.reporterOnly;
        this.cspRules = submitPayload.cspRules;

        String rawApprovedHashes = submitPayload.approvedHashes;
        this.approvedHashes = Arrays.stream(rawApprovedHashes.split("\n"))
                .map(s -> s.trim().replaceAll("'", ""))
                .filter(s -> !s.startsWith("#") && !s.isEmpty())
                .collect(Collectors.toSet());

        response.sendRedirect2("");
    }

    public static class SubmitPayload {
        public String desiredPayload;
        public String cspRules;
        public boolean reporterOnly;
        public String approvedHashes;
    }

    @Extension
    public static class CspReportRootActionCrumbExclusion extends CrumbExclusion {
        @Override
        public boolean process(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
            String pathInfo = request.getPathInfo();
            if (pathInfo.startsWith("/csp-poc-report")) {
                chain.doFilter(request, response);
                return true;
            }
            return false;
        }
    }

    @Extension
    @Symbol("CSP-report")
    public static class CspReportRootAction implements UnprotectedRootAction {
        @RequirePOST
        public HttpResponse doSubmitReport(StaplerRequest request) throws IOException {
            JSONObject o = JSONObject.fromObject(IOUtils.toString(request.getReader()));

            JsonConfig jsonConfig = new JsonConfig();
            jsonConfig.setRootClass(SubmitReportWrapper.class);
            jsonConfig.setJavaIdentifierTransformer(JavaIdentifierTransformer.CAMEL_CASE);

            SubmitReportWrapper wrapper = (SubmitReportWrapper) JSONObject.toBean(o, jsonConfig);
            SubmitReport report = wrapper.cspReport;

            CspRootAction cspRootAction = Jenkins.get().getExtensionList(RootAction.class).get(CspRootAction.class);
            cspRootAction.reports.add(report);

            return HttpResponses.ok();
        }

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return "csp-poc-report";
        }
    }

    public static class SubmitReportWrapper {
        public SubmitReport cspReport;
    }

    public static class SubmitReport {
        public String blockedUri;
        public String disposition;
        public String documentUri;
        public String effectiveDirective;
        public String originalPolicy;
        public String referrer;
        public String scriptSample;
        public String statusCode;
        public String violatedDirective;

        // specific to some types of report
        public String sourceFile;
        public Integer lineNumber;
        public Integer columnNumber;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SubmitReport that = (SubmitReport) o;
            return Objects.equals(blockedUri, that.blockedUri) &&
                    Objects.equals(disposition, that.disposition) &&
                    Objects.equals(documentUri, that.documentUri) &&
                    Objects.equals(effectiveDirective, that.effectiveDirective) &&
                    Objects.equals(originalPolicy, that.originalPolicy) &&
                    Objects.equals(referrer, that.referrer) &&
                    Objects.equals(scriptSample, that.scriptSample) &&
                    Objects.equals(statusCode, that.statusCode) &&
                    Objects.equals(violatedDirective, that.violatedDirective) &&
                    Objects.equals(sourceFile, that.sourceFile) &&
                    Objects.equals(lineNumber, that.lineNumber) &&
                    Objects.equals(columnNumber, that.columnNumber);
        }

        @Override
        public int hashCode() {
            return Objects.hash(blockedUri, disposition, documentUri, effectiveDirective, originalPolicy, referrer, scriptSample, statusCode, violatedDirective, sourceFile, lineNumber, columnNumber);
        }
    }

}
