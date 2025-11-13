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
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import jenkins.security.HMACConfidentialKey;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.springframework.security.core.Authentication;

/**
 * The context (user and page) from which a CSP report is received.
 *
 * @since TODO
 */
@Restricted(Beta.class)
public class ReportingContext {
    private static final HMACConfidentialKey KEY = new HMACConfidentialKey(ReportingContext.class, "key");

    private ReportingContext() {}

    private static String toBase64(String utf8) {
        return Base64.getUrlEncoder().encodeToString(utf8.getBytes(StandardCharsets.UTF_8));
    }

    private static String fromBase64(String b64) {
        return new String(Base64.getUrlDecoder().decode(b64), StandardCharsets.UTF_8);
    }

    public static String encodeContext(
            @CheckForNull final Class<?> ancestorClass,
            @CheckForNull final Authentication authentication,
            @NonNull final String restOfPath) {
        final String userId = authentication == null ? "" : authentication.getName();
        final String encodedContext =
                toBase64(userId) + ":" + toBase64(ancestorClass == null ? "" : ancestorClass.getName()) + ":" + toBase64(restOfPath);
        final String mac =
                Base64.getUrlEncoder().encodeToString(KEY.mac(encodedContext.getBytes(StandardCharsets.UTF_8)));
        return mac + ":" + encodedContext;
    }

    public static DecodedContext decodeContext(final String rawContext) {
        String[] macAndContext = rawContext.split(":", 2);
        if (macAndContext.length != 2) {
            throw new IllegalArgumentException(
                    "Unexpected number of split entries, expected 2, got " + macAndContext.length);
        }
        String mac = macAndContext[0];
        String encodedContext = macAndContext[1];

        if (!KEY.checkMac(
                encodedContext.getBytes(StandardCharsets.UTF_8),
                Base64.getUrlDecoder().decode(mac))) {
            throw new IllegalArgumentException("Mac check failed for " + encodedContext);
        }

        String[] encodedContextParts = encodedContext.split(":", 3);
        if (encodedContextParts.length != 3) {
            throw new IllegalArgumentException(
                    "Unexpected number of split entries, expected 3, got " + encodedContextParts.length);
        }
        return new DecodedContext(
                fromBase64(encodedContextParts[0]),
                fromBase64(encodedContextParts[1]),
                fromBase64(encodedContextParts[2]));
    }

    public record DecodedContext(String userId, String contextClassName, String restOfPath) {
        public DecodedContext(
                @CheckForNull String userId, @NonNull String contextClassName, @NonNull String restOfPath) {
            this.userId = Util.fixEmpty(userId);
            this.contextClassName = contextClassName;
            this.restOfPath = restOfPath;
        }
    }
}
