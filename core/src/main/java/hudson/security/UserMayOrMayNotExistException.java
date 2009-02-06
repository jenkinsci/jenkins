/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.security;

import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.acegisecurity.userdetails.UserDetailsService;

/**
 * Thrown from {@link UserDetailsService#loadUserByUsername(String)}
 * to indicate that the underlying {@link SecurityRealm} is incapable
 * of retrieving the information, and furthermore, the system cannot
 * tell if such an user exists or not.
 *
 * <p>
 * This happens, for example, when the security realm is on top of the servlet implementation,
 * there's no way of even knowing if an user of a given name exists or not.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.280
 */
public class UserMayOrMayNotExistException extends UsernameNotFoundException {
    public UserMayOrMayNotExistException(String msg) {
        super(msg);
    }

    public UserMayOrMayNotExistException(String msg, Object extraInformation) {
        super(msg, extraInformation);
    }

    public UserMayOrMayNotExistException(String msg, Throwable t) {
        super(msg, t);
    }
}
