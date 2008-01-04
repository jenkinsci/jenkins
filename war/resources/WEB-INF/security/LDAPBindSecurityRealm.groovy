import org.acegisecurity.providers.ProviderManager
import org.acegisecurity.providers.anonymous.AnonymousAuthenticationProvider
import org.acegisecurity.providers.ldap.LdapAuthenticationProvider
import org.acegisecurity.providers.ldap.authenticator.BindAuthenticator
import org.acegisecurity.providers.ldap.populator.DefaultLdapAuthoritiesPopulator
import org.acegisecurity.ldap.DefaultInitialDirContextFactory

/*
    Configure LDAP as the authentication realm.

    Authentication is performed by doing LDAP bind.
*/

initialDirContextFactory(DefaultInitialDirContextFactory,it.providerUrl) {

  // if anonymous bind is not allowed --- but what is the use of anonymous bind?
  // managerDn = "..."
  // managerPassword="..."
}

bindAuthenticator(BindAuthenticator,initialDirContextFactory) {
  userDnPatterns = [
    "uid={0},ou=people"
  ]
}
authoritiesPopulator(DefaultLdapAuthoritiesPopulator,initialDirContextFactory,"ou=groups") {
  // groupRoleAttribute = "ou";
}

authenticationManager(ProviderManager) {
    providers = [
        // talk to LDAP
        bean(LdapAuthenticationProvider,bindAuthenticator,authoritiesPopulator),
        // this doesn't mean we allow anonymous access.
        // we just authenticate anonymous users as such,
        // so that later authorization can reject them if so configured
        bean(AnonymousAuthenticationProvider) {
            key = "anonymous"
        }
    ]
}