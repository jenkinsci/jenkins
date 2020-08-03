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
import org.springframework.security.core.GrantedAuthority;

public class GrantedAuthoritySid implements Sid {

    private final String grantedAuthority;

    public GrantedAuthoritySid(String grantedAuthority) {
        this.grantedAuthority = grantedAuthority;
    }

    /**
     * @since TODO
     */
    public GrantedAuthoritySid(GrantedAuthority ga) {
        grantedAuthority = ga.getAuthority();
    }

    /**
     * @deprecated use {@link #GrantedAuthoritySid(GrantedAuthority)}
     */
    @Deprecated
    public GrantedAuthoritySid(org.acegisecurity.GrantedAuthority ga) {
        this(ga.toSpring());
    }

    public String getGrantedAuthority() {
        return grantedAuthority;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GrantedAuthoritySid && Objects.equals(grantedAuthority, ((GrantedAuthoritySid) o).grantedAuthority);
    }

    @Override
    public int hashCode() {
        return grantedAuthority.hashCode();
    }

    @Override
    public String toString() {
        return grantedAuthority;
    }

}
