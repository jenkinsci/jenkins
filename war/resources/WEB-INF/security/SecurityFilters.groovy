/*
    Defines a part of the security configuration of Hudson.

    This file must define a servlet Filter instance with the name 'filter'
*/
import org.acegisecurity.providers.anonymous.AnonymousProcessingFilter
import org.acegisecurity.ui.AccessDeniedHandlerImpl
import org.acegisecurity.ui.ExceptionTranslationFilter
import org.acegisecurity.ui.basicauth.BasicProcessingFilter
import org.acegisecurity.ui.basicauth.BasicProcessingFilterEntryPoint
import org.acegisecurity.ui.webapp.AuthenticationProcessingFilter
import org.acegisecurity.context.HttpSessionContextIntegrationFilter
import org.acegisecurity.ui.webapp.AuthenticationProcessingFilterEntryPoint
import hudson.security.ChainedServletFilter

filter(ChainedServletFilter) {
    def entryPoint = bean(AuthenticationProcessingFilterEntryPoint) {
        loginFormUrl = "/login"
    }

    filters = [
        // this persists the authentication across requests by using session
        bean(HttpSessionContextIntegrationFilter) {
        },
        // allow clients to submit basic authentication credential
        bean(BasicProcessingFilter) {
            authenticationManager = authenticationManager
            // if basic authentication fails (which only happens incorrect basic auth credential is sent),
            // respond with 401 with basic auth request, instead of redirecting the user to the login page,
            // since users of basic auth tends to be a program and won't see the redirection to the form
            // page as a failure
            authenticationEntryPoint = bean(BasicProcessingFilterEntryPoint) {
                realmName = "Hudson"
            }
        },
        bean(AuthenticationProcessingFilter) {
            authenticationManager = authenticationManager
            authenticationFailureUrl = "/loginError"
            defaultTargetUrl = "/"
            filterProcessesUrl = "/j_acegi_security_check"
        },
        bean(AnonymousProcessingFilter) {
            key = "anonymous" // must match with the AnonymousProvider
            userAttribute = "anonymous,"
        },
        bean(ExceptionTranslationFilter) {
            // property can be created programatically/eagler like this,
            // instead of doing everything as managed Spring beans
            accessDeniedHandler = new AccessDeniedHandlerImpl()
            authenticationEntryPoint = entryPoint
        }
    ]
}
