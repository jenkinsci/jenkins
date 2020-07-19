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

import java.io.Serializable;
import java.security.Principal;
import org.apache.commons.lang.NotImplementedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * @deprecated use TODO or {@link org.springframework.security.core.Authentication}
 */
@Deprecated
public interface Authentication extends Principal, Serializable {

    GrantedAuthority[] getAuthorities();

    Object getCredentials();

    Object getDetails();

    Object getPrincipal();

    boolean isAuthenticated();

    void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException;

    static Authentication fromSpring(org.springframework.security.core.Authentication a) {
        return new Authentication() {
            @Override
            public GrantedAuthority[] getAuthorities() {
                return GrantedAuthority.fromSpring(a.getAuthorities());
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
        };
    }

    default org.springframework.security.core.Authentication toSpring() {
        return new AbstractAuthenticationToken(GrantedAuthority.toSpring(getAuthorities())) {
            @Override
            public Object getCredentials() {
                throw new NotImplementedException();
            }
            @Override
            public Object getPrincipal() {
                return Authentication.this.getPrincipal(); // TODO wrap UserDetails if necessary
            }
        };
    }

}
