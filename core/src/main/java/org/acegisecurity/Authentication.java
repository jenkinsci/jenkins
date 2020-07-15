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

import java.util.Collection;
import java.util.stream.Collectors;

public interface Authentication extends org.springframework.security.core.Authentication {

    @Override
    Collection<? extends GrantedAuthority> getAuthorities();

    class SpringSecurityBridge implements Authentication {
        private final org.springframework.security.core.Authentication a;
        public SpringSecurityBridge(org.springframework.security.core.Authentication a) {
            this.a = a;
        }
        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return a.getAuthorities().stream().map(ga -> new GrantedAuthority.SpringSecurityBridge(ga)).collect(Collectors.toList());
        }
        @Override
        public Object getCredentials() {
            return a.getCredentials();
        }
        @Override
        public Object getDetails() {
            return a.getDetails();
        }
        @Override
        public Object getPrincipal() {
            return a.getPrincipal(); // TODO wrap UserDetails if necessary
        }
        @Override
        public boolean isAuthenticated() {
            return a.isAuthenticated();
        }
        @Override
        public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
            a.setAuthenticated(isAuthenticated);
        }
        @Override
        public String getName() {
            return a.getName();
        }
    }

}
