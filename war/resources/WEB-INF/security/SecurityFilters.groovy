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
import org.acegisecurity.providers.anonymous.AnonymousProcessingFilter
import org.acegisecurity.ui.ExceptionTranslationFilter
import org.acegisecurity.ui.basicauth.BasicProcessingFilter
import org.acegisecurity.ui.basicauth.BasicProcessingFilterEntryPoint
import org.acegisecurity.ui.rememberme.RememberMeProcessingFilter
import hudson.security.HttpSessionContextIntegrationFilter2

// providers that apply to both patterns
def commonProviders(redirectUrl) {
    return [
        bean(AnonymousProcessingFilter) {
            key = "anonymous" // must match with the AnonymousProvider
            userAttribute = "anonymous,"
        },
        bean(ExceptionTranslationFilter) {
            accessDeniedHandler = new AccessDeniedHandlerImpl()
            authenticationEntryPoint = bean(HudsonAuthenticationEntryPoint) {
                loginFormUrl = redirectUrl;
            }
        },
        bean(UnwrapSecurityExceptionFilter)
    ]
}

filter(ChainedServletFilter) {
    filters = [
        // this persists the authentication across requests by using session
        bean(HttpSessionContextIntegrationFilter2) {
        },
        // allow clients to submit basic authentication credential
        bean(BasicProcessingFilter) {
            authenticationManager = securityComponents.manager
            // if basic authentication fails (which only happens incorrect basic auth credential is sent),
            // respond with 401 with basic auth request, instead of redirecting the user to the login page,
            // since users of basic auth tends to be a program and won't see the redirection to the form
            // page as a failure
            authenticationEntryPoint = bean(BasicProcessingFilterEntryPoint) {
                realmName = "Hudson"
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
    ] + commonProviders("/login?from={0}")
}

// this filter set up is used to emulate the legacy Hudson behavior
// of container authentication before 1.160 
legacy(ChainedServletFilter) {
    filters = [
        bean(BasicAuthenticationFilter)
    ] + commonProviders("/loginEntry?from={0}")
    // when using container-authentication we can't hit /login directly.
    // we first have to hit protected /loginEntry, then let the container
    // trap that into /login.
}