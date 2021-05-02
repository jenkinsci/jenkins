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

import org.acegisecurity.AcegiSecurityException;
import org.acegisecurity.AuthenticationException;
import org.springframework.dao.DataAccessException;

/**
 * @deprecated use {@link org.springframework.security.core.userdetails.UserDetailsService}
 */
@Deprecated
public interface UserDetailsService {

    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException;

    static UserDetailsService fromSpring(org.springframework.security.core.userdetails.UserDetailsService uds) {
        return username -> {
            try {
                return UserDetails.fromSpring(uds.loadUserByUsername(username));
            } catch (org.springframework.security.core.AuthenticationException x) {
                throw AuthenticationException.fromSpring(x);
            }
        };
    }

    default org.springframework.security.core.userdetails.UserDetailsService toSpring() {
        return username -> {
            try {
                return loadUserByUsername(username).toSpring();
            } catch (AcegiSecurityException x) {
                throw x.toSpring();
            } catch (DataAccessException x) {
                throw x.toSpring();
            }
        };
    }

}
