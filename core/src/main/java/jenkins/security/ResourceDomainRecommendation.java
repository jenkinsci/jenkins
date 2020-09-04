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

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.model.DirectoryBrowserSupport;
import hudson.security.Permission;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.IOException;

/**
 * Recommend use of {@link ResourceDomainConfiguration} to users with the system property
 * {@code hudson.model.DirectoryBrowserSupport.CSP} set to override
 * {@link DirectoryBrowserSupport#DEFAULT_CSP_VALUE}.
 *
 * @see ResourceDomainConfiguration
 *
 * @since 2.200
 */
@Extension
@Restricted(NoExternalUse.class)
public class ResourceDomainRecommendation extends AdministrativeMonitor {

    @Override
    public String getDisplayName() {
        return Messages.ResourceDomainConfiguration_DisplayName();
    }

    @Override
    public boolean isActivated() {
        boolean isResourceRootUrlSet = ResourceDomainConfiguration.isResourceDomainConfigured();
        boolean isOverriddenCSP = SystemProperties.getString(DirectoryBrowserSupport.CSP_PROPERTY_NAME) != null;
        return isOverriddenCSP && !isResourceRootUrlSet;
    }

    @RequirePOST
    public HttpResponse doAct(@QueryParameter String redirect, @QueryParameter String dismiss) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        if (dismiss != null) {
            disable(true);
            return HttpResponses.redirectViaContextPath("manage");
        }
        if (redirect != null) {
            return HttpResponses.redirectViaContextPath("configure");
        }
        return HttpResponses.forwardToPreviousPage();
    }

    @Override
    public Permission getRequiredPermission() {
        return Jenkins.SYSTEM_READ;
    }
}
