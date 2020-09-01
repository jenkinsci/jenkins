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

import java.util.Arrays;
import java.util.Objects;
import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.userdetails.UserDetails;

/**
 * @deprecated use {@link org.springframework.security.authentication.AbstractAuthenticationToken}
 */
@Deprecated
public abstract class AbstractAuthenticationToken implements Authentication {

    private final GrantedAuthority[] authorities;
    private Object details;
    private boolean authenticated;

    public AbstractAuthenticationToken() {
        this.authorities = new GrantedAuthority[0];
    }

    public AbstractAuthenticationToken(GrantedAuthority[] authorities) {
        this.authorities = authorities;
    }

    @Override
    public String getName() {
        Object principal = getPrincipal();
        return principal instanceof UserDetails ? ((UserDetails) principal).getUsername() : String.valueOf(principal);
    }

    @Override
    public GrantedAuthority[] getAuthorities() {
        return authorities;
    }

    @Override
    public Object getDetails() {
        return details;
    }

    public void setDetails(Object details) {
        this.details = details;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    @Override
    public String toString() {
        return super.toString() + getName();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AbstractAuthenticationToken &&
                Objects.equals(getPrincipal(), ((AbstractAuthenticationToken) o).getPrincipal()) &&
                Objects.equals(getDetails(), ((AbstractAuthenticationToken) o).getDetails()) &&
                Objects.equals(getCredentials(), ((AbstractAuthenticationToken) o).getCredentials()) &&
                isAuthenticated() == ((AbstractAuthenticationToken) o).isAuthenticated() &&
                Arrays.equals(getAuthorities(), ((AbstractAuthenticationToken) o).getAuthorities());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

}
