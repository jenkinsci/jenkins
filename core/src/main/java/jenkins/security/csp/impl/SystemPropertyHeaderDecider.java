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
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.security.csp.CspHeader;
import jenkins.security.csp.CspHeaderDecider;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Specify the {@link jenkins.security.csp.CspHeader} for {@link CspDecorator} via system property.
 */
@Restricted(NoExternalUse.class)
@Extension(ordinal = Double.MAX_VALUE) // Highest precedence
public class SystemPropertyHeaderDecider implements CspHeaderDecider {

    private static final Logger LOGGER = Logger.getLogger(SystemPropertyHeaderDecider.class.getName());
    static final String SYSTEM_PROPERTY_NAME = CspHeader.class.getName() + ".headerName";

    @Override
    public Optional<CspHeader> decide() {
        final String systemProperty = SystemProperties.getString(SYSTEM_PROPERTY_NAME);
        if (systemProperty != null) {
            LOGGER.log(Level.FINEST, "Using system property: {0}", new Object[]{ systemProperty });
            return Arrays.stream(CspHeader.values()).filter(h -> h.getHeaderName().equals(systemProperty)).findFirst();
        }
        return Optional.empty();
    }

    // Jelly
    public String getHeaderName() {
        final Optional<CspHeader> decision = decide();
        return decision.map(CspHeader::getHeaderName).orElse(null);
    }
}
