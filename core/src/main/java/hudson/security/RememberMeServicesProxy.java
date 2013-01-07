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

import jenkins.model.Jenkins;
import jenkins.security.ConfidentialStore;
import org.acegisecurity.ui.rememberme.RememberMeServices;
import org.acegisecurity.Authentication;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * {@link RememberMeServices} proxy.
 *
 * <p>
 * In Jenkins, we need {@link Jenkins} instance to perform remember-me service,
 * because it relies on {@link ConfidentialStore}. However, security
 * filters can be initialized before Jenkins is initialized.
 * (See #1210 for example.)
 *
 * <p>
 * So to work around the problem, we use a proxy.
 *
 * @author Kohsuke Kawaguchi
 */
public class RememberMeServicesProxy implements RememberMeServices {
    private volatile RememberMeServices delegate;

    public Authentication autoLogin(HttpServletRequest request, HttpServletResponse response) {
        RememberMeServices d = delegate;
        if(d!=null)     return d.autoLogin(request,response);
        return null;
    }

    public void loginFail(HttpServletRequest request, HttpServletResponse response) {
        RememberMeServices d = delegate;
        if(d!=null)     d.loginFail(request,response);
    }

    public void loginSuccess(HttpServletRequest request, HttpServletResponse response, Authentication successfulAuthentication) {
        RememberMeServices d = delegate;
        if(d!=null)     d.loginSuccess(request,response,successfulAuthentication);
    }

    public void setDelegate(RememberMeServices delegate) {
        this.delegate = delegate;
    }
}
