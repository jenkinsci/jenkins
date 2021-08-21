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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.Serializable;
import org.acegisecurity.GrantedAuthority;

/**
 * @deprecated use {@link org.springframework.security.core.userdetails.UserDetails} instead
 */
@Deprecated
public interface UserDetails extends Serializable {

    GrantedAuthority[] getAuthorities();

    String getPassword();

    String getUsername();

    boolean isAccountNonExpired();

    boolean isAccountNonLocked();

    boolean isCredentialsNonExpired();

    boolean isEnabled();

    default @NonNull org.springframework.security.core.userdetails.UserDetails toSpring() {
        return new UserDetailsSpringImpl(this);
    }

    static @NonNull UserDetails fromSpring(@NonNull org.springframework.security.core.userdetails.UserDetails ud) {
        if (ud instanceof UserDetailsSpringImpl) {
            return ((UserDetailsSpringImpl) ud).delegate;
        }
        return new UserDetails() {
            @Override
            public GrantedAuthority[] getAuthorities() {
                return GrantedAuthority.fromSpring(ud.getAuthorities());
            }
            @Override
            public String getPassword() {
                return ud.getPassword();
            }
            @Override
            public String getUsername() {
                return ud.getUsername();
            }
            @Override
            public boolean isAccountNonExpired() {
                return ud.isAccountNonExpired();
            }
            @Override
            public boolean isAccountNonLocked() {
                return ud.isAccountNonLocked();
            }
            @Override
            public boolean isCredentialsNonExpired() {
                return ud.isCredentialsNonExpired();
            }
            @Override
            public boolean isEnabled() {
                return ud.isEnabled();
            }
        };
    }

    static @Nullable Object toSpringPrincipal(@CheckForNull Object acegiPrincipal) {
        if (acegiPrincipal instanceof UserDetails) {
            return ((UserDetails) acegiPrincipal).toSpring();
        } else {
            return acegiPrincipal;
        }
    }

    static @Nullable Object fromSpringPrincipal(@CheckForNull Object springPrincipal) {
        if (springPrincipal instanceof org.springframework.security.core.userdetails.UserDetails) {
            return fromSpring((org.springframework.security.core.userdetails.UserDetails) springPrincipal);
        } else {
            return springPrincipal;
        }
    }

}
