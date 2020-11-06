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
import org.acegisecurity.userdetails.UserDetails;

@Deprecated
final class AuthenticationSpringImpl implements org.springframework.security.core.Authentication {

    final Authentication delegate;

    AuthenticationSpringImpl(Authentication delegate) {
        this.delegate = delegate;
    }

    @Override
    public Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
        return GrantedAuthority.toSpring(delegate.getAuthorities());
    }

    @Override
    public Object getCredentials() {
        return delegate.getCredentials();
    }

    @Override
    public Object getDetails() {
        return delegate.getDetails();
    }

    @Override
    public Object getPrincipal() {
        return UserDetails.toSpringPrincipal(delegate.getPrincipal());
    }

    @Override
    public boolean isAuthenticated() {
        return delegate.isAuthenticated();
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        delegate.setAuthenticated(isAuthenticated);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof org.springframework.security.core.Authentication && ((org.springframework.security.core.Authentication) o).getName().equals(getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public String toString() {
        return super.toString() + ": " + getName();
    }

}
