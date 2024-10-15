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

package org.acegisecurity.providers.dao;

import org.acegisecurity.AcegiSecurityException;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.providers.AuthenticationProvider;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.acegisecurity.userdetails.UserDetails;

/**
 * @deprecated use {@link org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider}
 */
@Deprecated
public abstract class AbstractUserDetailsAuthenticationProvider implements AuthenticationProvider {

    private final org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider delegate =
            new org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider() {
        @Override
        protected void additionalAuthenticationChecks(
                org.springframework.security.core.userdetails.UserDetails userDetails,
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken authentication)
                throws org.springframework.security.core.AuthenticationException {
            try {
                AbstractUserDetailsAuthenticationProvider.this.additionalAuthenticationChecks(UserDetails.fromSpring(userDetails), new UsernamePasswordAuthenticationToken(authentication));
            } catch (AcegiSecurityException x) {
                throw x.toSpring();
            }
        }

        @Override
        protected org.springframework.security.core.userdetails.UserDetails retrieveUser(
                String username,
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken authentication)
                throws org.springframework.security.core.AuthenticationException {
            try {
                return AbstractUserDetailsAuthenticationProvider.this.retrieveUser(username, new UsernamePasswordAuthenticationToken(authentication)).toSpring();
            } catch (AcegiSecurityException x) {
                throw x.toSpring();
            }
        }
    };

    protected abstract void additionalAuthenticationChecks(UserDetails userDetails, UsernamePasswordAuthenticationToken authentication) throws AuthenticationException;

    protected abstract UserDetails retrieveUser(String username, UsernamePasswordAuthenticationToken authentication) throws AuthenticationException;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        try {
            return Authentication.fromSpring(delegate.authenticate(authentication.toSpring()));
        } catch (org.springframework.security.core.AuthenticationException x) {
            throw AuthenticationException.fromSpring(x);
        }
    }

    @Override
    public boolean supports(Class authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    // TODO other methods as needed: createSuccessAuthentication, getUserCache, etc.

}
