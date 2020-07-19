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
import java.util.stream.Stream;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * @deprecated use TODO or {@link org.springframework.security.core.GrantedAuthority}
 */
@Deprecated
public interface GrantedAuthority {

    String getAuthority();

    @Override
    String toString();

    @Override
    boolean equals(Object obj);

    @Override
    int hashCode();

    static GrantedAuthority fromSpring(org.springframework.security.core.GrantedAuthority ga) {
        return new GrantedAuthorityImpl(ga.getAuthority());
    }

    default org.springframework.security.core.GrantedAuthority toSpring() {
        return new SimpleGrantedAuthority(getAuthority());
    }

    static GrantedAuthority[] fromSpring(Collection<? extends org.springframework.security.core.GrantedAuthority> gas) {
        return gas.stream().map(GrantedAuthority::fromSpring).toArray(GrantedAuthority[]::new);
    }

    static Collection<? extends org.springframework.security.core.GrantedAuthority> toSpring(GrantedAuthority[] gas) {
        return Stream.of(gas).map(GrantedAuthority::toSpring).collect(Collectors.toList());
    }

}
