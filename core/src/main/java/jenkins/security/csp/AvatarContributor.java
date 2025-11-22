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

    @Override
    public void apply(CspBuilder cspBuilder) {
        domains.forEach(d -> cspBuilder.add("img-src", d));
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
            String domain = host;
            final String scheme = uri.getScheme();
            if (scheme != null) {
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
