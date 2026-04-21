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

package jenkins.security.apitoken;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.security.Permission;
import java.util.Set;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.springframework.security.core.Authentication;

/**
 * Scope-gate used by {@link hudson.security.ACL}. For scoped tokens, walks
 * {@link Permission#impliedBy} the same way user permission checks do; for any other
 * authentication this is a no-op.
 */
@Restricted(NoExternalUse.class)
public final class ApiTokenScope {

    private ApiTokenScope() {}

    public static boolean permits(@NonNull Authentication authentication, @NonNull Permission permission) {
        if (!(authentication instanceof ScopedApiTokenAuthentication scoped)) {
            return true;
        }
        Set<String> allowed = scoped.getAllowedScopes();
        for (Permission cursor = permission; cursor != null; cursor = cursor.impliedBy) {
            if (allowed.contains(cursor.getId())) {
                return true;
            }
        }
        return false;
    }
}
