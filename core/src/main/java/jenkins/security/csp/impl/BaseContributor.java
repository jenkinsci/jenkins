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

import static jenkins.security.csp.Directive.REPORT_SAMPLE;
import static jenkins.security.csp.Directive.SELF;

import hudson.Extension;
import jenkins.security.csp.Contributor;
import jenkins.security.csp.CspBuilder;
import jenkins.security.csp.Directive;
import jenkins.security.csp.FetchDirective;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Add a minimal set of CSP directives not expected to be removed later.
 */
@Extension(ordinal = Integer.MAX_VALUE)
@Restricted(NoExternalUse.class)
public final class BaseContributor implements Contributor {
    @Override
    public void apply(CspBuilder cspBuilder) {
        cspBuilder
                .initialize(FetchDirective.DEFAULT_SRC, SELF)
                .add(Directive.STYLE_SRC, REPORT_SAMPLE)
                .add(Directive.SCRIPT_SRC, REPORT_SAMPLE)
                .add(Directive.FORM_ACTION, SELF)
                .add(Directive.BASE_URI) // 'none'
                .add(Directive.FRAME_ANCESTORS, SELF);
    }
}
