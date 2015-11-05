package jenkins.security

import hudson.security.AbstractPasswordBasedSecurityRealm
import hudson.security.GroupDetails
import hudson.security.UserMayOrMayNotExistException
import org.acegisecurity.AuthenticationException
import org.acegisecurity.BadCredentialsException
import org.acegisecurity.GrantedAuthority
import org.acegisecurity.GrantedAuthorityImpl
import org.acegisecurity.userdetails.User
import org.acegisecurity.userdetails.UserDetails
import org.acegisecurity.userdetails.UsernameNotFoundException
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.JenkinsRule
import org.springframework.dao.DataAccessException

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class LastGrantedAuthoritiesPropertyTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void basicflow() {
        j.jenkins.securityRealm = new TestSecurityRealm()

        // login, and make sure it leaves the LastGrantedAuthoritiesProperty object
        def wc = j.createWebClient()
        wc.login("alice","alice:development:us")

        def u = hudson.model.User.get("alice")
        def p = u.getProperty(LastGrantedAuthoritiesProperty.class)
        assertAuthorities(p,"authenticated:alice:development:us")
        assertAuthorities(u.impersonate(),"authenticated:alice:development:us")

        // visiting the configuration page shouldn't change authorities
        def pg = wc.goTo("user/alice/configure");
        j.submit(pg.getFormByName("config"));

        p = u.getProperty(LastGrantedAuthoritiesProperty.class)
        assertAuthorities(p,"authenticated:alice:development:us")
        assertAuthorities(u.impersonate(),"authenticated:alice:development:us")

        // change should be reflected right away
        wc.login("alice","alice:development:uk")
        p = u.getProperty(LastGrantedAuthoritiesProperty.class)
        assertAuthorities(p,"authenticated:alice:development:uk")
        assertAuthorities(u.impersonate(),"authenticated:alice:development:uk")
    }

    void assertAuthorities(p,expected) {
        assert p.authorities*.authority.join(":")==expected
    }

    /**
     * SecurityRealm that cannot load information about other users, such Active Directory.
     */
    private class TestSecurityRealm extends AbstractPasswordBasedSecurityRealm {
        @Override
        protected UserDetails authenticate(String username, String password) throws AuthenticationException {
            if (password=="error")
                throw new BadCredentialsException(username);
            def authorities = password.split(":").collect { new GrantedAuthorityImpl(it) }

            return new User(username,"",true,authorities.toArray(new GrantedAuthority[0]))
        }

        @Override
        GroupDetails loadGroupByGroupname(String groupname) throws UsernameNotFoundException, DataAccessException {
            throw new UnsupportedOperationException()
        }

        @Override
        UserDetails loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException {
            throw new UserMayOrMayNotExistException("fallback");
        }
    }
}
