/*
 * The MIT License
 *
 * Copyright 2020 CloudBees, Inc.
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

package org.acegisecurity;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.security.ACL;
import hudson.security.SecurityRealm;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import jenkins.model.Jenkins;
import org.acegisecurity.providers.AbstractAuthenticationToken;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.providers.anonymous.AnonymousAuthenticationToken;
import org.acegisecurity.userdetails.User;
import org.acegisecurity.userdetails.UserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

@SuppressWarnings("deprecation")
class AuthenticationTest {

    @Test
    void system() {
        assertEquality(ACL.SYSTEM, ACL.SYSTEM2);
        assertSame(ACL.SYSTEM, org.acegisecurity.Authentication.fromSpring(ACL.SYSTEM2), "old code often compares a == SYSTEM");
        assertSame(ACL.SYSTEM2, ACL.SYSTEM.toSpring());

    }

    @Test
    void anonymous() {
        assertEquality(Jenkins.ANONYMOUS, Jenkins.ANONYMOUS2);
        assertTrue(ACL.isAnonymous(Jenkins.ANONYMOUS));
        assertTrue(ACL.isAnonymous(Authentication.fromSpring(Jenkins.ANONYMOUS2)));
        assertTrue(ACL.isAnonymous(Authentication.fromSpring(Jenkins.ANONYMOUS.toSpring())));
        assertTrue(ACL.isAnonymous2(Jenkins.ANONYMOUS2));
        assertTrue(ACL.isAnonymous2(Jenkins.ANONYMOUS.toSpring()));
        assertTrue(ACL.isAnonymous2(Authentication.fromSpring(Jenkins.ANONYMOUS2).toSpring()));
    }

    @Test
    void user() {
        assertEquality(new org.acegisecurity.providers.UsernamePasswordAuthenticationToken("user", "pass", new GrantedAuthority[] {SecurityRealm.AUTHENTICATED_AUTHORITY}),
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("user", "pass", Set.of(SecurityRealm.AUTHENTICATED_AUTHORITY2)));
    }

    private void assertEquality(Authentication acegi, org.springframework.security.core.Authentication spring) {
        Authentication acegi2 = Authentication.fromSpring(spring);
        org.springframework.security.core.Authentication spring2 = acegi.toSpring();
        Authentication acegi3 = Authentication.fromSpring(spring2);
        org.springframework.security.core.Authentication spring3 = acegi2.toSpring();
        Collection<Executable> checks = new ArrayList<>();
        Authentication[] acegis = new Authentication[] {acegi, acegi2, acegi3};
        org.springframework.security.core.Authentication[] springs = new org.springframework.security.core.Authentication[] {spring, spring2, spring3};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                int _i = i;
                int _j = j;
                checks.add(() -> assertEquals(acegis[_i], acegis[_j], "Acegi #" + (_i + 1) + " == #" + (_j + 1)));
            }
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                int _i = i;
                int _j = j;
                checks.add(() -> assertEquals(springs[_i], springs[_j], "Spring #" + (_i + 1) + " == #" + (_j + 1)));
            }
        }
        assertAll(checks);
    }

    @Test
    void principal() {
        User user = new User("bob", "s3cr3t", true, new GrantedAuthority[0]);
        assertPrincipal(new UsernamePasswordAuthenticationToken(user, "s3cr3t"));
        assertPrincipal(new AnonymousAuthenticationToken("anonymous", user, new GrantedAuthority[] {new GrantedAuthorityImpl("anonymous")}));
    }

    private void assertPrincipal(Authentication acegi) {
        assertThat(acegi.getPrincipal(), instanceOf(UserDetails.class));
        org.springframework.security.core.Authentication spring = acegi.toSpring();
        assertThat(spring.getPrincipal(), instanceOf(org.springframework.security.core.userdetails.UserDetails.class));
        Authentication acegi2 = Authentication.fromSpring(spring);
        assertThat(acegi2.getPrincipal(), instanceOf(UserDetails.class));
    }

    @Test
    void custom() {
        class CustomAuth extends AbstractAuthenticationToken {
            final int x;

            CustomAuth(int x) {
                this.x = x;
            }

            @Override
            public Object getCredentials() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return "xxx";
            }
        }

        CustomAuth ca = new CustomAuth(23);
        Authentication a = Authentication.fromSpring(ca.toSpring());
        assertThat(a, instanceOf(CustomAuth.class));
        assertEquals(23, ((CustomAuth) a).x);
    }

}
