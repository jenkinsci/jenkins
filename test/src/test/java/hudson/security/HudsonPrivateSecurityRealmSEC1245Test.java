/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc. and others
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

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import hudson.model.User;
import jenkins.security.seed.UserSeedProperty;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.Assert.assertThat;

@For({UserSeedProperty.class, HudsonPrivateSecurityRealm.class})
public class HudsonPrivateSecurityRealmSEC1245Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("SECURITY-1245")
    public void changingPassword_mustInvalidateAllSessions() throws Exception {
        User alice = prepareRealmAndAlice();
        String initialSeed = alice.getProperty(UserSeedProperty.class).getSeed();

        WebClient wc = j.createWebClient();
        WebClient wc_anotherTab = j.createWebClient();

        wc.login(alice.getId());
        assertUserConnected(wc, alice.getId());

        wc_anotherTab.login(alice.getId());
        assertUserConnected(wc_anotherTab, alice.getId());

        HtmlPage configurePage = wc.goTo(alice.getUrl() + "/configure");
        HtmlPasswordInput password1 = configurePage.getElementByName("user.password");
        HtmlPasswordInput password2 = configurePage.getElementByName("user.password2");

        password1.setText("alice2");
        password2.setText("alice2");

        HtmlForm form = configurePage.getFormByName("config");
        j.submit(form);

        assertUserNotConnected(wc, alice.getId());
        assertUserNotConnected(wc_anotherTab, alice.getId());

        String seedAfter = alice.getProperty(UserSeedProperty.class).getSeed();
        assertThat(seedAfter, not(is(initialSeed)));
    }

    @Test
    @Issue("SECURITY-1245")
    public void notChangingPassword_hasNoImpactOnSeed() throws Exception {
        User alice = prepareRealmAndAlice();
        String initialSeed = alice.getProperty(UserSeedProperty.class).getSeed();

        WebClient wc = j.createWebClient();
        WebClient wc_anotherTab = j.createWebClient();

        wc.login(alice.getId());
        assertUserConnected(wc, alice.getId());

        wc_anotherTab.login(alice.getId());
        assertUserConnected(wc_anotherTab, alice.getId());

        HtmlPage configurePage = wc.goTo(alice.getUrl() + "/configure");
        // not changing password this time
        HtmlForm form = configurePage.getFormByName("config");
        j.submit(form);

        assertUserConnected(wc, alice.getId());
        assertUserConnected(wc_anotherTab, alice.getId());

        String seedAfter = alice.getProperty(UserSeedProperty.class).getSeed();
        assertThat(seedAfter, is(initialSeed));
    }

    @Test
    @Issue("SECURITY-1245")
    public void changingPassword_withSeedDisable_hasNoImpact() throws Exception {
        boolean previousConfig = UserSeedProperty.DISABLE_USER_SEED;
        try {
            UserSeedProperty.DISABLE_USER_SEED = true;

            User alice = prepareRealmAndAlice();

            WebClient wc = j.createWebClient();
            WebClient wc_anotherTab = j.createWebClient();

            wc.login(alice.getId());
            assertUserConnected(wc, alice.getId());

            wc_anotherTab.login(alice.getId());
            assertUserConnected(wc_anotherTab, alice.getId());

            HtmlPage configurePage = wc.goTo(alice.getUrl() + "/configure");
            HtmlPasswordInput password1 = configurePage.getElementByName("user.password");
            HtmlPasswordInput password2 = configurePage.getElementByName("user.password2");

            password1.setText("alice2");
            password2.setText("alice2");

            HtmlForm form = configurePage.getFormByName("config");
            j.submit(form);

            assertUserConnected(wc, alice.getId());
            assertUserConnected(wc_anotherTab, alice.getId());
        } finally {
            UserSeedProperty.DISABLE_USER_SEED = previousConfig;
        }
    }
    
    private User prepareRealmAndAlice() throws Exception {
        j.jenkins.setDisableRememberMe(false);
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(securityRealm);

        return securityRealm.createAccount("alice", "alice");
    }

    private void assertUserConnected(JenkinsRule.WebClient wc, String expectedUsername) throws Exception {
        XmlPage page = (XmlPage) wc.goTo("whoAmI/api/xml", "application/xml");
        assertThat(page, hasXPath("//name", is(expectedUsername)));
    }

    private void assertUserNotConnected(JenkinsRule.WebClient wc, String notExpectedUsername) throws Exception {
        XmlPage page = (XmlPage) wc.goTo("whoAmI/api/xml", "application/xml");
        assertThat(page, hasXPath("//name", not(is(notExpectedUsername))));
    }
}
