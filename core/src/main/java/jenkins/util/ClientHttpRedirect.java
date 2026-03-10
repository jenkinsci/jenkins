/*
 * The MIT License
 *
 * Copyright (c) 2026, CloudBees, Inc.
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

package jenkins.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * An HTTP response that redirects the client to the given URL.
 * Unlike {@link org.kohsuke.stapler.HttpRedirect}, this implements a client-side redirect (using meta tag and/or JavaScript).
 * This allows the redirect to work even when Content Security Policy is enforced in Chrome
 * (which applies {@code form-action} to redirects after form submission).
 * <p>
 * For security reasons, only HTTP/HTTPS URLs and relative paths are allowed.
 * Attempts to redirect to other schemes (e.g., {@code javascript:}, {@code data:}, {@code file:})
 * will result in a security warning page instead of performing the redirect.
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy/form-action">MDN documentation on form-action</a>
 * @see <a href="https://github.com/w3c/webappsec-csp/issues/8">Content Security Policy issue discussing this behavior</a>
 * @since 2.550
 */
public record ClientHttpRedirect(@NonNull String redirectUrl) implements HttpResponse {

    public ClientHttpRedirect {
        Objects.requireNonNull(redirectUrl);
    }

    private static boolean isSafeToRedirectTo(@NonNull String url) {
        if (Util.isSafeToRedirectTo(url)) {
            return true;
        }

        String urlLower = url.toLowerCase(Locale.ENGLISH);
        return urlLower.startsWith("http://") || urlLower.startsWith("https://");
    }

    @Override
    public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object o) throws IOException, ServletException {
        if (!isSafeToRedirectTo(redirectUrl)) {
            throw hudson.util.HttpResponses.error(403,
                "Unsafe redirect blocked: Jenkins only allows redirects to HTTP/HTTPS URLs or relative paths. "
                    + "Blocked URL: " + Util.escape(redirectUrl));
        }

        rsp.setContentType("text/html;charset=UTF-8");
        Util.printRedirect(req.getContextPath(), redirectUrl, Util.escape(redirectUrl), rsp.getWriter());
    }
}
