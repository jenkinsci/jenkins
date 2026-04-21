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
 * Shared scope-check helper used by the permission-enforcement path in
 * {@link hudson.security.ACL}.
 * <p>
 * Unscoped authentications (i.e. anything that is not a
 * {@link ScopedApiTokenAuthentication}) always pass the scope gate and are evaluated
 * solely by the regular authorization strategy. For scoped authentications, the
 * requested permission is permitted only if itself or any permission in its
 * {@link Permission#impliedBy} chain is in the token's allowed-scope set, mirroring the
 * way user permission checks already walk the implication chain.
 *
 * @since TODO
 */
@Restricted(NoExternalUse.class)
public final class ApiTokenScope {

    private ApiTokenScope() {
        // utility class
    }

    /**
     * @return {@code true} if the given authentication is allowed to exercise the given
     * permission under its scope. Returns {@code true} unconditionally for non-scoped
     * authentications, so callers can use this as an additive gate without special-casing
     * unscoped callers.
     */
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
