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

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import hudson.model.User;
import hudson.remoting.Base64;
import static hudson.security.HudsonPrivateSecurityRealm.CLASSIC;
import static hudson.security.HudsonPrivateSecurityRealm.PASSWORD_ENCODER;
import hudson.security.pages.SignupPage;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import jenkins.security.ApiTokenProperty;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.WithoutJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

public class HudsonPrivateSecurityRealmTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Tests the data compatibility with Hudson before 1.283.
     * Starting 1.283, passwords are now stored hashed.
     */
    @Test
    @Issue("JENKINS-2381")
    @LocalData
    public void dataCompatibilityWith1_282() throws Exception {
        // make sure we can login with the same password as before
        WebClient wc = j.createWebClient().login("alice", "alice");

        try {
            // verify the sanity that the password is really used
            // this should fail
            j.createWebClient().login("bob", "bob");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(401,e.getStatusCode());
        }

        // resubmit the config and this should force the data store to be rewritten
        HtmlPage p = wc.goTo("user/alice/configure");
        j.submit(p.getFormByName("config"));

        // verify that we can still login
        j.createWebClient().login("alice", "alice");
    }

    @Test
    @WithoutJenkins
    public void hashCompatibility() {
        String old = CLASSIC.encodePassword("hello world", null);
        assertTrue(PASSWORD_ENCODER.isPasswordValid(old,"hello world",null));

        String secure = PASSWORD_ENCODER.encodePassword("hello world", null);
        assertTrue(PASSWORD_ENCODER.isPasswordValid(old,"hello world",null));

        assertFalse(secure.equals(old));
    }


    @Issue("SECURITY-243")
    @Test
    public void fullNameCollisionPassword() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        
        User u1 = securityRealm.createAccount("user1", "password1");
        u1.setFullName("User One");
        u1.save();

        User u2 = securityRealm.createAccount("user2", "password2");
        u2.setFullName("User Two");
        u2.save();

        WebClient wc1 = j.createWebClient();
        wc1.login("user1", "password1");

        WebClient wc2 = j.createWebClient();
        wc2.login("user2", "password2");

        
        // Check both users can use their token
        XmlPage w1 = (XmlPage) wc1.goTo("whoAmI/api/xml", "application/xml");
        assertThat(w1, hasXPath("//name", is("user1")));
        
        XmlPage w2 = (XmlPage) wc2.goTo("whoAmI/api/xml", "application/xml");
        assertThat(w2, hasXPath("//name", is("user2")));

        u1.setFullName("user2");
        u1.save();
        
        // check the tokens still work
        wc1 = j.createWebClient();
        wc1.login("user1", "password1");

        wc2 = j.createWebClient();
        // throws FailingHttpStatusCodeException on login failure
        wc2.login("user2", "password2");

        // belt and braces incase the failed login no longer throws exceptions.
        w1 = (XmlPage) wc1.goTo("whoAmI/api/xml", "application/xml");
        assertThat(w1, hasXPath("//name", is("user1")));
        
        w2 = (XmlPage) wc2.goTo("whoAmI/api/xml", "application/xml");
        assertThat(w2, hasXPath("//name", is("user2")));
    }

    @Issue("SECURITY-243")
    @Test
    public void fullNameCollisionToken() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        
        User u1 = securityRealm.createAccount("user1", "password1");
        u1.setFullName("User One");
        u1.save();
        String u1Token = u1.getProperty(ApiTokenProperty.class).getApiToken();

        User u2 = securityRealm.createAccount("user2", "password2");
        u2.setFullName("User Two");
        u2.save();
        String u2Token = u2.getProperty(ApiTokenProperty.class).getApiToken();

        WebClient wc1 = j.createWebClient();
        wc1.addRequestHeader("Authorization", basicHeader("user1", u1Token));
        //wc1.setCredentialsProvider(new FixedCredentialsProvider("user1", u1Token));

        WebClient wc2 = j.createWebClient();
        wc2.addRequestHeader("Authorization", basicHeader("user2", u2Token));
        //wc2.setCredentialsProvider(new FixedCredentialsProvider("user2", u1Token));
        
        // Check both users can use their token
        XmlPage w1 = (XmlPage) wc1.goTo("whoAmI/api/xml", "application/xml");
        assertThat(w1, hasXPath("//name", is("user1")));
        
        XmlPage w2 = (XmlPage) wc2.goTo("whoAmI/api/xml", "application/xml");
        assertThat(w2, hasXPath("//name", is("user2")));


        u1.setFullName("user2");
        u1.save();
        // check the tokens still work
        w1 = (XmlPage) wc1.goTo("whoAmI/api/xml", "application/xml");
        assertThat(w1, hasXPath("//name", is("user1")));
        
        w2 = (XmlPage) wc2.goTo("whoAmI/api/xml", "application/xml");
        assertThat(w2, hasXPath("//name", is("user2")));
    }


    private static final String basicHeader(String user, String pass) throws UnsupportedEncodingException {
        String str = user +':' + pass;
        String auth = Base64.encode(str.getBytes("US-ASCII"));
        String authHeader = "Basic " + auth;
        return authHeader;
    }

    @Test
    public void signup() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        JenkinsRule.WebClient wc = j.createWebClient();
        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername("alice");
        signup.enterPassword("alice");
        signup.enterFullName("Alice User");
        signup.enterEmail("alice@nowhere.com");
        HtmlPage success = signup.submit(j);
        assertThat(success.getElementById("main-panel").getTextContent(), containsString("Success"));
        assertThat(success.getAnchorByHref("/jenkins/user/alice").getTextContent(), containsString("Alice User"));


        assertEquals("Alice User", securityRealm.getUser("alice").getDisplayName());

    }

    @Issue("SECURITY-166")
    @Test
    public void anonymousCantSignup() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        JenkinsRule.WebClient wc = j.createWebClient();
        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername("anonymous");
        signup.enterFullName("Bob");
        signup.enterPassword("nothing");
        signup.enterEmail("noone@nowhere.com");
        signup = new SignupPage(signup.submit(j));
        signup.assertErrorContains("prohibited as a username");
        assertNull(User.get("anonymous", false, Collections.emptyMap()));
    }

    @Issue("SECURITY-166")
    @Test
    public void systemCantSignup() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        JenkinsRule.WebClient wc = j.createWebClient();
        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername("system");
        signup.enterFullName("Bob");
        signup.enterPassword("nothing");
        signup.enterEmail("noone@nowhere.com");
        signup = new SignupPage(signup.submit(j));
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
        j.jenkins.setSecurityRealm(securityRealm);
        JenkinsRule.WebClient wc = j.createWebClient();
        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername("unknown2");
        signup.enterPassword("unknown2");
        signup.enterFullName("unknown");
        signup.enterEmail("noone@nowhere.com");
        signup = new SignupPage(signup.submit(j));
        signup.assertErrorContains("prohibited as a full name");
        assertNull(User.get("unknown2",false, Collections.emptyMap()));
    }

}
