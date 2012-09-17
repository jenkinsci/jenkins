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

import org.acegisecurity.AuthenticationManager;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.DisabledException;

/**
 * {@link AuthenticationManager} proxy that delegates to another instance.
 *
 * <p>
 * This is used so that we can set up servlet filters first (which requires a reference
 * to {@link AuthenticationManager}), then later change the actual authentication manager
 * (and its set up) at runtime.
 *
 * @author Kohsuke Kawaguchi
 */
public class AuthenticationManagerProxy implements AuthenticationManager {
    private volatile AuthenticationManager delegate;

    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        AuthenticationManager m = delegate; // fix the reference we are working with

        if(m ==null)
            throw new DisabledException("Authentication service is still not ready yet");
        else
            return m.authenticate(authentication);
    }

    public void setDelegate(AuthenticationManager manager) {
        this.delegate = manager;
    }
}
