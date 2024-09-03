package hudson.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import hudson.model.User;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import jenkins.security.seed.UserSeedProperty;
import org.htmlunit.CookieManager;
import org.htmlunit.util.Cookie;
import org.htmlunit.xml.XmlPage;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.Stapler;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.rememberme.AbstractRememberMeServices;
import test.security.realm.InMemorySecurityRealm;

public class TokenBasedRememberMeServices2Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

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
        wc.goTo("");

        // make sure that the server recorded this failure
        assertTrue(failureInduced);
        // and the problematic cookie should have been removed
        assertNull(getRememberMeCookie(wc));
    }

    private Cookie getRememberMeCookie(JenkinsRule.WebClient wc) {
        return wc.getCookieManager().getCookie(AbstractRememberMeServices.SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY);
    }

    private static class InvalidUserWhenLoggingBackInRealm extends AbstractPasswordBasedSecurityRealm {
        @Override
        protected UserDetails authenticate2(String username, String password) throws AuthenticationException {
            if (username.equals(password)) {
                return new org.springframework.security.core.userdetails.User(username, password, Set.of(new SimpleGrantedAuthority("myteam")));
            }
            throw new BadCredentialsException(username);
        }

        @Override
        public GroupDetails loadGroupByGroupname2(String groupname, boolean fetchMembers) throws UsernameNotFoundException {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserDetails loadUserByUsername2(String username) throws UsernameNotFoundException {
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
            Authentication a = Jenkins.getAuthentication2();
            assertEquals("bob", a.getName());
            assertEquals(Arrays.asList("authenticated", "myteam"), a.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()));
            return null;
        });
    }

    private static class StupidRealm extends InvalidUserWhenLoggingBackInRealm {
        @Override
        public UserDetails loadUserByUsername2(String username) throws UsernameNotFoundException {
            failureInduced = true;
            throw new UserMayOrMayNotExistException2("I cannot tell");
        }
    }

    @Test
    @Issue("SECURITY-868")
    @For(UserSeedProperty.class)
    public void rememberMeToken_invalid_afterUserSeedReset() throws Exception {
        j.jenkins.setDisableRememberMe(false);

        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(realm);

        String username = "alice";
        hudson.model.User alice = realm.createAccount(username, username);

        JenkinsRule.WebClient wc = j.createWebClient();

        wc.login(username, username, true);
        CookieManager cm = wc.getCookieManager();

        cm.removeCookie(cm.getCookie("JSESSIONID"));
        assertUserConnected(wc, username);

        alice.getProperty(UserSeedProperty.class).renewSeed();

        cm.removeCookie(cm.getCookie("JSESSIONID"));
        assertUserNotConnected(wc, username);
    }

    @Test
    @Issue("SECURITY-868")
    @For(UserSeedProperty.class)
    public void rememberMeToken_stillValid_afterUserSeedReset_ifUserSeedDisabled() throws Exception {
        boolean currentStatus = UserSeedProperty.DISABLE_USER_SEED;
        try {
            UserSeedProperty.DISABLE_USER_SEED = true;

            j.jenkins.setDisableRememberMe(false);

            HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false, false, null);
            j.jenkins.setSecurityRealm(realm);

            String username = "alice";
            hudson.model.User alice = realm.createAccount(username, username);

            JenkinsRule.WebClient wc = j.createWebClient();

            wc.login(username, username, true);
            CookieManager cm = wc.getCookieManager();

            cm.removeCookie(cm.getCookie("JSESSIONID"));
            assertUserConnected(wc, username);

            alice.getProperty(UserSeedProperty.class).renewSeed();

            cm.removeCookie(cm.getCookie("JSESSIONID"));
            // as userSeed disabled, no care about the renew
            assertUserConnected(wc, username);
        } finally {
            UserSeedProperty.DISABLE_USER_SEED = currentStatus;
        }
    }

    @Test
    @Issue("SECURITY-868")
    public void rememberMeToken_shouldNotAccept_expirationDurationLargerThanConfigured() throws Exception {
        j.jenkins.setDisableRememberMe(false);

        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false, false, null);
        TokenBasedRememberMeServices2 tokenService = (TokenBasedRememberMeServices2) realm.getSecurityComponents().rememberMe2;
        j.jenkins.setSecurityRealm(realm);

        String username = "alice";
        hudson.model.User alice = realm.createAccount(username, username);

        { // a malicious cookie with expiration too far in the future should not work
            JenkinsRule.WebClient wc = j.createWebClient();

            // by default we have 14 days of validity,
            // here we increase artificially the duration of validity, that could be used to have permanent access
            long oneDay = TimeUnit.DAYS.toMillis(1);
            Cookie cookie = createRememberMeCookie(tokenService, oneDay, alice);
            wc.getCookieManager().addCookie(cookie);

            // the application should not use the cookie to connect
            assertUserNotConnected(wc, username);
        }

        { // a hand crafted cookie with regular expiration duration works
            JenkinsRule.WebClient wc = j.createWebClient();

            // by default we have 14 days of validity,
            // here we reduce a bit the expiration date to simulate an "old" cookie (regular usage)
            long minusFiveMinutes = TimeUnit.MINUTES.toMillis(-5);
            Cookie cookie = createRememberMeCookie(tokenService, minusFiveMinutes, alice);
            wc.getCookieManager().addCookie(cookie);

            // if we reactivate the remember me feature, it's ok
            assertUserConnected(wc, username);
        }
    }

    @Test
    @Issue("SECURITY-868")
    public void rememberMeToken_skipExpirationCheck() throws Exception {
        boolean previousConfig = TokenBasedRememberMeServices2.SKIP_TOO_FAR_EXPIRATION_DATE_CHECK;
        try {
            TokenBasedRememberMeServices2.SKIP_TOO_FAR_EXPIRATION_DATE_CHECK = true;

            j.jenkins.setDisableRememberMe(false);

            HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false, false, null);
            TokenBasedRememberMeServices2 tokenService = (TokenBasedRememberMeServices2) realm.getSecurityComponents().rememberMe2;
            j.jenkins.setSecurityRealm(realm);

            String username = "alice";
            hudson.model.User alice = realm.createAccount(username, username);

            { // a malicious cookie with expiration too far in the future should not work
                JenkinsRule.WebClient wc = j.createWebClient();

                // by default we have 14 days of validity,
                // here we increase artificially the duration of validity, that could be used to have permanent access
                long oneDay = TimeUnit.DAYS.toMillis(1);
                Cookie cookie = createRememberMeCookie(tokenService, oneDay, alice);
                wc.getCookieManager().addCookie(cookie);

                // the application should not use the cookie to connect
                assertUserConnected(wc, username);
            }

            { // a hand crafted cookie with regular expiration duration works
                JenkinsRule.WebClient wc = j.createWebClient();

                // by default we have 14 days of validity,
                // here we reduce a bit the expiration date to simulate an "old" cookie (regular usage)
                long minusFiveMinutes = TimeUnit.MINUTES.toMillis(-5);
                Cookie cookie = createRememberMeCookie(tokenService, minusFiveMinutes, alice);
                wc.getCookieManager().addCookie(cookie);

                // if we reactivate the remember me feature, it's ok
                assertUserConnected(wc, username);
            }
        } finally {
            TokenBasedRememberMeServices2.SKIP_TOO_FAR_EXPIRATION_DATE_CHECK = previousConfig;
        }
    }

    @Test
    @Issue("JENKINS-56243")
    public void rememberMeToken_shouldLoadUserDetailsOnlyOnce() throws Exception {
        j.jenkins.setDisableRememberMe(false);
        LoadUserCountingSecurityRealm realm = new LoadUserCountingSecurityRealm();
        realm.createAccount("alice");
        j.jenkins.setSecurityRealm(realm);
        User alice = User.getOrCreateByIdOrFullName("alice");
        realm.verifyInvocations(1);

        // first, start a session with a remember me token
        Cookie cookie = getRememberMeCookie(j.createWebClient().login("alice", "alice", true));
        // next, start a new session with that token
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getCookieManager().addCookie(cookie);
        // trigger remember me
        String sessionSeed = wc.executeOnServer(() -> Stapler.getCurrentRequest2().getSession(false).getAttribute(UserSeedProperty.USER_SESSION_SEED).toString());
        realm.verifyInvocations(1);
        String userSeed = alice.getProperty(UserSeedProperty.class).getSeed();

        assertEquals(userSeed, sessionSeed);

        // finally, ensure that loadUserByUsername2 is not being called anymore
        wc.goTo("");
        assertUserConnected(wc, "alice");
        realm.verifyInvocations(0);
    }

    private static class LoadUserCountingSecurityRealm extends InMemorySecurityRealm {
        // if this class wasn't serialized into config.xml, this could be replaced by @Spy from Mockito
        private int counter = 0;

        @Override
        public synchronized UserDetails loadUserByUsername2(String username) throws UsernameNotFoundException {
            ++counter;
            return super.loadUserByUsername2(username);
        }

        synchronized void verifyInvocations(int count) {
            assertEquals(count, counter);
            counter = 0;
        }
    }

    private Cookie createRememberMeCookie(TokenBasedRememberMeServices2 tokenService, long deltaDuration, hudson.model.User user) throws Exception {
        long tokenValiditySeconds = tokenService.getTokenValiditySeconds();
        long expiryTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(tokenValiditySeconds);

        // the hack
        expiryTime += deltaDuration;

        HudsonPrivateSecurityRealm.Details details = user.getProperty(HudsonPrivateSecurityRealm.Details.class);
        String signatureValue = tokenService.makeTokenSignature(expiryTime, details.getUsername());
        String tokenValue = user.getId() + ":" + expiryTime + ":" + signatureValue;
        String tokenValueBase64 = Base64.getEncoder().encodeToString(tokenValue.getBytes(StandardCharsets.UTF_8));
        return new Cookie(j.getURL().getHost(), tokenService.getCookieName(), tokenValueBase64);
    }

    private void assertUserConnected(JenkinsRule.WebClient wc, String expectedUsername) throws Exception {
        XmlPage page = (XmlPage) wc.goTo("whoAmI/api/xml", "application/xml");
        assertThat(page, hasXPath("//name", is(expectedUsername)));
    }

    private void assertUserNotConnected(JenkinsRule.WebClient wc, String notExpectedUsername) throws Exception {
        XmlPage page = (XmlPage) wc.goTo("whoAmI/api/xml", "application/xml");
        assertThat(page, hasXPath("//name", not(is(notExpectedUsername))));
    }

    @Test
    @Issue("SECURITY-996")
    public void rememberMeToken_shouldNotBeRead_ifOptionIsDisabled() throws Exception {
        j.jenkins.setDisableRememberMe(false);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        Cookie rememberMeCookie = null;
        {
            JenkinsRule.WebClient wc = j.createWebClient();
            wc.login("alice", "alice", true);


            // we should see a remember me cookie
            rememberMeCookie = getRememberMeCookie(wc);
            assertNotNull(rememberMeCookie);
            assertThat(rememberMeCookie.getValue(), not(is(emptyString())));
        }

        j.jenkins.setDisableRememberMe(true);
        {
            JenkinsRule.WebClient wc = j.createWebClient();

            wc.getCookieManager().addCookie(rememberMeCookie);

            // the application should not use the cookie to connect
            XmlPage page = (XmlPage) wc.goTo("whoAmI/api/xml", "application/xml");
            assertThat(page, hasXPath("//name", not(is("alice"))));
        }

        j.jenkins.setDisableRememberMe(false);
        {
            JenkinsRule.WebClient wc = j.createWebClient();

            wc.getCookieManager().addCookie(rememberMeCookie);

            // if we reactivate the remember me feature, it's ok
            XmlPage page = (XmlPage) wc.goTo("whoAmI/api/xml", "application/xml");
            assertThat(page, hasXPath("//name", is("alice")));
        }
    }
}
