/*
 * The MIT License
 *
 * Copyright (c) 2021, Daniel Beck
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

package jenkins;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.URLConnectionDecorator;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Sets a Jenkins specific user-agent HTTP header for {@link HttpURLConnection}.
 *
 * @since 2.286
 */
@Extension
@Restricted(NoExternalUse.class)
@Symbol("userAgent")
public class UserAgentURLConnectionDecorator extends URLConnectionDecorator {
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* non-final for Groovy */ boolean DISABLED = SystemProperties.getBoolean(UserAgentURLConnectionDecorator.class.getName() + ".DISABLED");

    @Override
    public void decorate(URLConnection con) throws IOException {
        if (!DISABLED && con instanceof HttpURLConnection httpConnection) {
            httpConnection.setRequestProperty("User-Agent", getUserAgent());
        }
    }

    public static String getUserAgent() {
        return "Jenkins/" + Jenkins.getVersion();
    }
}
