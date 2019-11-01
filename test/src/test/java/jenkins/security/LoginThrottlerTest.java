/*
 * The MIT License
 *
 * Copyright (c) 2019, CloudBees, Inc.
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
package jenkins.security;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import hudson.model.User;
import jenkins.model.Jenkins;
import jenkins.security.apitoken.ApiTokenStore;
import jenkins.security.auth.LoginThrottler;
import jenkins.util.TimeProvider;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import javax.servlet.http.HttpServletResponse;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class LoginThrottlerTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private ManualTimeProvider time;
    private LoginThrottler loginThrottler;

    @Before
    public void setupLinks() {
        time = Jenkins.get().getExtensionList(TimeProvider.class).get(ManualTimeProvider.class);
        loginThrottler = LoginThrottler.get();
    }

    @Test
    public void regularUsage() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User foo = User.getById("foo", true);

        assertThat(loginThrottler.getUsersWithFailures(), empty());
        assertThat(loginThrottler.getUsersCurrentlyLocked(), empty());

        loginSuccess(foo);

        assertThat(loginThrottler.getUsersWithFailures(), empty());
        assertThat(loginThrottler.getUsersCurrentlyLocked(), empty());
    }

    @Test
    public void fewFailuresNoImpact() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User foo = User.getById("foo", true);

        loginFailure(foo);

        assertThat(loginThrottler.getUsersWithFailures(), contains(foo.getId()));
        assertThat(loginThrottler.getUsersCurrentlyLocked(), empty());

        time.moveForwardMs(10);

        loginFailure(foo);
        assertThat(loginThrottler.getUsersWithFailures(), contains(foo.getId()));
        assertThat(loginThrottler.getUsersCurrentlyLocked(), empty());

        time.moveForwardMs(10);

        // correct login
        loginSuccess(foo);
        assertThat(loginThrottler.getUsersWithFailures(), empty());
        assertThat(loginThrottler.getUsersCurrentlyLocked(), empty());
    }

    @Test
    public void multipleFailures_timeoutTheAccount() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User foo = User.getById("foo", true);

        loginFailure(foo);
        time.moveForwardMs(10);
        loginFailure(foo);
        time.moveForwardMs(10);
        loginFailure(foo);

        assertThat(loginThrottler.getUsersWithFailures(), contains(foo.getId()));
        assertThat(loginThrottler.getUsersCurrentlyLocked(), contains(foo.getId()));

        time.moveForwardMs(10);

        // correct login but refused
        loginFailureUsingCorrectPwd(foo);

        // the user needs to wait up to 30 seconds before being able to login again
        time.moveForwardSeconds(30);
        loginSuccess(foo);

        assertThat(loginThrottler.getUsersWithFailures(), empty());
        assertThat(loginThrottler.getUsersCurrentlyLocked(), empty());
    }

    @Test
    public void oneAccountLocked_doesNotImpactOther() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User foo = User.getById("foo", true);
        User bar = User.getById("bar", true);

        loginFailure(foo);
        time.moveForwardMs(10);
        loginFailure(foo);
        time.moveForwardMs(10);
        loginFailure(foo);
        time.moveForwardMs(10);

        // does not prevent other user to connect
        loginSuccess(bar);
    }

    @Test
    public void successThenFailure() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User foo = User.getById("foo", true);

        loginSuccess(foo);
        time.moveForwardMs(10);

        loginFailure(foo);
        time.moveForwardMs(10);
        loginFailure(foo);
        time.moveForwardMs(10);
        loginFailure(foo);
        time.moveForwardMs(10);

        // correct login but refused
        loginFailureUsingCorrectPwd(foo);

        // the user needs to wait up to 30 seconds before being able to login again
        time.moveForwardSeconds(30);
        loginSuccess(foo);

        time.moveForwardMs(10);
        loginFailure(foo);
        time.moveForwardMs(10);
        loginFailure(foo);
        time.moveForwardMs(10);

        assertThat(loginThrottler.getUsersWithFailures(), contains(foo.getId()));
        assertThat(loginThrottler.getUsersCurrentlyLocked(), empty());

        // reset counter
        loginSuccess(foo);
        assertThat(loginThrottler.getUsersWithFailures(), empty());
        assertThat(loginThrottler.getUsersCurrentlyLocked(), empty());

        time.moveForwardMs(10);
        loginFailure(foo);
        time.moveForwardMs(10);
        loginFailure(foo);
        time.moveForwardMs(10);
        loginFailure(foo);
        time.moveForwardMs(10);

        // correct login but refused
        loginFailureUsingCorrectPwd(foo);

        // the user needs to wait up to 30 seconds before being able to login again
        time.moveForwardSeconds(30);
        loginSuccess(foo);

        assertThat(loginThrottler.getUsersWithFailures(), empty());
        assertThat(loginThrottler.getUsersCurrentlyLocked(), empty());
    }

    @Test
    public void longTimeBetweenFailures_noTimeout() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User foo = User.getById("foo", true);

        loginFailure(foo);
        time.moveForwardSeconds(45);
        loginFailure(foo);
        time.moveForwardSeconds(45);
        loginFailure(foo);
        time.moveForwardSeconds(45);
        loginFailure(foo);
        time.moveForwardMs(10);

        // no impact
        loginSuccess(foo);
    }

    @Test
    public void evenIfTimedout_apiTokenShouldStillWork() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User foo = User.getById("foo", true);
        ApiTokenStore.TokenUuidAndPlainValue tokenUuidAndPlainValue = foo.getProperty(ApiTokenProperty.class).getTokenStore().generateNewToken("test-token");
        foo.save();

        loginFailure(foo);
        time.moveForwardMs(10);
        loginFailure(foo);
        time.moveForwardMs(10);
        loginFailure(foo);

        assertThat(loginThrottler.getUsersCurrentlyLocked(), contains(foo.getId()));
        assertThat(loginThrottler.getUsersWithFailures(), contains(foo.getId()));

        j.createWebClient()
                .withBasicCredentials(foo.getId(), tokenUuidAndPlainValue.plainValue)
                .goTo("");
    }

    @Test
    public void basicAuthAlsoTriggerTheFailures() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User foo = User.getById("foo", true);

        basicLoginFailure(foo);
        time.moveForwardMs(10);
        basicLoginFailure(foo);
        time.moveForwardMs(10);
        basicLoginFailure(foo);

        assertThat(loginThrottler.getUsersCurrentlyLocked(), contains(foo.getId()));
        assertThat(loginThrottler.getUsersWithFailures(), contains(foo.getId()));

        time.moveForwardMs(10);
        basicLoginFailureUsingCorrectPwd(foo);

        time.moveForwardSeconds(30);
        basicLoginSuccess(foo);

        assertThat(loginThrottler.getUsersCurrentlyLocked(), empty());
        assertThat(loginThrottler.getUsersWithFailures(), empty());
    }

    @Test
    public void basicAuthWithMixOfApiAndPwd() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        User foo = User.getById("foo", true);

        basicLoginFailure(foo);
        time.moveForwardMs(10);
        loginFailure(foo);
        time.moveForwardMs(10);
        basicLoginFailure(foo);

        assertThat(loginThrottler.getUsersCurrentlyLocked(), contains(foo.getId()));
        assertThat(loginThrottler.getUsersWithFailures(), contains(foo.getId()));

        time.moveForwardMs(10);
        basicLoginFailureUsingCorrectPwd(foo);

        ApiTokenStore.TokenUuidAndPlainValue tokenUuidAndPlainValue = foo.getProperty(ApiTokenProperty.class).getTokenStore().generateNewToken("test-token");
        foo.save();

        time.moveForwardMs(10);
        j.createWebClient()
                .withBasicCredentials(foo.getId(), tokenUuidAndPlainValue.plainValue)
                .goTo("");

        // API Token success does not reset the timeout
        assertThat(loginThrottler.getUsersCurrentlyLocked(), contains(foo.getId()));
        assertThat(loginThrottler.getUsersWithFailures(), contains(foo.getId()));

        time.moveForwardMs(10);
        loginFailureUsingCorrectPwd(foo);

        time.moveForwardSeconds(30);
        basicLoginSuccess(foo);

        assertThat(loginThrottler.getUsersCurrentlyLocked(), empty());
        assertThat(loginThrottler.getUsersWithFailures(), empty());
        loginSuccess(foo);
    }

    private void loginSuccess(User user) throws Exception {
        j.createWebClient().login(user.getId());
    }

    private void loginFailure(User user) throws Exception {
        _loginFailure(user.getId(), "invalidPwd");
    }

    private void loginFailureUsingCorrectPwd(User user) throws Exception {
        _loginFailure(user.getId(), user.getId());
    }

    private void _loginFailure(String login, String pwd) throws Exception {
        try {
            j.createWebClient().login(login, pwd);
            fail("The login should have been a failure");
        } catch (FailingHttpStatusCodeException e) {
            assertThat(e.getStatusCode(), equalTo(HttpServletResponse.SC_UNAUTHORIZED));
        }
    }

    private void basicLoginSuccess(User user) throws Exception {
        j.createWebClient()
                .withBasicCredentials(user.getId())
                .goTo("");
    }

    private void basicLoginFailure(User user) throws Exception {
        _basicLoginFailure(user.getId(), "invalidPwd");
    }

    private void basicLoginFailureUsingCorrectPwd(User user) throws Exception {
        _basicLoginFailure(user.getId(), user.getId());
    }

    private void _basicLoginFailure(String login, String password) throws Exception {
        try {
            j.createWebClient()
                    .withBasicCredentials(login, password)
                    .goTo("");
            fail("The login should have been a failure");
        } catch (FailingHttpStatusCodeException e) {
            assertThat(e.getStatusCode(), equalTo(HttpServletResponse.SC_UNAUTHORIZED));
        }
    }

    @TestExtension
    public static class ManualTimeProvider implements TimeProvider {
        private long currentTimeMs = 0;

        @Override
        public long getCurrentTimeMillis() {
            return currentTimeMs;
        }

        public void moveForwardMs(long valueMs) {
            currentTimeMs += valueMs;
        }

        public void moveForwardSeconds(long valueSeconds) {
            currentTimeMs += valueSeconds * 1000;
        }
    }
}
