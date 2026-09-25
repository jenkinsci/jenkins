/*
 * The MIT License
 *
 * Copyright (c) 2018 CloudBees, Inc.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.model.User;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.htmlunit.ElementNotFoundException;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.xml.XmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import test.security.realm.InMemorySecurityRealm;

@WithJenkins
class UserSeedPropertyTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("SECURITY-901")
    void userCreation_implies_userSeedCreation() {
        User alice = User.getById("alice", true);
        assertNotNull(alice);
        UserSeedProperty userSeed = alice.getProperty(UserSeedProperty.class);
        assertNotNull(userSeed);
        assertNotNull(userSeed.getSeed());
    }

    @Test
    @Issue("SECURITY-901")
    void userSeedRenewal_changeTheSeed() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        Set<String> seeds = new HashSet<>();

        User alice = User.getById("alice", true);

        UserSeedProperty userSeed = alice.getProperty(UserSeedProperty.class);
        seeds.add(userSeed.getSeed());

        int times = 10;
        for (int i = 1; i < times; i++) {
            requestRenewSeedForUser(alice);
            userSeed = alice.getProperty(UserSeedProperty.class);
            seeds.add(userSeed.getSeed());
        }

        assertThat(seeds.size(), equalTo(times));
        assertFalse(seeds.contains(""));
        assertFalse(seeds.contains(null));
    }

    @Test
    @Issue("SECURITY-901")
    void initialUserSeedIsAlwaysDifferent() throws Exception {
        Set<String> seeds = new HashSet<>();

        int times = 10;
        for (int i = 0; i < times; i++) {
            User alice = User.getById("alice", true);
            UserSeedProperty userSeed = alice.getProperty(UserSeedProperty.class);
            seeds.add(userSeed.getSeed());
            alice.delete();
        }

        assertThat(seeds.size(), equalTo(times));
        assertFalse(seeds.contains(""));
        assertFalse(seeds.contains(null));
    }

    @Test
    @Issue("SECURITY-901")
    void differentUserHaveDifferentInitialSeeds() {
        Set<String> seeds = new HashSet<>();

        List<String> userIds = Arrays.asList("Alice", "Bob", "Charles", "Derek", "Edward");
        userIds.forEach(userId -> {
            User user = User.getById(userId, true);
            UserSeedProperty userSeed = user.getProperty(UserSeedProperty.class);
            seeds.add(userSeed.getSeed());
        });

        assertThat(seeds.size(), equalTo(userIds.size()));
        assertFalse(seeds.contains(""));
        assertFalse(seeds.contains(null));
    }

    @Test
    @Issue("SECURITY-901")
    void userCreatedInThirdPartyRealm_cannotReconnect_afterSessionInvalidation_andRealmDeletion() throws Exception {
        InMemorySecurityRealm realm = new InMemorySecurityRealm();
        j.jenkins.setSecurityRealm(realm);
        j.jenkins.setCrumbIssuer(null);

        String ALICE = "alice";

        realm.createAccount(ALICE);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(ALICE);

        User alice = User.getById(ALICE, false);
        assertNotNull(alice);
        UserSeedProperty userSeed = alice.getProperty(UserSeedProperty.class);
        assertNotNull(userSeed);

        assertUserConnected(wc, ALICE);

        realm.deleteAccount(ALICE);

        // even after the security realm deleted the user, they can still connect, until session invalidation
        assertUserConnected(wc, ALICE);

        requestRenewSeedForUser(alice);

        assertUserNotConnected(wc, ALICE);
        assertUserConnected(wc, "anonymous");

        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> wc.login(ALICE), "Alice does not exist any longer and so should not be able to login");
        assertEquals(401, e.getStatusCode());
    }

    @Test
    @Issue("SECURITY-901")
    void userAfterBeingDeletedInThirdPartyRealm_canStillUseTheirSession_withDisabledSeed() throws Exception {
        boolean currentStatus = UserSeedProperty.DISABLE_USER_SEED;
        try {
            UserSeedProperty.DISABLE_USER_SEED = true;

            InMemorySecurityRealm realm = new InMemorySecurityRealm();
            j.jenkins.setSecurityRealm(realm);
            j.jenkins.setCrumbIssuer(null);

            String ALICE = "alice";

            realm.createAccount(ALICE);

            JenkinsRule.WebClient wc = j.createWebClient();
            wc.login(ALICE);

            User alice = User.getById(ALICE, false);
            assertNotNull(alice);
            UserSeedProperty userSeed = alice.getProperty(UserSeedProperty.class);
            assertNotNull(userSeed);

            assertUserConnected(wc, ALICE);

            realm.deleteAccount(ALICE);

            // even after the security realm deleted the user, they can still connect, until session invalidation
            assertUserConnected(wc, ALICE);

            // as the feature is disabled, we cannot renew the seed
            assertThrows(FailingHttpStatusCodeException.class, () -> requestRenewSeedForUser(alice), "The feature should be disabled");

            // failed attempt to renew the seed does not have any effect
            assertUserConnected(wc, ALICE);

            UserSeedProperty userSeedProperty = alice.getProperty(UserSeedProperty.class);
            userSeedProperty.renewSeed();

            // failed attempt to renew the seed does not have any effect
            assertUserConnected(wc, ALICE);

            JenkinsRule.WebClient wc2 = j.createWebClient();
            FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> wc2.login(ALICE), "Alice is not longer backed by security realm");
            assertEquals(401, e.getStatusCode());
        } finally {
            UserSeedProperty.DISABLE_USER_SEED = currentStatus;
        }
    }

    @Test
    @Issue("SECURITY-901")
    void userCreatedInThirdPartyRealm_canReconnect_afterSessionInvalidation() throws Exception {
        InMemorySecurityRealm realm = new InMemorySecurityRealm();
        j.jenkins.setSecurityRealm(realm);
        j.jenkins.setCrumbIssuer(null);

        String ALICE = "alice";

        realm.createAccount(ALICE);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(ALICE);

        User alice = User.getById(ALICE, false);
        assertNotNull(alice);
        UserSeedProperty userSeed = alice.getProperty(UserSeedProperty.class);
        assertNotNull(userSeed);

        assertUserConnected(wc, ALICE);

        requestRenewSeedForUser(alice);

        assertUserNotConnected(wc, ALICE);
        assertUserConnected(wc, "anonymous");

        wc.login(ALICE);
        assertUserConnected(wc, ALICE);
    }

    @Test
    void userSeedSection_isCorrectlyDisplayed() throws Exception {
        InMemorySecurityRealm realm = new InMemorySecurityRealm();
        j.jenkins.setSecurityRealm(realm);
        j.jenkins.setCrumbIssuer(null);

        String ALICE = "alice";

        realm.createAccount(ALICE);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login(ALICE);

        User alice = User.getById(ALICE, false);
        assertNotNull(alice);

        HtmlPage htmlPage = wc.goTo(alice.getUrl() + "/security/");
        htmlPage.getDocumentElement().getOneHtmlElementByAttribute("div", "class", "user-seed-panel");
    }

    @Test
    void userSeedSection_isCorrectlyHidden_withSpecificSetting() throws Exception {
        boolean currentStatus = UserSeedProperty.HIDE_USER_SEED_SECTION;
        try {
            UserSeedProperty.HIDE_USER_SEED_SECTION = true;

            InMemorySecurityRealm realm = new InMemorySecurityRealm();
            j.jenkins.setSecurityRealm(realm);
            j.jenkins.setCrumbIssuer(null);

            String ALICE = "alice";

            realm.createAccount(ALICE);

            JenkinsRule.WebClient wc = j.createWebClient();
            wc.login(ALICE);

            User alice = User.getById(ALICE, false);
            assertNotNull(alice);

            HtmlPage htmlPage = wc.goTo(alice.getUrl() + "/security/");
            assertThrows(ElementNotFoundException.class, () -> htmlPage.getDocumentElement().getOneHtmlElementByAttribute("div", "class", "user-seed-panel"), "Seed section should not be displayed");
        }
        finally {
            UserSeedProperty.HIDE_USER_SEED_SECTION = currentStatus;
        }
    }

    private void assertUserConnected(JenkinsRule.WebClient wc, String expectedUsername) throws Exception {
        XmlPage page = (XmlPage) wc.goTo("whoAmI/api/xml", "application/xml");
        assertThat(page, hasXPath("//name", is(expectedUsername)));
    }

    private void assertUserNotConnected(JenkinsRule.WebClient wc, String notExpectedUsername) throws Exception {
        XmlPage page = (XmlPage) wc.goTo("whoAmI/api/xml", "application/xml");
        assertThat(page, hasXPath("//name", not(is(notExpectedUsername))));
    }

    private void requestRenewSeedForUser(User user) throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest request = new WebRequest(new URI(j.jenkins.getRootUrl() + user.getUrl() + "/descriptorByName/" + UserSeedProperty.class.getName() + "/renewSessionSeed/").toURL(), HttpMethod.POST);
        wc.getPage(request);
    }
}
