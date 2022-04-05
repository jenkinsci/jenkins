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

import hudson.security.UserMayOrMayNotExistException;
import hudson.security.UserMayOrMayNotExistException2;
import org.acegisecurity.BadCredentialsException;

/**
 * @deprecated use {@link org.springframework.security.core.userdetails.UsernameNotFoundException}
 */
@Deprecated
public class UsernameNotFoundException extends BadCredentialsException {

    public UsernameNotFoundException(String msg) {
        super(msg);
    }

    public UsernameNotFoundException(String msg, Object extraInformation) {
        super(msg, extraInformation);
    }

    public UsernameNotFoundException(String msg, Throwable t) {
        super(msg, t);
    }

    @Override
    public org.springframework.security.core.userdetails.UsernameNotFoundException toSpring() {
        return new org.springframework.security.core.userdetails.UsernameNotFoundException(toString(), this);
    }

    public static UsernameNotFoundException fromSpring(org.springframework.security.core.userdetails.UsernameNotFoundException x) {
        if (x instanceof UserMayOrMayNotExistException2) {
            return UserMayOrMayNotExistException.fromSpring((UserMayOrMayNotExistException2) x);
        } else {
            return new UsernameNotFoundException(x.toString(), x);
        }
    }

}
