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

import java.util.ArrayList;
import java.util.List;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Convenient base class for CSP contributors only adding individual domains to a fetch directive.
 * Plugins may need to do this, likely for {@code img-src}, to allow loading avatars and similar resources.
 *
 * @since TODO
 */
@Restricted(Beta.class)
public abstract class SimpleContributor implements Contributor {
    private final List<Directive> allowlist = new ArrayList<>();

    protected void allow(FetchDirective directive, String... domain) {
        // Inheritance is unused, so doesn't matter despite being a FetchDirective
        this.allowlist.add(new Directive(directive.toKey(), null, List.of(domain)));
    }

    @Override
    public final void apply(CspBuilder cspBuilder) {
        allowlist.forEach(entry -> cspBuilder.add(entry.name(), entry.values().toArray(new String[0])));
    }
}
