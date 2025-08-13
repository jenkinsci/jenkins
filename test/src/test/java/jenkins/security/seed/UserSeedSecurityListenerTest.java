/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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

package jenkins.security.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.Stapler;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@WithJenkins
class UserSeedSecurityListenerTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("JENKINS-59107")
    void authenticateSecondaryUserWhileLoggedIn_shouldNotOverwritePrimaryUserSessionSeed() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        AuthenticationManager authenticationManager = j.jenkins.getSecurityRealm().getSecurityComponents().manager2;
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("alice").executeOnServer(() -> {
            HttpSession session = Stapler.getCurrentRequest2().getSession();
            String existingSeed = (String) session.getAttribute(UserSeedProperty.USER_SESSION_SEED);
            assertNotNull(existingSeed);
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken("bob", "bob"));
            String updatedSeed = (String) session.getAttribute(UserSeedProperty.USER_SESSION_SEED);
            assertEquals(existingSeed, updatedSeed);
            return null;
        });
    }
}
