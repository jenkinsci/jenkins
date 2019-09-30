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
import hudson.Util;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.UnprotectedRootAction;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AccessControlled;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.Authentication;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.*;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Root action serving {@link DirectoryBrowserSupport} instances
 * on random URLs to support resource URLs (second domain).
 *
 * @see ResourceDomainConfiguration
 * @see ResourceDomainFilter
 *
 * @since TODO
 */
@Extension
@Restricted(NoExternalUse.class)
public class ResourceDomainRootAction implements UnprotectedRootAction {

    private static final Logger LOGGER = Logger.getLogger(ResourceDomainRootAction.class.getName());

    @CheckForNull
    @Override
    public String getIconFileName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return "static-files";
    }

    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException {
        if (!ResourceDomainConfiguration.isResourceRequest(req)) {
            rsp.sendError(404, "Cannot handle requests to this URL unless on Jenkins resource URL.");
        } else {
            rsp.sendError(404, "Jenkins serves only static files on this domain.");
        }
    }

    public Object getDynamic(String id, StaplerRequest req, StaplerResponse rsp) throws Exception {
        if (!ResourceDomainConfiguration.isResourceRequest(req)) {
            rsp.sendError(404, "Cannot handle requests to this URL unless on Jenkins resource URL.");
            return null;
        }

        String metadata = decode(id);
        if (metadata == null) {
            rsp.sendError(404, "Jenkins serves only static files on this domain.");
            return null;
        }

        String[] splits = metadata.split(":", 3);
        String authenticationName = Util.fixEmpty(splits[0]);
        String epoch = splits[1];
        String browserUrl = splits[2];

        long creationDate = Long.parseLong(epoch);
        long age = new Date().getTime() - creationDate;

        if (age >= 0 && age < TimeUnit.MINUTES.toMillis(VALID_FOR_MINUTES)) {
            return new InternalResourceRequest(browserUrl, authenticationName);
        }

        // too old, so redirect to the real file first
        return new Redirection(browserUrl);
    }

    private static class Redirection {
        private final String url;

        public Redirection(String url) {
            this.url = url;
        }

        public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException {
            String restOfPath = req.getRestOfPath();

            String url = Jenkins.get().getRootUrl() + this.url + restOfPath;
            rsp.sendRedirect(302, url);
        }
    }

    public String getRedirectUrl(String key, String restOfPath) {
        String resourceRootUrl = getResourceRootUrl();
        if (!resourceRootUrl.endsWith("/")) {
            resourceRootUrl += "/";
        }
        if (!restOfPath.startsWith("/")) {
            // Unsure whether this can happen -- just be safe here
            restOfPath = "/" + restOfPath;
        }
        return resourceRootUrl + getUrlName() + "/" + key + restOfPath;
    }

    private static String getResourceRootUrl() {
        return ResourceDomainConfiguration.get().getResourceRootUrl();
    }

    public String register(DirectoryBrowserSupport dbs, StaplerRequest req) {
        String dbsFile = req.getRestOfPath();

        String completeUrl = req.getAncestors().get(0).getRestOfUrl();
        completeUrl = completeUrl.substring(0, completeUrl.length() - dbsFile.length());

        Authentication authentication = Jenkins.getAuthentication();
        String authenticationName = authentication == Jenkins.ANONYMOUS ? "" : authentication.getName();

        Date date = new Date();

        String value = authenticationName + ":" + date.getTime() + ":" + completeUrl;
        try {
            return encode(value);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to encode " + value, ex);
        }
        return null;
    }

    /**
     * Implements the browsing support for a specific {@link DirectoryBrowserSupport} like permission check.
     */
    private static class InternalResourceRequest {
        private final String authenticationName;
        private final String browserUrl;

        InternalResourceRequest(@Nonnull String browserUrl, String authenticationName) {
            this.browserUrl = browserUrl;
            this.authenticationName = authenticationName;
        }

        public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException {
            String restOfPath = req.getRestOfPath();

            if (restOfPath.isEmpty()) {
                // Do not go through resource URLs for empty rest-of-path
                String url = Jenkins.get().getRootUrl() + browserUrl;
                rsp.sendRedirect(302, url);
                return;
            }

            AccessControlled requestRoot = Jenkins.get();
            String requestUrlSuffix = this.browserUrl;

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Performing a request as authentication: " + authenticationName + " to object: " + requestRoot + " and restOfUrl: " + requestUrlSuffix + " and restOfPath: " + restOfPath);
            }

            Authentication auth = Jenkins.ANONYMOUS;
            if (authenticationName != null) {
                User user = User.getById(authenticationName, false);
                if (user != null) {
                    auth = user.impersonate();
                }
            }

            try (ACLContext ignored = ACL.as(auth)) {
                Stapler.getCurrent().invoke(req, rsp, requestRoot, requestUrlSuffix + restOfPath);
                /*
                TODO I am unsure what to do when this throws an exception.
                Right now (below) we return 403/404 error pages. A more user-friendly approach is probably to
                just redirect like we do for expired resource URLs, but the question is whether we'd end up in a
                redirect loop if the exception is specific to this mode (and the "normal" URLs redirect to resource
                URLs). That seems even worse.
                 */
            } catch (AccessDeniedException ade) {
                LOGGER.log(Level.INFO, "Failed permission check for resource URL access", ade);
                rsp.sendError(403, "Failed permission check: " + ade.getMessage());
            } catch (Exception e) {
                LOGGER.log(Level.INFO, "Something else failed for resource URL access", e);
                rsp.sendError(404, "Failed: " + e.getMessage());
            }
        }

        @Override
        public String toString() {
            return "[" + super.toString() + ", authentication=" + authenticationName + "; key=" + browserUrl + "]";
        }
    }

    private String encode(String value) {
        String mac = KEY.mac(value);
        return mac + Util.toHexString((value.getBytes(StandardCharsets.UTF_8)));
    }

    private String decode(String value) {
        try {
            String mac = value.substring(0, 64);
            String rest = new String(Util.fromHexString(value.substring(64)), StandardCharsets.UTF_8);
            if (!KEY.checkMac(rest, mac)) {
                throw new IllegalArgumentException("Failed mac check for " + rest);
            }
            return rest;
        } catch (Exception ex) {
            // Choose log level that hides people messing with the URLs
            LOGGER.log(Level.FINE, "Failure decoding", ex);
            return null;
        }
    }

    private static HMACConfidentialKey KEY = new HMACConfidentialKey(ResourceDomainRootAction.class, "key");

    @Restricted(NoExternalUse.class)
    public static /* not final for Groovy */ int VALID_FOR_MINUTES = SystemProperties.getInteger(ResourceDomainRootAction.class.getName() + ".validForMinutes", 30);
}
