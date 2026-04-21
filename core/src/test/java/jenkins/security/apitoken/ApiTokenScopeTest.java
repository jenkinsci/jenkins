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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Item;
import hudson.security.Permission;
import java.util.Collections;
import java.util.Set;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class ApiTokenScopeTest {

    @Test
    void unscopedAuthenticationAlwaysPermits() {
        Authentication auth = anonymous();
        assertTrue(ApiTokenScope.permits(auth, Jenkins.ADMINISTER));
        assertTrue(ApiTokenScope.permits(auth, Item.READ));
    }

    @Test
    void scopedAuthenticationRestrictsToDeclaredScope() {
        Authentication auth = scoped(Set.of(Item.READ.getId()));
        assertTrue(ApiTokenScope.permits(auth, Item.READ));
        assertFalse(ApiTokenScope.permits(auth, Item.BUILD));
    }

    @Test
    void scopedAuthenticationHonoursImpliedByChain() {
        // Item.DISCOVER.impliedBy = Item.READ, so an Item.READ scope satisfies Item.DISCOVER
        Authentication auth = scoped(Set.of(Item.READ.getId()));
        assertTrue(ApiTokenScope.permits(auth, Item.DISCOVER));
    }

    @Test
    void jenkinsAdministerScopeSatisfiesAnyPermission() {
        // Every Item.* permission eventually chains up to Jenkins.ADMINISTER via Permission.HUDSON_ADMINISTER.
        Authentication auth = scoped(Set.of(Jenkins.ADMINISTER.getId()));
        assertTrue(ApiTokenScope.permits(auth, Item.READ));
        assertTrue(ApiTokenScope.permits(auth, Item.BUILD));
        assertTrue(ApiTokenScope.permits(auth, Item.CONFIGURE));
    }

    @Test
    void hudsonAdministerScopeSatisfiesAnyPermission() {
        Authentication auth = scoped(Set.of(Permission.HUDSON_ADMINISTER.getId()));
        assertTrue(ApiTokenScope.permits(auth, Item.READ));
        assertTrue(ApiTokenScope.permits(auth, Item.BUILD));
    }

    @Test
    void siblingItemPermissionsAreNotRelatedThroughImpliedBy() {
        // Item.CONFIGURE and Item.READ are siblings, NOT in each other's impliedBy chain.
        // Documented footgun: UI must help users select companions.
        Authentication auth = scoped(Set.of(Item.CONFIGURE.getId()));
        assertFalse(ApiTokenScope.permits(auth, Item.READ));
    }

    @Test
    void emptyScopeDeniesEverythingButAdminIfPresent() {
        Authentication auth = scoped(Collections.emptySet());
        assertFalse(ApiTokenScope.permits(auth, Item.READ));
        assertFalse(ApiTokenScope.permits(auth, Jenkins.ADMINISTER));
    }

    private static Authentication anonymous() {
        return new UsernamePasswordAuthenticationToken(
                "anon", "N/A", Set.of(new SimpleGrantedAuthority("authenticated")));
    }

    private static Authentication scoped(Set<String> scopes) {
        return new ScopedApiTokenAuthentication(anonymous(), scopes);
    }
}
