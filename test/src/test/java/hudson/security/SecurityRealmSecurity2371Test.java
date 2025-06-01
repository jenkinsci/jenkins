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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import jenkins.model.Jenkins;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.util.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Split from {@link SecurityRealmTest} because this is parameterized.
 */
@WithJenkins
class SecurityRealmSecurity2371Test {

    public static final String SESSION_COOKIE_NAME = "JSESSIONID";
    public static final String USERNAME = "alice";

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    static List<Integer> modes() {
        return Arrays.asList(null, 1, 2);
    }

    @ParameterizedTest
    @MethodSource("modes")
    void testSessionChangeOnLogin(Integer mode) throws Exception {
        if (mode != null) {
            System.setProperty(SecurityRealm.class.getName() + ".sessionFixationProtectionMode", String.valueOf(mode));
        }
        try {
            j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
            j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().toEveryone().grant(Jenkins.ADMINISTER).everywhere().to(USERNAME));
            final JenkinsRule.WebClient webClient = j.createWebClient();
            webClient.goTo("");
            assertThrows(FailingHttpStatusCodeException.class, () -> webClient.goTo("manage"), "anonymous session should not be able to go to /manage");
            final Cookie anonymousCookie = webClient.getCookieManager().getCookie(SESSION_COOKIE_NAME); // dynamic cookie names are only set when run through Winstone
            webClient.login(USERNAME);
            webClient.goTo("");
            final Cookie aliceCookie = webClient.getCookieManager().getCookie(SESSION_COOKIE_NAME);

            // Confirm the session cookie changed
            // We cannot just call #assertNotEquals(Cookie, Cookie) because it doesn't actually look at #getValue()
            assertNotEquals(anonymousCookie.getValue(), aliceCookie.getValue());

            // Now ensure the old session was actually invalidated / is not associated with the new auth
            webClient.getCookieManager().clearCookies();
            webClient.getCookieManager().addCookie(anonymousCookie);
            assertThrows(FailingHttpStatusCodeException.class, () -> webClient.goTo("manage"), "anonymous session should not be able to go to /manage");
        } finally {
            System.clearProperty(SecurityRealm.class.getName() + ".sessionFixationProtectionMode");
        }
    }

    /**
     * Explicitly disable
     */
    @Test
    void optOut() throws Exception {
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
            assertEquals(anonymousCookie.getValue(), aliceCookie.getValue());
        } finally {
            System.clearProperty(SecurityRealm.class.getName() + ".sessionFixationProtectionMode");
        }
    }
}
