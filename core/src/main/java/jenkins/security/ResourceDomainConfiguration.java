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

import static jenkins.security.ResourceDomainFilter.ERROR_RESPONSE;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.util.FormValidation;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.diagnostics.RootUrlNotSetMonitor;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.model.identity.InstanceIdentityProvider;
import jenkins.util.UrlHelper;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;

/**
 * Configure the resource root URL, an alternative root URL to serve resources from
 * to not need Content-Security-Policy headers, which mess with desired complex output.
 *
 * @see ResourceDomainFilter
 * @see ResourceDomainRootAction
 *
 * @since 2.200, unrestricted since 2.203
 */
@Extension(ordinal = JenkinsLocationConfiguration.ORDINAL - 1) // sort just below the regular location config
@Restricted(Beta.class)
@Symbol("resourceRoot")
public final class ResourceDomainConfiguration extends GlobalConfiguration {

    private static final Logger LOGGER = Logger.getLogger(ResourceDomainConfiguration.class.getName());

    private String url;

    @Restricted(NoExternalUse.class)
    public ResourceDomainConfiguration() {
        load();
    }

    @Restricted(NoExternalUse.class)
    @POST
    public FormValidation doCheckUrl(@QueryParameter("url") String resourceRootUrlString) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        return checkUrl(resourceRootUrlString, true);
    }

    private FormValidation checkUrl(String resourceRootUrlString, boolean allowOnlineIdentityCheck) {
        String jenkinsRootUrlString = JenkinsLocationConfiguration.get().getUrl();
        if (ExtensionList.lookupSingleton(RootUrlNotSetMonitor.class).isActivated() || jenkinsRootUrlString == null) {
            // This is needed to round-trip expired resource URLs through regular URLs to refresh them,
            // so while it's not required in the strictest sense, it is required.
            return FormValidation.warning(Messages.ResourceDomainConfiguration_NeedsRootURL());
        }

        resourceRootUrlString = Util.fixEmptyAndTrim(resourceRootUrlString);
        if (resourceRootUrlString == null) {
            return FormValidation.ok(Messages.ResourceDomainConfiguration_Empty());
        }

        if (!UrlHelper.isValidRootUrl(resourceRootUrlString)) {
            return FormValidation.error(Messages.ResourceDomainConfiguration_Invalid());
        }

        if (!resourceRootUrlString.endsWith("/")) {
            resourceRootUrlString += '/';
        }

        URL resourceRootUrl;
        try {
            resourceRootUrl = new URI(resourceRootUrlString).toURL();
        } catch (MalformedURLException | URISyntaxException ex) {
            return FormValidation.error(Messages.ResourceDomainConfiguration_Invalid());
        }

        String resourceRootUrlHost = resourceRootUrl.getHost();
        try {
            String jenkinsRootUrlHost = new URI(jenkinsRootUrlString).getHost();
            if (jenkinsRootUrlHost.equals(resourceRootUrlHost)) {
                // We do not allow the same host for Jenkins and resource root URLs even if there's some other difference.
                // This is a conservative choice and prohibits same host/different proto/different port/different path:
                // - Different path still counts as the same origin for same-origin policy
                // - Cookies are shared across ports, and non-Secure cookies get sent to HTTPS sites
                return FormValidation.error(Messages.ResourceDomainConfiguration_SameAsJenkinsRoot());
            }
        } catch (Exception ex) {
            LOGGER.log(Level.CONFIG, "Failed to create URL from the existing Jenkins URL", ex);
            return FormValidation.error(Messages.ResourceDomainConfiguration_InvalidRootURL(ex.getMessage()));
        }

        StaplerRequest2 currentRequest = Stapler.getCurrentRequest2();
        if (currentRequest != null) {
            String currentRequestHost = currentRequest.getServerName();

            if (currentRequestHost.equals(resourceRootUrlHost)) {
                return FormValidation.error(Messages.ResourceDomainConfiguration_SameAsCurrent());
            }
        }

        if (!allowOnlineIdentityCheck) {
            return FormValidation.ok();
        }

        // Send a request to /instance-identity/ at the resource root URL and check whether it is this Jenkins
        try {
            URLConnection urlConnection = new URI(resourceRootUrlString + "instance-identity/").toURL().openConnection();
            if (urlConnection instanceof HttpURLConnection httpURLConnection) {
                int responseCode = httpURLConnection.getResponseCode();

                if (responseCode == 200) {
                    String identityHeader = urlConnection.getHeaderField("X-Instance-Identity");
                    if (identityHeader == null) {
                        return FormValidation.warning(Messages.ResourceDomainConfiguration_NotJenkins());
                    }
                    // URL points to a Jenkins instance
                    RSAPublicKey publicKey = InstanceIdentityProvider.RSA.getPublicKey();
                    if (publicKey != null) {
                        String identity = Base64.getEncoder().encodeToString(publicKey.getEncoded());
                        if (identity.equals(identityHeader)) {
                            return FormValidation.ok(Messages.ResourceDomainConfiguration_ThisJenkins());
                        }
                        return FormValidation.warning(Messages.ResourceDomainConfiguration_OtherJenkins());
                    } // the current instance has no public key
                    return FormValidation.warning(Messages.ResourceDomainConfiguration_SomeJenkins());
                }
                // response is error
                String responseMessage = httpURLConnection.getResponseMessage();
                if (responseCode == 404) {
                    String responseBody = String.join("", IOUtils.readLines(httpURLConnection.getErrorStream(), StandardCharsets.UTF_8));
                    if (responseMessage.contains(ERROR_RESPONSE) || responseBody.contains(ERROR_RESPONSE)) {
                        return FormValidation.ok(Messages.ResourceDomainConfiguration_ResourceResponse());
                    }
                }
                return FormValidation.error(Messages.ResourceDomainConfiguration_FailedIdentityCheck(responseCode, responseMessage));
            }
            return FormValidation.error(Messages.ResourceDomainConfiguration_Invalid()); // unlikely to ever be hit
        } catch (MalformedURLException | URISyntaxException ex) {
            // Not expected to be hit
            LOGGER.log(Level.FINE, "MalformedURLException occurred during instance identity check for " + resourceRootUrlString, ex);
            return FormValidation.error(Messages.ResourceDomainConfiguration_Exception(ex.getMessage()));
        } catch (IOException ex) {
            LOGGER.log(Level.FINE, "IOException occurred during instance identity check for " + resourceRootUrlString, ex);
            return FormValidation.warning(Messages.ResourceDomainConfiguration_IOException(ex.getMessage()));
        }
    }

    @CheckForNull
    public String getUrl() {
        return url;
    }

    public void setUrl(@CheckForNull String url) {
        if (checkUrl(url, false).kind == FormValidation.Kind.OK) {
            // only accept valid configurations, both with and without URL, but allow for networking issues
            url = Util.fixEmpty(url);
            if (url != null && !url.endsWith("/")) {
                url += "/";
            }
            this.url = url;
            save();
        }
    }

    /**
     * Returns true if and only if this is a request to URLs under the resource root URL.
     *
     * For this to be the case, the requested host and port (from the Host HTTP request header) must match what is
     * configured for the resource root URL.
     *
     * @param req the request to check
     * @return whether the request is a resource URL request
     */
    @Restricted(NoExternalUse.class)
    public static boolean isResourceRequest(HttpServletRequest req) {
        if (!isResourceDomainConfigured()) {
            return false;
        }
        String resourceRootUrl = get().getUrl();
        try {
            URL url = new URL(resourceRootUrl);

            String resourceRootHost = url.getHost();
            if (!resourceRootHost.equalsIgnoreCase(req.getServerName())) {
                return false;
            }

            int resourceRootPort = url.getPort();
            if (resourceRootPort == -1) {
                resourceRootPort = url.getDefaultPort();
            }

            // let's hope this gives the default port if the Host header exists but doesn't specify a port
            int requestedPort = req.getServerPort();

            if (requestedPort != resourceRootPort) {
                return false;
            }
        } catch (MalformedURLException ex) {
            // the URL here cannot be so broken that we cannot call `new URL(String)` on it...
            return false;
        }
        return true;
    }

    /**
     * Returns true if and only if a domain has been configured to serve resource URLs from
     *
     * @return whether a domain has been configured
     */
    @Restricted(NoExternalUse.class)
    public static boolean isResourceDomainConfigured() {
        String resourceRootUrl = get().getUrl();
        if (resourceRootUrl == null || resourceRootUrl.isEmpty()) {
            return false;
        }

        // effectively not configured when the location configuration is empty
        return Util.nullify(JenkinsLocationConfiguration.get().getUrl()) != null;
    }

    public static ResourceDomainConfiguration get() {
        return ExtensionList.lookupSingleton(ResourceDomainConfiguration.class);
    }
}
