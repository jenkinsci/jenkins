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
import java.net.IDN;
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

    private final Set<String> domains = ConcurrentHashMap.newKeySet();
        private final Set<String> allowedSources = ConcurrentHashMap.newKeySet();

        @Override
        public void apply(CspBuilder cspBuilder) {
                domains.forEach(d -> cspBuilder.add("img-src", d));
                allowedSources.forEach(s -> cspBuilder.add("img-src", s));
        }

    /**
     * Request addition of the domain of the specified URL to the allowed set of avatar image domains.
     * <p>
     *     This is a utility method intended to accept any avatar URL from an undetermined, but trusted (for images) domain.
     *     If the specified URL is not {@code null}, has a host, and {@code http} or {@code https} scheme, its domain will
     *     be added to the set of allowed domains.
     * </p>
     * <p>
     *     <strong>Important:</strong> Only implementations restricting specification of avatar URLs to at least somewhat
     *     privileged users to should invoke this method, for example users with at least {@link hudson.model.Item#CONFIGURE}
     *     permission. Note that this guidance may change over time and require implementation changes.
     * </p>
     *
     * @param url The avatar image URL whose domain should be added to the list of allowed domains
     */
    public static void allow(@CheckForNull String url) {
        String domain = extractDomainFromUrl(url);

        if (domain == null) {
            LOGGER.log(Level.FINE, "Skipping null domain in avatar URL: " + url);
            return;
        }

        if (ExtensionList.lookupSingleton(AvatarContributor.class).domains.add(domain)) {
            LOGGER.log(Level.CONFIG, "Adding domain '" + domain + "' from avatar URL: " + url);
        } else {
            LOGGER.log(Level.FINEST, "Skipped adding duplicate domain '" + domain + "' from avatar URL: " + url);
        }
    }

    /**
     * Request addition of a specific URL to the allowed set of avatar sources.
     * This method allows external plugins to allowlist specific image URLs so they can be loaded via CSP.
     *
     * @param url The full avatar image URL to allow.
     */
    @SuppressWarnings("unused")
        public static void allowUrl(@CheckForNull String url) {
                String normalized = normalizeUrl(url);

                if (normalized == null) {
                        LOGGER.log(Level.FINE, "Skipping invalid or unsupported avatar URL: " + url);
                        return;
                }

                if (ExtensionList.lookupSingleton(AvatarContributor.class).allowedSources.add(normalized)) {
                        LOGGER.log(Level.CONFIG, "Adding allowed avatar URL: " + normalized + " (from: " + url + ")");
                } else {
                        LOGGER.log(Level.FINEST, "Skipped adding duplicate allowed url: " + normalized + " (from: " + url + ")");
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
     */
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

    /**
     * Normalizes a URL for use in Content-Security-Policy.
     * <p>
     * This method performs several important steps:
     * <ul>
     * <li>Only http or https schemes are accepted.</li>
     * <li>Removes embedded credentials for security.</li>
     * <li>Converts IDN to ASCII.</li>
     * <li>Normalizes IPv6 addresses.</li>
     * <li>Removes default ports.</li>
     * </ul>
     * </p>
     *
     * @param url The raw input URL.
     * @return A canonical, safe string representation of the URL, or null if the URL is invalid or unsafe.
     */
    @CheckForNull
    public static String normalizeUrl(@CheckForNull String url) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = new URI(url);

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

            if (uri.getUserInfo() != null && !uri.getUserInfo().isEmpty()) {
                LOGGER.log(Level.FINER, "Ignoring URI with embedded credentials: " + url);
                return null;
            }

            String rawAuthority = uri.getRawAuthority();
            if (rawAuthority == null) {
                LOGGER.log(Level.FINER, "Ignoring URI without authority: " + url);
                return null;
            }

            String host;
            int port = uri.getPort();

            if (rawAuthority.startsWith("[")) {
                int end = rawAuthority.indexOf(']');
                if (end == -1) {
                    return null;
                }
                host = rawAuthority.substring(1, end);
            } else {
                int colon = rawAuthority.indexOf(':');
                host = colon >= 0 ? rawAuthority.substring(0, colon) : rawAuthority;
            }

            String asciiHost = IDN.toASCII(host).toLowerCase(Locale.ROOT);
            boolean ipv6 = asciiHost.contains(":");
            String hostPart = ipv6 ? "[" + asciiHost + "]" : asciiHost;

            boolean omitPort =
                port == -1 ||
                    (scheme.equals("http") && port == 80) ||
                    (scheme.equals("https") && port == 443);

            String portPart = omitPort ? "" : ":" + port;

            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }

            String query = uri.getRawQuery();
            String queryPart = query == null || query.isEmpty() ? "" : "?" + query;

            return scheme + "://" + hostPart + portPart + path + queryPart;

        } catch (URISyntaxException | IllegalArgumentException e) {
            LOGGER.log(Level.FINE, "Failed to normalize avatar URI: " + url, e);
            return null;
        }
    }

}
