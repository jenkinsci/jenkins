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
import org.acegisecurity.providers.ProviderManager
import org.acegisecurity.providers.anonymous.AnonymousAuthenticationProvider
import org.acegisecurity.providers.ldap.LdapAuthenticationProvider
import org.acegisecurity.providers.ldap.authenticator.BindAuthenticator2
import org.acegisecurity.ldap.DefaultInitialDirContextFactory
import org.acegisecurity.ldap.search.FilterBasedLdapUserSearch
import org.acegisecurity.providers.rememberme.RememberMeAuthenticationProvider
import jenkins.model.Jenkins
import hudson.security.LDAPSecurityRealm.AuthoritiesPopulatorImpl
import hudson.Util
import javax.naming.Context

/*
    Configure LDAP as the authentication realm.

    Authentication is performed by doing LDAP bind.
    The 'instance' object refers to the instance of LDAPSecurityRealm
*/

initialDirContextFactory(DefaultInitialDirContextFactory, instance.getLDAPURL() ) {
  if(instance.managerDN!=null) {
    managerDn = instance.managerDN;
    managerPassword = instance.getManagerPassword();
  }
  extraEnvVars = [(Context.REFERRAL):"follow"];
}

ldapUserSearch(FilterBasedLdapUserSearch, instance.userSearchBase, instance.userSearch, initialDirContextFactory) {
    searchSubtree=true
}

bindAuthenticator(BindAuthenticator2,initialDirContextFactory) {
    // this is when you the user name can be translated into DN.
//  userDnPatterns = [
//    "uid={0},ou=people"
//  ]
    // this is when we need to find it.
    userSearch = ldapUserSearch;
}

authoritiesPopulator(AuthoritiesPopulatorImpl, initialDirContextFactory, instance.groupSearchBase) {
    // see DefaultLdapAuthoritiesPopulator for other possible configurations
    searchSubtree = true;
    groupSearchFilter = "(| (member={0}) (uniqueMember={0}) (memberUid={1}))";
}

authenticationManager(ProviderManager) {
    providers = [
        // talk to LDAP
        bean(LdapAuthenticationProvider,bindAuthenticator,authoritiesPopulator),

    // these providers apply everywhere
        bean(RememberMeAuthenticationProvider) {
            key = Jenkins.getInstance().getSecretKey();
        },
        // this doesn't mean we allow anonymous access.
        // we just authenticate anonymous users as such,
        // so that later authorization can reject them if so configured
        bean(AnonymousAuthenticationProvider) {
            key = "anonymous"
        }
    ]
}
