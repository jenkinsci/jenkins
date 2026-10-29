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
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
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

    public void doIndex(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        if (ResourceDomainConfiguration.isResourceRequest(req)) {
            rsp.sendError(404, ResourceDomainFilter.ERROR_RESPONSE);
        } else {
            req.setAttribute(RESOURCE_DOMAIN_ROOT_ACTION_ERROR, true);
            rsp.sendError(404, "Cannot handle requests to this URL unless on Jenkins resource URL.");
        }
    }

    public Object getDynamic(String id, StaplerRequest2 req, StaplerResponse2 rsp) throws Exception {
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

        public void doDynamic(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
            String restOfPath = req.getRestOfPath();

            // Ensure url is a safe relative path (defense in depth against open redirect).
            // The token is HMAC-signed so tampering is not expected, but be defensive here.
            String safeUrl = sanitizeRedirectPath(this.url);
            String safeRestOfPath = sanitizeRedirectPath(restOfPath);

            StringBuilder sb = new StringBuilder();
            sb.append(Jenkins.get().getRootUrl());

            if (!safeUrl.startsWith("/")) {
                sb.append('/');
            }
            sb.append(safeUrl);

            if (safeUrl.isEmpty() || safeUrl.charAt(safeUrl.length() - 1) != '/') {
                sb.append('/');
            }
            if (safeRestOfPath.startsWith("/")) {
                sb.append(safeRestOfPath.substring(1));
            } else {
                sb.append(safeRestOfPath);
            }

            rsp.sendRedirect(302, sb.toString());
        }
    }

    /**
     * Sanitize a path component used for redirects or internal dispatch.
     * Ensures the path is a relative path and does not start with // (which would
     * create a protocol-relative URL when prefixed with a scheme+host).
     * Blocks paths containing URL schemes (://) which could enable open redirects.
     */
    private static String sanitizeRedirectPath(String path) {
        if (path == null) {
            return "";
        }
        String sanitized = path;
        // Strip any URL scheme that could cause open redirects
        int schemeIdx = sanitized.indexOf("://");
        if (schemeIdx >= 0) {
            sanitized = sanitized.substring(schemeIdx + 3);
            // After stripping scheme, skip the host portion if present
            int nextSlash = sanitized.indexOf('/');
            if (nextSlash >= 0) {
                sanitized = sanitized.substring(nextSlash);
            } else {
                sanitized = "";
            }
        }
        // Prevent protocol-relative URLs (//evil.com/...)
        while (sanitized.startsWith("//")) {
            sanitized = sanitized.substring(1);
        }
        return sanitized;
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
     * Called from {@link DirectoryBrowserSupport#generateResponse(StaplerRequest2, StaplerResponse2, Object)} to obtain
     * a token to use when rendering a response.
     *
     * @param dbs the {@link DirectoryBrowserSupport} instance requesting the token
     * @param req the current request
     * @return a token that can be used to redirect users to the {@link ResourceDomainRootAction}.
     */
    @CheckForNull
    public Token getToken(@NonNull DirectoryBrowserSupport dbs, @NonNull StaplerRequest2 req) {
        // This is the "restOfPath" of the DirectoryBrowserSupport, i.e. the directory/file/pattern "inside" the DBS.
        final String dbsFile = req.getOriginalRestOfPath();

        // Now get the 'restOfUrl' after the top-level ancestor (which is the Jenkins singleton).
        // In other words, this is the complete URL after Jenkins handled the top-level request.
        final String completeUrl = req.getAncestors().getFirst().getRestOfUrl();

        // And finally, remove the 'restOfPath' suffix from the complete URL, as that's the path from Jenkins to the DBS.
        // Defensive boundary checks to prevent IndexOutOfBoundsException on unexpected paths.
        String dbsUrl;
        if (completeUrl == null || dbsFile == null || completeUrl.length() < dbsFile.length()) {
            LOGGER.log(Level.WARNING, "Cannot determine DBS URL: completeUrl='" + completeUrl + "', dbsFile='" + dbsFile + "'");
            return null;
        }
        dbsUrl = completeUrl.substring(0, completeUrl.length() - dbsFile.length());

        // Security: ensure dbsUrl is a safe relative path, not an absolute URL or protocol-relative URL
        // that could be abused for open redirect if the token HMAC were somehow broken.
        if (dbsUrl.contains("://") || dbsUrl.startsWith("//")) {
            LOGGER.log(Level.WARNING, "Refusing to create token with unsafe DBS URL: " + dbsUrl);
            return null;
        }

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

        public void doDynamic(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
            String restOfPath = req.getRestOfPath();

            // Sanitize browser URL from token -- defense in depth against open redirect
            // and path traversal (the token is HMAC-signed but validate anyway).
            String safeBrowserUrl = sanitizeRedirectPath(this.browserUrl);

            LOGGER.fine(() -> "Performing a request as authentication: " + authenticationName + " and restOfUrl: " + safeBrowserUrl + " and restOfPath: " + restOfPath);

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
                    // Construct a safe path for Stapler dispatch.
                    // Ensure safeBrowserUrl starts with "/" and handle trailing slash properly.
                    StringBuilder pathBuilder = new StringBuilder();
                    if (!safeBrowserUrl.startsWith("/")) {
                        pathBuilder.append('/');
                    }
                    pathBuilder.append(safeBrowserUrl);

                    // Normalize restOfPath (from URL) -- split, encode each segment, join
                    String encodedRestOfPath = Arrays.stream(restOfPath.split("[/]"))
                            .map(Util::rawEncode)
                            .collect(Collectors.joining("/"));

                    if (!encodedRestOfPath.isEmpty()) {
                        if (pathBuilder.length() > 0 && pathBuilder.charAt(pathBuilder.length() - 1) != '/') {
                            pathBuilder.append('/');
                        }
                        if (encodedRestOfPath.startsWith("/")) {
                            pathBuilder.append(encodedRestOfPath.substring(1));
                        } else {
                            pathBuilder.append(encodedRestOfPath);
                        }
                    }

                    String path = pathBuilder.toString();
                    // Prevent path traversal: ensure path does not contain ".." segments
                    if (Arrays.stream(path.split("[/]")).anyMatch(".."::equals)) {
                        LOGGER.log(Level.FINE, "Path traversal attempt blocked: " + path);
                        rsp.sendError(400);
                        return;
                    }

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

        private Object readResolve() {
            LOGGER.log(Level.WARNING, "Blocked deserialization of InternalResourceRequest for security reasons", new Exception("stack trace"));
            throw new SecurityException("Blocked deserialization of InternalResourceRequest for security reasons");
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
