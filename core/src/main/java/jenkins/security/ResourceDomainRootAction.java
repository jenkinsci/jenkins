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

import static java.time.Instant.now;
import static java.time.Instant.ofEpochMilli;
import static java.time.temporal.ChronoUnit.MINUTES;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.UnprotectedRootAction;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Root action serving {@link DirectoryBrowserSupport} instances
 * on random URLs to support resource URLs (second domain).
 *
 * @see ResourceDomainConfiguration
 * @see ResourceDomainFilter
 *
 * @since 2.200
 */
@Extension
@Restricted(NoExternalUse.class)
public class ResourceDomainRootAction implements UnprotectedRootAction {

    private static final String RESOURCE_DOMAIN_ROOT_ACTION_ERROR = "jenkins.security.ResourceDomainRootAction.error";

    private static final Logger LOGGER = Logger.getLogger(ResourceDomainRootAction.class.getName());

    public static final String URL = "static-files";

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
        return URL;
    }

    public static ResourceDomainRootAction get() {
        return ExtensionList.lookupSingleton(ResourceDomainRootAction.class);
    }

    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException {
        if (ResourceDomainConfiguration.isResourceRequest(req)) {
            rsp.sendError(404, ResourceDomainFilter.ERROR_RESPONSE);
        } else {
            req.setAttribute(RESOURCE_DOMAIN_ROOT_ACTION_ERROR, true);
            rsp.sendError(404, "Cannot handle requests to this URL unless on Jenkins resource URL.");
        }
    }

    public Object getDynamic(String id, StaplerRequest req, StaplerResponse rsp) throws Exception {
        if (!ResourceDomainConfiguration.isResourceRequest(req)) {
            req.setAttribute(RESOURCE_DOMAIN_ROOT_ACTION_ERROR, true);
            rsp.sendError(404, "Cannot handle requests to this URL unless on Jenkins resource URL.");
            return null;
        }

        if (!ALLOW_AUTHENTICATED_USER && !ACL.isAnonymous2(Jenkins.getAuthentication2())) {
            rsp.sendError(400);
            return null;
        }

        Token token = Token.decode(id);
        if (token == null) {
            rsp.sendError(404, ResourceDomainFilter.ERROR_RESPONSE);
            return null;
        }

        String authenticationName = token.username;
        String browserUrl = token.path;


        if (token.timestamp.plus(VALID_FOR_MINUTES, MINUTES).isAfter(now()) && token.timestamp.isBefore(now())) {
            return new InternalResourceRequest(browserUrl, authenticationName);
        }

        // too old, so redirect to the real file first
        return new Redirection(browserUrl);
    }

    private static class Redirection {
        private final String url;

        private Redirection(String url) {
            this.url = url;
        }

        public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException {
            String restOfPath = req.getRestOfPath();

            String url = Jenkins.get().getRootUrl() + this.url + restOfPath;
            rsp.sendRedirect(302, url);
        }
    }

    public String getRedirectUrl(@NonNull Token token, @NonNull String restOfPath) {
        String resourceRootUrl = getResourceRootUrl();
        if (!restOfPath.startsWith("/")) {
            // Unsure whether this can happen -- just be safe here
            restOfPath = "/" + restOfPath;
        }
        return resourceRootUrl + getUrlName() + "/" + token.encode() + Arrays.stream(restOfPath.split("[/]")).map(Util::rawEncode).collect(Collectors.joining("/"));
    }

    private static String getResourceRootUrl() {
        return ResourceDomainConfiguration.get().getUrl();
    }

    /**
     * Called from {@link DirectoryBrowserSupport#generateResponse(StaplerRequest, StaplerResponse, Object)} to obtain
     * a token to use when rendering a response.
     *
     * @param dbs the {@link DirectoryBrowserSupport} instance requesting the token
     * @param req the current request
     * @return a token that can be used to redirect users to the {@link ResourceDomainRootAction}.
     */
    @CheckForNull
    public Token getToken(@NonNull DirectoryBrowserSupport dbs, @NonNull StaplerRequest req) {
        // This is the "restOfPath" of the DirectoryBrowserSupport, i.e. the directory/file/pattern "inside" the DBS.
        final String dbsFile = req.getOriginalRestOfPath();

        // Now get the 'restOfUrl' after the top-level ancestor (which is the Jenkins singleton).
        // In other words, this is the complete URL after Jenkins handled the top-level request.
        final String completeUrl = req.getAncestors().get(0).getRestOfUrl();

        // And finally, remove the 'restOfPath' suffix from the complete URL, as that's the path from Jenkins to the DBS.
        String dbsUrl = completeUrl.substring(0, completeUrl.length() - dbsFile.length());
        LOGGER.fine(() -> "Determined DBS URL: " + dbsUrl + " from restOfUrl: " + completeUrl + " and restOfPath: " + dbsFile);

        Authentication authentication = Jenkins.getAuthentication2();
        String authenticationName = authentication.equals(Jenkins.ANONYMOUS2) ? "" : authentication.getName();

        try {
            return new Token(dbsUrl, authenticationName, Instant.now());
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Failed to encode token for URL: " + dbsUrl + " user: " + authenticationName, ex);
        }
        return null;
    }

    /**
     * Implements the browsing support for a specific {@link DirectoryBrowserSupport} like permission check.
     */
    private static class InternalResourceRequest {
        private final String authenticationName;
        private final String browserUrl;

        InternalResourceRequest(@NonNull String browserUrl, @NonNull String authenticationName) {
            this.browserUrl = browserUrl;
            this.authenticationName = authenticationName;
        }

        public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException {
            String restOfPath = req.getRestOfPath();

            String requestUrlSuffix = this.browserUrl;

            LOGGER.fine(() -> "Performing a request as authentication: " + authenticationName + " and restOfUrl: " + requestUrlSuffix + " and restOfPath: " + restOfPath);

            Authentication auth = Jenkins.ANONYMOUS2;
            if (Util.fixEmpty(authenticationName) != null) {
                User user = User.getById(authenticationName, false);
                if (user != null) {
                    try {
                        auth = user.impersonate2();
                        LOGGER.fine(() -> "Successfully impersonated " + authenticationName);
                    } catch (UsernameNotFoundException ex) {
                        LOGGER.log(Level.FINE, "Failed to impersonate " + authenticationName, ex);
                        rsp.sendError(403, "No such user: " + authenticationName);
                        return;
                    }
                }
            }

            try (ACLContext ignored = ACL.as2(auth)) {
                try {
                    String path = requestUrlSuffix + Arrays.stream(restOfPath.split("[/]")).map(Util::rawEncode).collect(Collectors.joining("/"));
                    Stapler.getCurrent().invoke(req, rsp, Jenkins.get(), path);
                } catch (Exception ex) {
                    // cf. UnwrapSecurityExceptionFilter
                    Throwable cause = ex.getCause();
                    while (cause != null) {
                        if (cause instanceof AccessDeniedException) {
                            throw (AccessDeniedException) cause;
                        }
                        cause = cause.getCause();
                    }
                    throw ex;
                }
                /*
                While we could just redirect below to the real URL like we do for expired resource URLs, the question
                is whether we'd end up in a redirect loop if the exception is specific to this mode (and the "normal"
                URLs redirect to resource URLs). That seems even worse than an error here.
                 */
            } catch (AccessDeniedException ade) {
                /* This is expected to be fairly common, as permission issues are thrown up as exceptions */
                LOGGER.log(Level.FINE, "Failed permission check for resource URL access", ade);
                rsp.sendError(403, "Failed permission check: " + ade.getMessage());
            } catch (Exception e) {
                /*
                This should be fairly uncommon -- it's basically the 'rage' butler response. Notably, lack of access
                to a job (permissions/deleted/renamed/...) would not throw an exception, but just sends a 404 response.
                 */
                LOGGER.log(Level.FINE, "Something else failed for resource URL access", e);
                rsp.sendError(404);
            }
        }

        @Override
        public String toString() {
            return "[" + super.toString() + ", authentication=" + authenticationName + "; key=" + browserUrl + "]";
        }
    }

    public static class Token {
        private String path;
        private String username;
        private Instant timestamp;

        @VisibleForTesting
        Token(@NonNull String path, @Nullable String username, @NonNull Instant timestamp) {
            this.path = path;
            this.username = Util.fixNull(username);
            this.timestamp = timestamp;
        }

        private String encode() {
            String value = timestamp.toEpochMilli() + ":" + username.length() + ":" + username + ":" + path;
            byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
            byte[] macBytes = KEY.mac(valueBytes);
            byte[] result = new byte[macBytes.length + valueBytes.length];
            System.arraycopy(macBytes, 0, result, 0, macBytes.length);
            System.arraycopy(valueBytes, 0, result, macBytes.length, valueBytes.length);
            return Base64.getUrlEncoder().encodeToString(result);
        }

        private static Token decode(String value) {
            try {
                byte[] byteValue = Base64.getUrlDecoder().decode(value);
                byte[] mac = Arrays.copyOf(byteValue, 32);
                byte[] restBytes = Arrays.copyOfRange(byteValue, 32, byteValue.length);
                String rest = new String(restBytes, StandardCharsets.UTF_8);
                if (!KEY.checkMac(restBytes, mac)) {
                    throw new IllegalArgumentException("Failed mac check for " + rest);
                }

                String[] splits = rest.split("[:]", 3);
                String epoch = splits[0];
                int authenticationNameLength = Integer.parseInt(splits[1]);
                String authenticationNameAndBrowserUrl = splits[2];
                String authenticationName = authenticationNameAndBrowserUrl.substring(0, authenticationNameLength);
                String browserUrl = authenticationNameAndBrowserUrl.substring(authenticationNameLength + 1);
                return new Token(browserUrl, authenticationName, ofEpochMilli(Long.parseLong(epoch)));
            } catch (RuntimeException ex) {
                // Choose log level that hides people messing with the URLs
                LOGGER.log(Level.FINE, "Failure decoding", ex);
                return null;
            }
        }

    }

    private static HMACConfidentialKey KEY = new HMACConfidentialKey(ResourceDomainRootAction.class, "key");

    // Not @Restricted because the entire class is
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* not final for Groovy */ int VALID_FOR_MINUTES = SystemProperties.getInteger(ResourceDomainRootAction.class.getName() + ".validForMinutes", 30);

    /* Escape hatch for a security hardening preventing one of the known ways to elevate arbitrary file read to RCE */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* not final for Groovy */ boolean ALLOW_AUTHENTICATED_USER = SystemProperties.getBoolean(ResourceDomainRootAction.class.getName() + ".allowAuthenticatedUser", false);
}
