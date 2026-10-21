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

package jenkins.security.csp.impl;

import hudson.Extension;
import hudson.Main;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.csp.CspHeader;
import jenkins.security.csp.CspHeaderDecider;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * During development and tests, use {@link jenkins.security.csp.CspHeader#ContentSecurityPolicy}.
 */
@Restricted(NoExternalUse.class)
@Extension(ordinal = Double.MAX_VALUE / 2) // High precedence
public class DevelopmentHeaderDecider implements CspHeaderDecider {
    private static final Logger LOGGER = Logger.getLogger(DevelopmentHeaderDecider.class.getName());

    static /* non-final for script console */ boolean DISABLED = SystemProperties.getBoolean(DevelopmentHeaderDecider.class.getName() + ".DISABLED");

    @Override
    public Optional<CspHeader> decide() {
        if (DISABLED) {
            LOGGER.log(Level.FINEST, "DevelopmentHeaderDecider disabled by system property");
            return Optional.empty();
        }
        LOGGER.log(Level.FINEST, "Main.isUnitTest: {0}, Main.isDevelopmentMode: {1}", new Object[] { Main.isUnitTest, Main.isDevelopmentMode });
        if (Main.isUnitTest || Main.isDevelopmentMode) {
            return Optional.of(CspHeader.ContentSecurityPolicy);
        }
        return Optional.empty();
    }
}
