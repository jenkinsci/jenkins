/*
 * The MIT License
 *
 * Copyright (c) 2023, CloudBees, Inc.
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

package jenkins.telemetry.impl;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.TcpAgentListener;
import hudson.security.csrf.CrumbIssuer;
import java.time.LocalDate;
import jenkins.model.Jenkins;
import jenkins.security.apitoken.ApiTokenPropertyConfiguration;
import jenkins.telemetry.Telemetry;
import net.sf.json.JSONObject;

@Extension
public class SecurityConfiguration extends Telemetry {
    @NonNull
    @Override
    public String getDisplayName() {
        return "Basic information about security-related settings";
    }

    @NonNull
    @Override
    public LocalDate getStart() {
        return LocalDate.of(2023, 8, 1);
    }

    @NonNull
    @Override
    public LocalDate getEnd() {
        return LocalDate.of(2023, 12, 1);
    }

    @Override
    public JSONObject createContent() {
        final Jenkins j = Jenkins.get();
        final JSONObject o = new JSONObject();
        o.put("components", buildComponentInformation());

        o.put("authorizationStrategy", j.getAuthorizationStrategy().getClass().getName());
        o.put("securityRealm", j.getSecurityRealm().getClass().getName());
        final CrumbIssuer crumbIssuer = j.getCrumbIssuer();
        o.put("crumbIssuer", crumbIssuer == null ? null : crumbIssuer.getClass().getName());
        o.put("markupFormatter", j.getMarkupFormatter().getClass().getName());
        final TcpAgentListener tcpAgentListener = j.getTcpAgentListener();
        o.put("inboundAgentListener", tcpAgentListener == null ? null : tcpAgentListener.configuredPort != -1);

        final ApiTokenPropertyConfiguration apiTokenPropertyConfiguration = ExtensionList.lookupSingleton(ApiTokenPropertyConfiguration.class);
        o.put("apiTokenCreationOfLegacyTokenEnabled", apiTokenPropertyConfiguration.isCreationOfLegacyTokenEnabled());
        o.put("apiTokenTokenGenerationOnCreationEnabled", apiTokenPropertyConfiguration.isTokenGenerationOnCreationEnabled());
        o.put("apiTokenUsageStatisticsEnabled", apiTokenPropertyConfiguration.isUsageStatisticsEnabled());

        return o;
    }
}
