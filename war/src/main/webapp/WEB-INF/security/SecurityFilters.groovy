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
/*
    Defines a part of the security configuration of Hudson.

    This file must define a servlet Filter instance with the name 'filter'
*/
import hudson.security.AccessDeniedHandlerImpl
import hudson.security.AuthenticationProcessingFilter2
import hudson.security.BasicAuthenticationFilter
import hudson.security.ChainedServletFilter
import hudson.security.UnwrapSecurityExceptionFilter
import hudson.security.HudsonAuthenticationEntryPoint
import jenkins.security.BasicHeaderProcessor
import jenkins.security.ExceptionTranslationFilter
import org.acegisecurity.providers.anonymous.AnonymousProcessingFilter
import org.acegisecurity.ui.basicauth.BasicProcessingFilterEntryPoint
import org.acegisecurity.ui.rememberme.RememberMeProcessingFilter
import hudson.security.HttpSessionContextIntegrationFilter2

// providers that apply to both patterns
def commonProviders() {
    return [
        bean(AnonymousProcessingFilter) {
            key = "anonymous" // must match with the AnonymousProvider
            userAttribute = "anonymous,"
        },
        bean(ExceptionTranslationFilter) {
            accessDeniedHandler = new AccessDeniedHandlerImpl()
            authenticationEntryPoint = bean(HudsonAuthenticationEntryPoint) {
                loginFormUrl = '/'+securityRealm.getLoginUrl()+"?from={0}"
            }
        },
        bean(UnwrapSecurityExceptionFilter)
    ]
}

filter(ChainedServletFilter) {
    filters = [
        // this persists the authentication across requests by using session
        bean(HttpSessionContextIntegrationFilter2) {
            // not allowing filter to create sessions, as it potentially tries to create
            // sessions for any request (although it usually fails
            // I suspect this is related to JENKINS-12585, in that
            // it ends up setting Set-Cookie for image responses.
            // Instead, we use layout.jelly to create sessions.
            allowSessionCreation = false
        },
        // if any "Authorization: Basic xxx:yyy" is sent this is the filter that processes it
        bean(BasicHeaderProcessor) {
            // if basic authentication fails (which only happens incorrect basic auth credential is sent),
            // respond with 401 with basic auth request, instead of redirecting the user to the login page,
            // since users of basic auth tends to be a program and won't see the redirection to the form
            // page as a failure
            authenticationEntryPoint = bean(BasicProcessingFilterEntryPoint) {
                realmName = "Jenkins"
            }
        },
        bean(AuthenticationProcessingFilter2) {
            authenticationManager = securityComponents.manager
            rememberMeServices = securityComponents.rememberMe
            authenticationFailureUrl = "/loginError"
            defaultTargetUrl = "/"
            filterProcessesUrl = "/j_acegi_security_check"
        },
        bean(RememberMeProcessingFilter) {
            rememberMeServices = securityComponents.rememberMe
            authenticationManager = securityComponents.manager
        },
    ] + commonProviders()
}

// this filter set up is used to emulate the legacy Hudson behavior
// of container authentication before 1.160 
legacy(ChainedServletFilter) {
    filters = [
        bean(BasicAuthenticationFilter)
    ] + commonProviders()
    // when using container-authentication we can't hit /login directly.
    // we first have to hit protected /loginEntry, then let the container
    // trap that into /login.
}
