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

package jenkins.security.csp;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.ExtensionList;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * This is a general extension for use by implementations of {@link hudson.tasks.UserAvatarResolver}
 * and {@code AvatarMetadataAction} from {@code scm-api} plugin, or other "avatar-like" use cases.
 * It simplifies allowlisting safe sources of avatars by offering simple APIs that take a complete URL.
 */
@Restricted(Beta.class)
@Extension
public class AvatarContributor implements Contributor {
    private static final Logger LOGGER = Logger.getLogger(AvatarContributor.class.getName());

   private final Set<String> allowedSources = ConcurrentHashMap.newKeySet();

    @Override
    public void apply(CspBuilder cspBuilder) {
        allowedSources.forEach(source -> cspBuilder.add("img-src", source));
    }

    /**
     * Request addition of the complete URL to the allowed set of avatar image sources.
     * <p>
     *     Unlike the previous implementation that extracted only the domain, this now
     *     allowlists the exact URL, providing more granular security control.
     * </p>
     * <p>
     *     <strong>Important:</strong> Only implementations restricting specification of avatar URLs to at least somewhat
     *     privileged users should invoke this method, for example users with at least {@link hudson.model.Item#CONFIGURE}
     *     permission. Note that this guidance may change over time and require implementation changes.
     * </p>
     * <p>
     *     <strong>Note:</strong> Due to CSP header length limitations (approximately 8KB), administrators should
     *     monitor the total number of allowlisted URLs. Issue #23887 provides mechanisms to handle extremely long
     *     CSP headers.
     * </p>
     *
     * @param url The complete avatar image URL that should be allowlisted
     */
    public static void allow(@CheckForNull String url) {
        String normalizedUrl = normalizeUrl(url);

        if (normalizedUrl == null) {
            LOGGER.log(Level.FINE, "Skipping invalid or null URL: " + url);
            return;
        }

        if (ExtensionList.lookupSingleton(AvatarContributor.class).allowedSources.add(normalizedUrl)) {
            LOGGER.log(Level.CONFIG, "Adding URL '" + normalizedUrl + "' to avatar allowlist");
        } else {
            LOGGER.log(Level.FINEST, "Skipped adding duplicate URL '" + normalizedUrl + "'");
        }
    }

    /**
     * Normalizes and validates a URL for CSP inclusion.
     * Only http and https URLs with a valid host are accepted.
     *
     * @param url the URL to normalize
     * @return the normalized URL suitable for CSP, or null if invalid
     */
    @CheckForNull
    private static String normalizeUrl(@CheckForNull String url) {
        if (url == null) {
            return null;
        }
        try {
            final URI uri = new URI(url);
            final String host = uri.getHost();
            if (host == null) {
                LOGGER.log(Level.FINER, "Ignoring URI without host: " + url);
                return null;
            }
            // Reject URLs with embedded credentials
            if (uri.getUserInfo() != null) {
                LOGGER.log(Level.FINER, "Ignoring URI with user info: " + url);
                return null;
            }
            String scheme = uri.getScheme();
            if (scheme == null) {
                LOGGER.log(Level.FINER, "Ignoring URI without scheme: " + url);
                return null;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                LOGGER.log(Level.FINER, "Ignoring URI with unsupported scheme: " + url);
                return null;
            }

            // ToASCII for IDNs
            final String asciiHost = java.net.IDN.toASCII(host.toLowerCase(Locale.ROOT));

            StringBuilder normalized = new StringBuilder(scheme).append("://");
            // IPv6 needs brackets when serialized
            if (asciiHost.contains(":") && !asciiHost.startsWith("[")) {
                normalized.append("[").append(asciiHost).append("]");
            } else {
                normalized.append(asciiHost);
            }

            final int port = uri.getPort();
            // omit default ports
            if (port != -1) {
                if (!(scheme.equals("http") && port == 80) && !(scheme.equals("https") && port == 443)) {
                    normalized.append(":").append(port);
                }
            }

            // preserve encoded path (raw) to avoid surprising decoding differences
            final String rawPath = uri.getRawPath();
            if (rawPath != null && !rawPath.isEmpty()) {
                normalized.append(rawPath);
            }
            return normalized.toString();
        } catch (URISyntaxException | IllegalArgumentException e) {
            LOGGER.log(Level.FINE, "Failed to parse avatar URI: " + url, e);
            return null;
        }
    }


    /**
     * Utility method extracting the domain specification for CSP fetch directives from a specified URL.
     * If the specified URL is not {@code null}, has a host, and {@code http} or {@code https} scheme, this method
     * will return its domain.
     * This can be used by implementations of {@link jenkins.security.csp.Contributor} for which {@link #allow(String)}
     * is not flexible enough (e.g., requesting administrator approval for a domain).
     *
     * @param url the URL
     * @return the domain from the specified URL, or {@code null} if the URL does not satisfy the stated conditions
     * @deprecated Use domain-based allowlisting only when necessary. Prefer URL-specific allowlisting via {@link #allow(String)}
     */
    @Deprecated
    @CheckForNull
    public static String extractDomainFromUrl(@CheckForNull String url) {
        if (url == null) {
            return null;
        }
        try {
            final URI uri = new URI(url);
            final String host = uri.getHost();
            if (host == null) {
                // If there's no host, assume a local path
                LOGGER.log(Level.FINER, "Ignoring URI without host: " + url);
                return null;
            }
            String domain = host.toLowerCase(Locale.ROOT);
            String scheme = uri.getScheme();
            if (scheme != null) {
                scheme = scheme.toLowerCase(Locale.ROOT);
                if (scheme.equals("http") || scheme.equals("https")) {
                    domain = scheme + "://" + domain;
                } else {
                    LOGGER.log(Level.FINER, "Ignoring URI with unsupported scheme: " + url);
                    return null;
                }
            }
            final int port = uri.getPort();
            if (port != -1) {
                domain = domain + ":" + port;
            }
            return domain;
        } catch (URISyntaxException e) {
            LOGGER.log(Level.FINE, "Failed to parse avatar URI: " + url, e);
            return null;
        }
    }
}
