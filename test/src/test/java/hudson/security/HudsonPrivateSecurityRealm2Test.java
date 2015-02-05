/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc
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

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.User;
import hudson.security.pages.SignupPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;
import org.jvnet.hudson.test.Issue;

/**
 * Tests the signup page of {@link hudson.security.HudsonPrivateSecurityRealm}
 * TODO: Add to {@link hudson.security.HudsonPrivateSecurityRealmTest} going forward
 */
public class HudsonPrivateSecurityRealm2Test {


    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Test
    public void signup() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        rule.jenkins.setSecurityRealm(securityRealm);
        JenkinsRule.WebClient wc = rule.createWebClient();
        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername("alice");
        signup.enterPassword("alice");
        signup.enterFullName("Alice User");
        signup.enterEmail("alice@nowhere.com");
        HtmlPage success = signup.submit(rule);
        assertThat(success.getElementById("main-panel").getTextContent(), containsString("Success"));
        assertThat(success.getAnchorByHref("/jenkins/user/alice").getTextContent(), containsString("Alice User"));


        assertEquals("Alice User", securityRealm.getUser("alice").getDisplayName());

    }

    @Issue("SECURITY-166")
    @Test
    public void anonymousCantSignup() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        rule.jenkins.setSecurityRealm(securityRealm);
        JenkinsRule.WebClient wc = rule.createWebClient();
        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername("anonymous");
        signup.enterFullName("Bob");
        signup.enterPassword("nothing");
        signup.enterEmail("noone@nowhere.com");
        signup = new SignupPage(signup.submit(rule));
        signup.assertErrorContains("prohibited as a username");
        assertNull(User.get("anonymous", false, Collections.emptyMap()));
    }

    @Issue("SECURITY-166")
    @Test
    public void systemCantSignup() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        rule.jenkins.setSecurityRealm(securityRealm);
        JenkinsRule.WebClient wc = rule.createWebClient();
        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername("system");
        signup.enterFullName("Bob");
        signup.enterPassword("nothing");
        signup.enterEmail("noone@nowhere.com");
        signup = new SignupPage(signup.submit(rule));
        signup.assertErrorContains("prohibited as a username");
        assertNull(User.get("system",false, Collections.emptyMap()));
    }

    /**
     * We don't allow prohibited fullnames since this may encumber auditing.
     */
    @Issue("SECURITY-166")
    @Test
    public void fullNameOfUnknownCantSignup() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        rule.jenkins.setSecurityRealm(securityRealm);
        JenkinsRule.WebClient wc = rule.createWebClient();
        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername("unknown2");
        signup.enterPassword("unknown2");
        signup.enterFullName("unknown");
        signup.enterEmail("noone@nowhere.com");
        signup = new SignupPage(signup.submit(rule));
        signup.assertErrorContains("prohibited as a full name");
        assertNull(User.get("unknown2",false, Collections.emptyMap()));
    }
}
