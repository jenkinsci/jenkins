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

package org.acegisecurity.ui.rememberme;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.acegisecurity.Authentication;

final class RememberMeServicesSpringImpl implements org.springframework.security.web.authentication.RememberMeServices {

    final RememberMeServices delegate;

    RememberMeServicesSpringImpl(RememberMeServices delegate) {
        this.delegate = delegate;
    }

    @Override
    public org.springframework.security.core.Authentication autoLogin(HttpServletRequest request, HttpServletResponse response) {
        Authentication a = delegate.autoLogin(request, response);
        return a != null ? a.toSpring() : null;
    }

    @Override
    public void loginFail(HttpServletRequest request, HttpServletResponse response) {
        delegate.loginFail(request, response);
    }

    @Override
    public void loginSuccess(HttpServletRequest request, HttpServletResponse response, org.springframework.security.core.Authentication successfulAuthentication) {
        delegate.loginSuccess(request, response, Authentication.fromSpring(successfulAuthentication));
    }

}
