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

package org.acegisecurity.userdetails;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.GrantedAuthorityImpl;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class UserDetailsTest {

    @Test
    void typePreserved() {
        SpecialUserDetails sud = new SpecialUserDetailsImpl("bob", "s3cr3t", true, new GrantedAuthority[] {new GrantedAuthorityImpl("here")}, "bobstuff");
        org.springframework.security.core.userdetails.UserDetails sud2 = sud.toSpring();
        assertEquals("bob", sud2.getUsername());
        assertEquals("here", sud2.getAuthorities().stream().map(org.springframework.security.core.GrantedAuthority::getAuthority).collect(Collectors.joining(";")));
        UserDetails sud3 = UserDetails.fromSpring(sud2);
        assertThat(sud3, instanceOf(SpecialUserDetails.class));
        assertEquals("bob", sud3.getUsername());
        assertEquals("here", Stream.of(sud3.getAuthorities()).map(GrantedAuthority::getAuthority).collect(Collectors.joining(";")));
        assertEquals("bobstuff", ((SpecialUserDetails) sud3).getStuff());
    }

    private interface SpecialUserDetails extends UserDetails {
        String getStuff();
    }

    private static final class SpecialUserDetailsImpl extends User implements SpecialUserDetails {

        private final String stuff;

        SpecialUserDetailsImpl(String username, String password, boolean enabled, GrantedAuthority[] authorities, String stuff) {
            super(username, password, enabled, authorities);
            this.stuff = stuff;
        }

        @Override
        public String getStuff() {
            return stuff;
        }

    }

}
