import org.acegisecurity.providers.ProviderManager
import org.acegisecurity.providers.anonymous.AnonymousAuthenticationProvider
import org.acegisecurity.providers.ldap.LdapAuthenticationProvider
import org.acegisecurity.providers.ldap.authenticator.BindAuthenticator
import org.acegisecurity.providers.ldap.populator.DefaultLdapAuthoritiesPopulator
import org.acegisecurity.ldap.DefaultInitialDirContextFactory
import org.acegisecurity.ldap.search.FilterBasedLdapUserSearch
import org.acegisecurity.providers.rememberme.RememberMeAuthenticationProvider
import hudson.model.Hudson

/*
    Configure LDAP as the authentication realm.

    Authentication is performed by doing LDAP bind.
    The 'instance' object refers to the instance of LDAPSecurityRealm
*/

initialDirContextFactory(DefaultInitialDirContextFactory, instance.getLDAPURL() ) {

  // if anonymous bind is not allowed --- but what is the use of anonymous bind?
  // managerDn = "..."
  // managerPassword="..."
}

bindAuthenticator(BindAuthenticator,initialDirContextFactory) {
    // this is when you the user name can be translated into DN.
//  userDnPatterns = [
//    "uid={0},ou=people"
//  ]
    // this is when we need to find it.
    userSearch = bean(FilterBasedLdapUserSearch, instance.userSearchBase, instance.userSearch, initialDirContextFactory) {
        searchSubtree=true
    }
}
authoritiesPopulator(DefaultLdapAuthoritiesPopulator,initialDirContextFactory,"ou=groups") {
  // groupRoleAttribute = "ou";
}

authenticationManager(ProviderManager) {
    providers = [
        // talk to LDAP
        bean(LdapAuthenticationProvider,bindAuthenticator,authoritiesPopulator),

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