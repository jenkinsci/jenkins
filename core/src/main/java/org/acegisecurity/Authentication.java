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
import java.util.Collection;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.providers.anonymous.AnonymousAuthenticationToken;

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
        if (a instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return new AnonymousAuthenticationToken((org.springframework.security.authentication.AnonymousAuthenticationToken) a);
        } else if (a instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken) {
            return new UsernamePasswordAuthenticationToken((org.springframework.security.authentication.UsernamePasswordAuthenticationToken) a);
        } else {
            return new Authentication() {
                @Override
                public GrantedAuthority[] getAuthorities() {
                    return GrantedAuthority.fromSpring(a.getAuthorities());
                }
                @Override
                public Object getCredentials() {
                    return a.getCredentials(); // TODO wrap if necessary
                }
                @Override
                public Object getDetails() {
                    return a.getDetails(); // TODO wrap if necessary
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
            };
        }
    }

    default org.springframework.security.core.Authentication toSpring() {
        return new org.springframework.security.core.Authentication() {
            @Override
            public Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
                return GrantedAuthority.toSpring(Authentication.this.getAuthorities());
            }
            @Override
            public Object getCredentials() {
                return Authentication.this.getCredentials(); // TODO wrap if necessary
            }
            @Override
            public Object getDetails() {
                return Authentication.this.getDetails(); // TODO wrap if necessary
            }
            @Override
            public Object getPrincipal() {
                return Authentication.this.getPrincipal(); // TODO wrap UserDetails if necessary
            }
            @Override
            public boolean isAuthenticated() {
                return Authentication.this.isAuthenticated();
            }
            @Override
            public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
                Authentication.this.setAuthenticated(isAuthenticated);
            }
            @Override
            public String getName() {
                return Authentication.this.getName();
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
        };
    }

}
