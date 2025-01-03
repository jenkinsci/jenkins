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

package org.acegisecurity.providers.anonymous;

import java.io.Serializable;
import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.userdetails.UserDetails;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * @deprecated use {@link org.springframework.security.authentication.AnonymousAuthenticationToken}
 */
@Deprecated
public class AnonymousAuthenticationToken implements Authentication, Serializable {
    private static final long serialVersionUID = 1L;

    // TODO perhaps better to extend AbstractAuthenticationToken
    private final org.springframework.security.authentication.AnonymousAuthenticationToken delegate;

    @Restricted(NoExternalUse.class)
    public AnonymousAuthenticationToken(org.springframework.security.authentication.AnonymousAuthenticationToken delegate) {
        this.delegate = delegate;
    }

    public AnonymousAuthenticationToken(String key, Object principal, GrantedAuthority[] authorities) {
        this(new org.springframework.security.authentication.AnonymousAuthenticationToken(key, UserDetails.toSpringPrincipal(principal), GrantedAuthority.toSpring(authorities)));
    }

    @Override
    public GrantedAuthority[] getAuthorities() {
        return GrantedAuthority.fromSpring(delegate.getAuthorities());
    }

    @Override
    public Object getCredentials() {
        return delegate.getCredentials();
    }

    @Override
    public Object getDetails() {
        return delegate.getDetails();
    }

    public void setDetails(Object details) {
        delegate.setDetails(details);
    }

    @Override
    public Object getPrincipal() {
        return UserDetails.fromSpringPrincipal(delegate.getPrincipal());
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
        return o instanceof Authentication && ((Authentication) o).getName().equals(getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public String toString() {
        return super.toString() + ": " + getName();
    }

    @Override
    public org.springframework.security.core.Authentication toSpring() {
        return delegate;
    }

}
