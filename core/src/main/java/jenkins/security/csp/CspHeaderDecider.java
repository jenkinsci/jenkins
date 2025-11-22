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

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import java.util.Optional;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Extension point to decide which {@link jenkins.security.csp.CspHeader} will be set.
 * The highest priority implementation returning a value will be chosen both to
 * show the configuration UI for {@link jenkins.security.csp.impl.CspConfiguration},
 * if any, and to select the header during request processing.
 * As a result, implementations must have fairly consistent behavior and not, e.g.,
 * inspect the current HTTP request to decide between providing a value (any value)
 * or not (inspecting the request and deciding which header to choose is fine,
 * as long as an implementation always returns a header).
 *
 * @since TODO
 */
@Restricted(Beta.class)
public interface CspHeaderDecider extends ExtensionPoint {
    Optional<CspHeader> decide();

    static ExtensionList<CspHeaderDecider> all() {
        return ExtensionList.lookup(CspHeaderDecider.class);
    }

    static Optional<CspHeaderDecider> getCurrentDecider() {
        for (CspHeaderDecider decider : all()) {
            final Optional<CspHeader> decision = decider.decide();
            if (decision.isPresent()) {
                return Optional.of(decider);
            }
        }
        return Optional.empty();
    }
}
