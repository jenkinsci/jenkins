package hudson.security

import jenkins.model.Jenkins
import org.acegisecurity.AuthenticationException
import org.acegisecurity.BadCredentialsException
import org.acegisecurity.GrantedAuthority
import org.acegisecurity.GrantedAuthorityImpl
import org.acegisecurity.ui.rememberme.TokenBasedRememberMeServices
import org.acegisecurity.userdetails.User
import org.acegisecurity.userdetails.UserDetails
import org.acegisecurity.userdetails.UsernameNotFoundException
import com.gargoylesoftware.htmlunit.util.Cookie
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.JenkinsRule
import org.springframework.dao.DataAccessException

import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

import static java.util.logging.Level.FINEST

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class TokenBasedRememberMeServices2Test {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private boolean failureInduced;

    private Logger logger = Logger.getLogger(TokenBasedRememberMeServices.class.name)

    private List<LogRecord> logs = []
    private Handler loghandler

    @Before
    public void setUp() {
        loghandler = new Handler() {
            @Override
            void publish(LogRecord record) {
                logs.add(record);
            }

            @Override
            void flush() {
            }

            @Override
            void close() throws SecurityException {
            }
        }
        loghandler.level = FINEST
        logger.addHandler(loghandler)
        logger.level = FINEST
    }

    @After
    public void tearDown() {
        logger.removeHandler(loghandler);
        logger.level = null
    }

    @Test
    public void rememberMeAutoLoginFailure()  {
        j.jenkins.securityRealm = new InvalidUserWhenLoggingBackInRealm()

        def wc = j.createWebClient()
        wc.login("alice","alice",true)

        // we should see a remember me cookie
        def c = getRememberMeCookie(wc)
        assert c!=null

        // start a new session and attempt to access Jenkins,
        // which should cause autoLogin failures
        wc = j.createWebClient()
        wc.cookieManager.addCookie(c);

        // even if SecurityRealm chokes, it shouldn't kill the page
        logs.clear()
        wc.goTo("")

        // make sure that the server recorded this failure
        assert failureInduced
        assert logs.find { it.message.contains("contained username 'alice' but was not found")}!=null
        // and the problematic cookie should have been removed
        assert getRememberMeCookie(wc)==null
    }

    private Cookie getRememberMeCookie(JenkinsRule.WebClient wc) {
        wc.cookieManager.getCookie(TokenBasedRememberMeServices2.ACEGI_SECURITY_HASHED_REMEMBER_ME_COOKIE_KEY)
    }

    private class InvalidUserWhenLoggingBackInRealm extends AbstractPasswordBasedSecurityRealm {
        @Override
        protected UserDetails authenticate(String username, String password) throws AuthenticationException {
            if (username==password)
                return new User(username,password,true,[new GrantedAuthorityImpl("myteam")] as GrantedAuthority[])
            throw new BadCredentialsException(username);
        }

        @Override
        GroupDetails loadGroupByGroupname(String groupname) throws UsernameNotFoundException, DataAccessException {
            throw new UnsupportedOperationException()
        }

        @Override
        UserDetails loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException {
            failureInduced = true
            throw new UsernameNotFoundException("intentionally not working");
        }
    }


    @Test
    public void basicFlow()  {
        j.jenkins.securityRealm = new StupidRealm()

        def wc = j.createWebClient()
        wc.login("bob","bob",true)

        // we should see a remember me cookie
        def c = getRememberMeCookie(wc)
        assert c!=null

        // start a new session and attempt to access Jenkins,
        wc = j.createWebClient()
        wc.cookieManager.addCookie(c);

        // this will trigger remember me
        wc.goTo("")

        // make sure that our security realm failed to report the info correctly
        assert failureInduced
        // but we should have logged in
        wc.executeOnServer {
            def a = Jenkins.getAuthentication()
            assert a.name=="bob"
            assert a.authorities*.authority.join(":")=="authenticated:myteam"
        }
    }

    private class StupidRealm extends InvalidUserWhenLoggingBackInRealm {
        @Override
        UserDetails loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException {
            failureInduced = true
            throw new UserMayOrMayNotExistException("I cannot tell");
        }
    }
}
