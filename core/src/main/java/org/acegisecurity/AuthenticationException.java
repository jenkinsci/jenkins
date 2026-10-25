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

import hudson.security.UserMayOrMayNotExistException2;
import org.acegisecurity.providers.ProviderNotFoundException;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.springframework.dao.DataAccessException;

/**
 * @deprecated use {@link org.springframework.security.core.AuthenticationException}
 */
@Deprecated
public abstract class AuthenticationException extends AcegiSecurityException {

    private Authentication authentication;
    private Object extraInformation;

    protected AuthenticationException(String msg) {
        super(msg);
    }

    protected AuthenticationException(String msg, Object extraInformation) {
        super(msg);
        this.extraInformation = extraInformation;
    }

    protected AuthenticationException(String msg, Throwable t) {
        super(msg, t);
    }

    public Authentication getAuthentication() {
        return authentication;
    }

    public void setAuthentication(Authentication authentication) {
        this.authentication = authentication;
    }

    public Object getExtraInformation() {
        return extraInformation;
    }

    public void clearExtraInformation() {
        extraInformation = null;
    }

    @Override
    public org.springframework.security.core.AuthenticationException toSpring() {
        return new org.springframework.security.core.AuthenticationException(toString(), this) {};
    }

    /**
     * @return either an {@link AuthenticationException} or a {@link DataAccessException}
     */
    public static RuntimeException fromSpring(org.springframework.security.core.AuthenticationException x) {
        return switch (x) {
            case org.springframework.security.authentication.BadCredentialsException badCredentialsException ->
                    BadCredentialsException.fromSpring(badCredentialsException);
            case org.springframework.security.authentication.AuthenticationServiceException authenticationServiceException ->
                    AuthenticationServiceException.fromSpring(authenticationServiceException);
            case org.springframework.security.authentication.AccountExpiredException accountExpiredException ->
                    AccountExpiredException.fromSpring(accountExpiredException);
            case org.springframework.security.authentication.CredentialsExpiredException credentialsExpiredException ->
                    CredentialsExpiredException.fromSpring(credentialsExpiredException);
            case org.springframework.security.authentication.DisabledException disabledException ->
                    DisabledException.fromSpring(disabledException);
            case org.springframework.security.authentication.InsufficientAuthenticationException insufficientAuthenticationException ->
                    InsufficientAuthenticationException.fromSpring(insufficientAuthenticationException);
            case org.springframework.security.authentication.LockedException lockedException ->
                    LockedException.fromSpring(lockedException);
            case org.springframework.security.authentication.ProviderNotFoundException providerNotFoundException ->
                    ProviderNotFoundException.fromSpring(providerNotFoundException);
            case UserMayOrMayNotExistException2 userMayOrMayNotExistException2 when x.getCause() instanceof DataAccessException ->
                    (DataAccessException) x.getCause();
            case org.springframework.security.core.userdetails.UsernameNotFoundException usernameNotFoundException ->
                    UsernameNotFoundException.fromSpring(usernameNotFoundException);
            default -> new AuthenticationException(x.toString(), x) {
            };
        };
    }

}
