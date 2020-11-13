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

package org.acegisecurity.acls.sid;

import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

public class PrincipalSid implements Sid {

    private final String principal;
    
    public PrincipalSid(String principal) {
        this.principal = principal;
    }

    /**
     * @since TODO
     */
    public PrincipalSid(Authentication a) {
        Object p = a.getPrincipal();
        this.principal = p instanceof UserDetails ? ((UserDetails) p).getUsername() : p.toString();
    }

    /**
     * @deprecated use {@link #PrincipalSid(Authentication)}
     */
    @Deprecated
    public PrincipalSid(org.acegisecurity.Authentication a) {
        this(a.toSpring());
    }
    
    public String getPrincipal() {
        return principal;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PrincipalSid && Objects.equals(principal, ((PrincipalSid) o).principal);
    }

    @Override
    public int hashCode() {
        return principal.hashCode();
    }

    @Override
    public String toString() {
        return principal;
    }

}
