package jenkins.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.security.AbstractPasswordBasedSecurityRealm;
import hudson.security.GroupDetails;
import hudson.security.UserMayOrMayNotExistException2;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class LastGrantedAuthoritiesPropertyTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void basicFlow() throws Exception {
        j.jenkins.setSecurityRealm(new TestSecurityRealm());

        // login, and make sure it leaves the LastGrantedAuthoritiesProperty object
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("alice", "alice:development:us");

        hudson.model.User u = hudson.model.User.getOrCreateByIdOrFullName("alice");
        LastGrantedAuthoritiesProperty p = u.getProperty(LastGrantedAuthoritiesProperty.class);
        assertAuthorities(p, "alice:authenticated:development:us");
        assertAuthorities(u.impersonate2(), "alice:authenticated:development:us");

        // visiting the configuration page shouldn't change authorities
        HtmlPage pg = wc.goTo("user/alice/account/");
        j.submit(pg.getFormByName("config"));

        p = u.getProperty(LastGrantedAuthoritiesProperty.class);
        assertAuthorities(p, "alice:authenticated:development:us");
        assertAuthorities(u.impersonate2(), "alice:authenticated:development:us");

        // change should be reflected right away
        wc.login("alice", "alice:development:uk");
        p = u.getProperty(LastGrantedAuthoritiesProperty.class);
        assertAuthorities(p, "alice:authenticated:development:uk");
        assertAuthorities(u.impersonate2(), "alice:authenticated:development:uk");

        // if already receiving the authenticated group, we should avoid duplicate
        wc.login("alice", "alice:authenticated:development:uk");
        p = u.getProperty(LastGrantedAuthoritiesProperty.class);

        assertAuthorities(p, "alice:authenticated:development:uk");
        assertAuthorities(u.impersonate2(), "alice:authenticated:development:uk");
    }

    private void assertAuthorities(LastGrantedAuthoritiesProperty p, String expected) {
        _assertAuthorities(p.getAuthorities2(), expected);
    }

    private void assertAuthorities(Authentication auth, String expected) {
        _assertAuthorities(auth.getAuthorities(), expected);
    }

    private void _assertAuthorities(Collection<? extends GrantedAuthority> grantedAuthorities, String expected) {
        List<String> authorities = grantedAuthorities.stream().map(GrantedAuthority::getAuthority).sorted().collect(Collectors.toList());

        assertEquals(expected, String.join(":", authorities));
    }

    /**
     * SecurityRealm that cannot load information about other users, such Active Directory.
     */
    private static class TestSecurityRealm extends AbstractPasswordBasedSecurityRealm {
        @Override
        protected UserDetails authenticate2(String username, String password) throws AuthenticationException {
            if (password.equals("error"))
                throw new BadCredentialsException(username);
            String[] desiredAuthorities = password.split(":");
            List<GrantedAuthority> authorities = Arrays.stream(desiredAuthorities).map(SimpleGrantedAuthority::new).collect(Collectors.toList());

            return new User(username, "", authorities);
        }

        @Override
        public GroupDetails loadGroupByGroupname2(String groupname, boolean fetchMembers) throws UsernameNotFoundException {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserDetails loadUserByUsername2(String username) throws UsernameNotFoundException {
            throw new UserMayOrMayNotExistException2("fallback");
        }
    }
}
