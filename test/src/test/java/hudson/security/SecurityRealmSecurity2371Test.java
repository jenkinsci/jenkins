/*
 * The MIT License
 *
 * Copyright (c) 2021 CloudBees, Inc.
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

package hudson.security;

import static org.junit.Assert.assertThrows;

import java.util.Arrays;
import java.util.List;
import jenkins.model.Jenkins;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.util.Cookie;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

/**
 * Split from {@link SecurityRealmTest} because this is parameterized.
 */
@RunWith(Parameterized.class)
public class SecurityRealmSecurity2371Test {

    public static final String SESSION_COOKIE_NAME = "JSESSIONID";
    public static final String USERNAME = "alice";

    private final Integer mode;

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Parameterized.Parameters
    public static List<Integer> modes() {
        return Arrays.asList(null, 1, 2);
    }

    public SecurityRealmSecurity2371Test(Integer mode) {
        this.mode = mode;
    }

    @Test
    public void testSessionChangeOnLogin() throws Exception {
        if (mode != null) {
            System.setProperty(SecurityRealm.class.getName() + ".sessionFixationProtectionMode", String.valueOf(mode));
        }
        try {
            j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
            j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().toEveryone().grant(Jenkins.ADMINISTER).everywhere().to(USERNAME));
            final JenkinsRule.WebClient webClient = j.createWebClient();
            webClient.goTo("");
            assertThrows("anonymous session should not be able to go to /manage", FailingHttpStatusCodeException.class, () -> webClient.goTo("manage"));
            final Cookie anonymousCookie = webClient.getCookieManager().getCookie(SESSION_COOKIE_NAME); // dynamic cookie names are only set when run through Winstone
            webClient.login(USERNAME);
            webClient.goTo("");
            final Cookie aliceCookie = webClient.getCookieManager().getCookie(SESSION_COOKIE_NAME);

            // Confirm the session cookie changed
            // We cannot just call #assertNotEquals(Cookie, Cookie) because it doesn't actually look at #getValue()
            Assert.assertNotEquals(anonymousCookie.getValue(), aliceCookie.getValue());

            // Now ensure the old session was actually invalidated / is not associated with the new auth
            webClient.getCookieManager().clearCookies();
            webClient.getCookieManager().addCookie(anonymousCookie);
            assertThrows("anonymous session should not be able to go to /manage", FailingHttpStatusCodeException.class, () -> webClient.goTo("manage"));
        } finally {
            System.clearProperty(SecurityRealm.class.getName() + ".sessionFixationProtectionMode");
        }
    }

    /**
     * Explicitly disable
     */
    @Test
    public void optOut() throws Exception {
        System.setProperty(SecurityRealm.class.getName() + ".sessionFixationProtectionMode", String.valueOf(0));
        try {
            j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
            j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().toEveryone().grant(Jenkins.ADMINISTER).everywhere().to(USERNAME));
            final JenkinsRule.WebClient webClient = j.createWebClient();
            webClient.goTo("");

            final Cookie anonymousCookie = webClient.getCookieManager().getCookie(SESSION_COOKIE_NAME); // dynamic cookie names are only set when run through Winstone
            webClient.login(USERNAME);
            webClient.goTo("");
            final Cookie aliceCookie = webClient.getCookieManager().getCookie(SESSION_COOKIE_NAME);

            // Confirm the session cookie did not change
            Assert.assertEquals(anonymousCookie.getValue(), aliceCookie.getValue());
        } finally {
            System.clearProperty(SecurityRealm.class.getName() + ".sessionFixationProtectionMode");
        }
    }
}
