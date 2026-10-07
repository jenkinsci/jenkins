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

import static jenkins.security.csp.Directive.DATA;
import static jenkins.security.csp.Directive.IMG_SRC;
import static jenkins.security.csp.Directive.SCRIPT_SRC;
import static jenkins.security.csp.Directive.STYLE_SRC;
import static jenkins.security.csp.Directive.UNSAFE_INLINE;

import hudson.Extension;
import hudson.model.UsageStatistics;
import jenkins.security.csp.Contributor;
import jenkins.security.csp.CspBuilder;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Add the CSP directives currently required for Jenkins to work properly.
 * The long-term goal is for these directives to become unnecessary.
 * Any for which we determine with high confidence they can never be fixed
 * should be moved into {@link BaseContributor} instead.
 */
@Extension(ordinal = Integer.MAX_VALUE / 2.0)
@Restricted(NoExternalUse.class)
public final class CompatibleContributor implements Contributor {
    @Override
    public void apply(CspBuilder cspBuilder) {
        cspBuilder
                .add(STYLE_SRC, UNSAFE_INLINE) // Infeasible for now given inline styles in SVGs
                .add(IMG_SRC, DATA); // TODO https://github.com/jenkinsci/csp-plugin/issues/60
        if (UsageStatistics.isEnabled()) {
            // For transparency, always do this while usage stats submission is enabled, rather than checking #isDue.
            // TODO https://issues.jenkins.io/browse/JENKINS-76268
            cspBuilder.add(SCRIPT_SRC, "usage.jenkins.io");
        }
    }
}
