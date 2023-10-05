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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.security.ACL;
import java.io.Serializable;
import java.security.Principal;
import java.util.Objects;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.providers.anonymous.AnonymousAuthenticationToken;
import org.acegisecurity.userdetails.UserDetails;

/**
 * @deprecated use {@link org.springframework.security.core.Authentication}
 */
@Deprecated
public interface Authentication extends Principal, Serializable {

    GrantedAuthority[] getAuthorities();

    Object getCredentials();

    Object getDetails();

    Object getPrincipal();

    boolean isAuthenticated();

    void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException;

    static @NonNull Authentication fromSpring(@NonNull org.springframework.security.core.Authentication a) {
        Objects.requireNonNull(a);
        if (a == ACL.SYSTEM2) {
            return ACL.SYSTEM;
        } else if (a instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return new AnonymousAuthenticationToken((org.springframework.security.authentication.AnonymousAuthenticationToken) a);
        } else if (a instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken) {
            return new UsernamePasswordAuthenticationToken((org.springframework.security.authentication.UsernamePasswordAuthenticationToken) a);
        } else if (a instanceof AuthenticationSpringImpl) {
            return ((AuthenticationSpringImpl) a).delegate;
        } else {
            return new Authentication() {
                @Override
                public GrantedAuthority[] getAuthorities() {
                    return GrantedAuthority.fromSpring(a.getAuthorities());
                }

                @Override
                public Object getCredentials() {
                    return a.getCredentials(); // seems to be String, typically, so nothing to wrap
                }

                @Override
                public Object getDetails() {
                    // Could try to wrap WebAuthenticationDetails, but it does not appear that any code actual checkcasts this.
                    return a.getDetails();
                }

                @Override
                public Object getPrincipal() {
                    return UserDetails.fromSpringPrincipal(a.getPrincipal());
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

    default @NonNull org.springframework.security.core.Authentication toSpring() {
        return new AuthenticationSpringImpl(this);
    }

}
