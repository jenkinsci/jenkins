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
import hudson.ExtensionList;
import hudson.Util;
import hudson.util.FormValidation;
import jenkins.diagnostics.RootUrlNotSetMonitor;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.util.UrlHelper;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Configure the resource root URL, an alternative root URL to serve resources from
 * to not need Content-Security-Policy headers, which mess with desired complex output.
 *
 * @see ResourceDomainFilter
 * @see ResourceDomainRootAction
 *
 * @since TODO
 */
@Extension(ordinal = JenkinsLocationConfiguration.ORDINAL-1) // sort just below the regular location config
@Restricted(NoExternalUse.class)
@Symbol("resourceDomain")
public class ResourceDomainConfiguration extends GlobalConfiguration {

    private String resourceRootUrl;

    public ResourceDomainConfiguration() {
        load();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        save();
        return true;
    }

    public FormValidation doCheckResourceRootUrl(@QueryParameter("resourceRootUrl") String resourceRootUrlString) {
        /*
        TODO Better handle difference between root URLs, host names, and origins.
         */
        if (ExtensionList.lookupSingleton(RootUrlNotSetMonitor.class).isActivated()) {
            // This is needed to round-trip expired resource URLs through regular URLs to refresh them,
            // so while it's not required in the strictest sense, it is required.
            return FormValidation.warning(Messages.ResourceDomainConfiguration_NeedsRootURL());
        }

        resourceRootUrlString = Util.fixEmptyAndTrim(resourceRootUrlString);
        if (resourceRootUrlString == null) {
            return FormValidation.ok(Messages.ResourceDomainConfiguration_Empty());
        }

        if (!UrlHelper.isValidRootUrl(resourceRootUrlString)) {
            return FormValidation.warning(Messages.ResourceDomainConfiguration_Invalid());
        }

        URL resourceRootUrl;
        try {
            resourceRootUrl = new URL(resourceRootUrlString);
        } catch (MalformedURLException ex) {
            return FormValidation.warning(Messages.ResourceDomainConfiguration_Invalid());
        }

        String resourceRootUrlHost = resourceRootUrl.getHost();
        try {
            String jenkinsRootUrlHost = new URL(JenkinsLocationConfiguration.get().getUrl()).getHost();
            if (jenkinsRootUrlHost.equals(resourceRootUrlHost)) {
                return FormValidation.error(Messages.ResourceDomainConfiguration_SameAsJenkinsRoot());
            }
        } catch (Exception ex) {
            // TODO logging
            // TODO more appropriate message
            return FormValidation.warning(Messages.ResourceDomainConfiguration_NeedsRootURL());
        }

        StaplerRequest currentRequest = Stapler.getCurrentRequest();
        if (currentRequest != null) {
            String currentRequestHost = currentRequest.getHeader("Host");

            if (currentRequestHost.equals(resourceRootUrlHost)) {
                return FormValidation.warning(Messages.ResourceDomainConfiguration_SameAsCurrent());
            }
        }

        // TODO perform more elaborate permission checks to prevent users from setting a subdomain?
        return FormValidation.ok();
    }

    public String getResourceRootUrl() {
        return resourceRootUrl;
    }

    public void setResourceRootUrl(String resourceRootUrl) {
        if (doCheckResourceRootUrl(resourceRootUrl).kind == FormValidation.Kind.OK) {
            // only accept valid configurations, both with and without URL
            this.resourceRootUrl = Util.fixEmpty(resourceRootUrl);
        }
    }

    /**
     * Returns true if and only if this is a request to URLs under the resource root URL.
     *
     * @param req the request to check
     * @return whether the request is a resource URL request
     */
    public static boolean isResourceRequest(HttpServletRequest req) {
        if (!isResourceDomainConfigured()) {
            return false;
        }
        String resourceRootUrl = get().getResourceRootUrl();
        try {
            URL url = new URL(resourceRootUrl);
            String resourceRootHost = url.getHost();
            if (url.getPort() != -1) {
                resourceRootHost += ":" + url.getPort();
            }
            // TODO this looks brittle, figure out a better way
            return resourceRootHost.equals(req.getHeader("Host"));
        } catch (MalformedURLException ex) {
            // the URL here cannot be so broken that we cannot call `new URL(String)` on it...
            return false;
        }
    }

    /**
     * Returns true if and only if a domain has been configured to serve resource URLs from
     *
     * @return whether a domain has been configured
     */
    public static boolean isResourceDomainConfigured() {
        String resourceRootUrl = get().getResourceRootUrl();
        return resourceRootUrl != null && !resourceRootUrl.isEmpty();
    }

    public static ResourceDomainConfiguration get() {
        return ExtensionList.lookupSingleton(ResourceDomainConfiguration.class);
    }
}
