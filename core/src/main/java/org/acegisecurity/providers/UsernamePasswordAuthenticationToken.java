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

package org.acegisecurity.providers;

import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.apache.commons.lang.NotImplementedException;

/**
 * @deprecated TODO replacement
 */
@Deprecated
public class UsernamePasswordAuthenticationToken implements Authentication {

    private final org.springframework.security.authentication.UsernamePasswordAuthenticationToken delegate;

    public UsernamePasswordAuthenticationToken(Object principal, Object credentials) {
        delegate = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, credentials);
    }

    public UsernamePasswordAuthenticationToken(Object principal, Object credentials, GrantedAuthority[] authorities) {
        delegate = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, credentials, GrantedAuthority.toSpring(authorities));
    }

    @Override
    public GrantedAuthority[] getAuthorities() {
        return GrantedAuthority.fromSpring(delegate.getAuthorities());
    }
    @Override
    public Object getCredentials() {
        throw new NotImplementedException();
    }
    @Override
    public Object getDetails() {
        throw new NotImplementedException();
    }
    @Override
    public Object getPrincipal() {
        return delegate.getPrincipal(); // TODO wrap UserDetails if necessary
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

    // TODO Serializable? equals/hashCode/toString?

}
