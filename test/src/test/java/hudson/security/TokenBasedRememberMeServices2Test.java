package hudson.security;

import com.gargoylesoftware.htmlunit.util.Cookie;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import static java.util.logging.Level.FINEST;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.BadCredentialsException;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.GrantedAuthorityImpl;
import org.acegisecurity.ui.rememberme.TokenBasedRememberMeServices;
import org.acegisecurity.userdetails.User;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.springframework.dao.DataAccessException;

public class TokenBasedRememberMeServices2Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule();

    private static boolean failureInduced;

    @Before
    public void resetFailureInduced() {
        failureInduced = false;
    }

    @Test
    public void rememberMeAutoLoginFailure() throws Exception {
        j.jenkins.setSecurityRealm(new InvalidUserWhenLoggingBackInRealm());

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("alice", "alice", true);

        // we should see a remember me cookie
        Cookie c = getRememberMeCookie(wc);
        assertNotNull(c);

        // start a new session and attempt to access Jenkins,
        // which should cause autoLogin failures
        wc = j.createWebClient();
        wc.getCookieManager().addCookie(c);

        // even if SecurityRealm chokes, it shouldn't kill the page
        logging.capture(1000).record(TokenBasedRememberMeServices.class, FINEST);
        wc.goTo("");

        // make sure that the server recorded this failure
        assertTrue(failureInduced);
        assertTrue(logging.getMessages().stream().anyMatch(m -> m.contains("contained username 'alice' but was not found")));
        // and the problematic cookie should have been removed
        assertNull(getRememberMeCookie(wc));
    }

    private Cookie getRememberMeCookie(JenkinsRule.WebClient wc) {
        return wc.getCookieManager().getCookie(TokenBasedRememberMeServices2.ACEGI_SECURITY_HASHED_REMEMBER_ME_COOKIE_KEY);
    }

    private static class InvalidUserWhenLoggingBackInRealm extends AbstractPasswordBasedSecurityRealm {
        @Override
        protected UserDetails authenticate(String username, String password) throws AuthenticationException {
            if (username.equals(password)) {
                return new User(username, password, true, new GrantedAuthority[] {new GrantedAuthorityImpl("myteam")});
            }
            throw new BadCredentialsException(username);
        }

        @Override
        public GroupDetails loadGroupByGroupname(String groupname) throws UsernameNotFoundException, DataAccessException {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException {
            failureInduced = true;
            throw new UsernameNotFoundException("intentionally not working");
        }
    }

    @Test
    public void basicFlow() throws Exception {
        j.jenkins.setSecurityRealm(new StupidRealm());

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("bob", "bob", true);

        // we should see a remember me cookie
        Cookie c = getRememberMeCookie(wc);
        assertNotNull(c);

        // start a new session and attempt to access Jenkins,
        wc = j.createWebClient();
        wc.getCookieManager().addCookie(c);

        // this will trigger remember me
        wc.goTo("");

        // make sure that our security realm failed to report the info correctly
        assertTrue(failureInduced);
        // but we should have logged in
        wc.executeOnServer(() -> {
            Authentication a = Jenkins.getAuthentication();
            assertEquals("bob", a.getName());
            assertEquals(ImmutableList.of("authenticated", "myteam"), Arrays.asList(a.getAuthorities()).stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()));
            return null;
        });
    }

    private static class StupidRealm extends InvalidUserWhenLoggingBackInRealm {
        @Override
        public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException {
            failureInduced = true;
            throw new UserMayOrMayNotExistException("I cannot tell");
        }
    }

}
