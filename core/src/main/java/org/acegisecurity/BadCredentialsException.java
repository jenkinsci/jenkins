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

import org.acegisecurity.userdetails.UsernameNotFoundException;

/**
 * @deprecated use {@link org.springframework.security.authentication.BadCredentialsException}
 */
@Deprecated
public class BadCredentialsException extends AuthenticationException {

    public BadCredentialsException(String msg) {
        super(msg);
    }

    public BadCredentialsException(String msg, Object extraInformation) {
        super(msg, extraInformation);
    }

    public BadCredentialsException(String msg, Throwable t) {
        super(msg, t);
    }

    @Override
    public org.springframework.security.core.AuthenticationException toSpring() {
        return new org.springframework.security.authentication.BadCredentialsException(toString(), this);
    }

    public static BadCredentialsException fromSpring(org.springframework.security.core.AuthenticationException x) {
        if (x instanceof org.springframework.security.core.userdetails.UsernameNotFoundException) {
            return UsernameNotFoundException.fromSpring((org.springframework.security.core.userdetails.UsernameNotFoundException) x);
        } else {
            return new BadCredentialsException(x.toString(), x);
        }
    }

}
