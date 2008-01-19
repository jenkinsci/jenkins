/*
    Configure Hudson's own user database as the authentication realm.
*/
import org.acegisecurity.providers.ProviderManager
import hudson.security.HudsonPrivateSecurityRealm.HudsonUserDetailsService
import org.acegisecurity.providers.dao.DaoAuthenticationProvider
import org.acegisecurity.providers.anonymous.AnonymousAuthenticationProvider
import org.acegisecurity.providers.rememberme.RememberMeAuthenticationProvider
import hudson.model.Hudson


userDetailsService(HudsonUserDetailsService) {}

authenticationManager(ProviderManager) {
    providers = [
        // the primary authentication source is Hudson's own user database
        bean(DaoAuthenticationProvider) {
            userDetailsService = userDetailsService
        },

    // these providers apply everywhere
        bean(RememberMeAuthenticationProvider) {
            key = Hudson.getInstance().getSecretKey();
        },
        // this doesn't mean we allow anonymous access.
        // we just authenticate anonymous users as such,
        // so that later authorization can reject them if so configured
        bean(AnonymousAuthenticationProvider) {
            key = "anonymous"
        }
    ]
}