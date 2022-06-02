/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

package hudson.security;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.Jenkins;
import org.acegisecurity.acls.sid.GrantedAuthoritySid;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * {@link AuthorizationStrategy} implementation that emulates the legacy behavior.
 * @author Kohsuke Kawaguchi
 */
public final class LegacyAuthorizationStrategy extends AuthorizationStrategy {
    private static final ACL LEGACY_ACL = new SparseACL(null) {{
        add(EVERYONE, Permission.READ, true);
        add(new GrantedAuthoritySid("admin"), Jenkins.ADMINISTER, true);
    }};

    @DataBoundConstructor
    public LegacyAuthorizationStrategy() {
    }

    @Override
    public ACL getRootACL() {
        return LEGACY_ACL;
    }

    @Override
    public Collection<String> getGroups() {
        return Collections.singleton("admin");
    }

    @Extension @Symbol("legacy")
    public static final class DescriptorImpl extends Descriptor<AuthorizationStrategy> {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.LegacyAuthorizationStrategy_DisplayName();
        }
    }
}
