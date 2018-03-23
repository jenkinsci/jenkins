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
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import hudson.ExtensionList;
import hudson.model.User;
import hudson.remoting.Base64;
import static hudson.security.HudsonPrivateSecurityRealm.CLASSIC;
import static hudson.security.HudsonPrivateSecurityRealm.PASSWORD_ENCODER;
import hudson.security.pages.SignupPage;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import jenkins.security.ApiTokenProperty;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.Assert.*;

import jenkins.security.apitoken.ApiTokenPropertyConfiguration;
import jenkins.security.SecurityListener;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.WithoutJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

import javax.annotation.Nonnull;

public class HudsonPrivateSecurityRealmTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private SpySecurityListenerImpl spySecurityListener;

    @Before
    public void linkExtension() throws Exception {
        spySecurityListener = ExtensionList.lookup(SecurityListener.class).get(SpySecurityListenerImpl.class);
    }

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
            fail();
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
        ApiTokenPropertyConfiguration.get().setTokenGenerationOnCreationEnabled(true);
        
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

    @Issue("JENKINS-48383")
    @Test
    public void selfRegistrationTriggerLoggedIn() throws Exception {
        HudsonPrivateSecurityRealm securityRealm = new HudsonPrivateSecurityRealm(true, false, null);
        j.jenkins.setSecurityRealm(securityRealm);
        j.jenkins.setCrumbIssuer(null);

        assertTrue(spySecurityListener.loggedInUsernames.isEmpty());

        createFirstAccount("admin");
        assertTrue(spySecurityListener.loggedInUsernames.get(0).equals("admin"));

        createAccountByAdmin("alice");
        // no new event in such case
        assertTrue(spySecurityListener.loggedInUsernames.isEmpty());

        selfRegistration("bob");
        assertTrue(spySecurityListener.loggedInUsernames.get(0).equals("bob"));
    }

    private void createFirstAccount(String login) throws Exception {
        assertNull(User.getById(login, false));

        JenkinsRule.WebClient wc = j.createWebClient();

        HudsonPrivateSecurityRealm.SignupInfo info = new HudsonPrivateSecurityRealm.SignupInfo();
        info.username = login;
        info.password1 = login;
        info.password2 = login;
        info.fullname = StringUtils.capitalize(login);

        WebRequest request = new WebRequest(new URL(wc.getContextPath() + "securityRealm/createFirstAccount"), HttpMethod.POST);
        request.setRequestParameters(Arrays.asList(
                new NameValuePair("username", login),
                new NameValuePair("password1", login),
                new NameValuePair("password2", login),
                new NameValuePair("fullname", StringUtils.capitalize(login)),
                new NameValuePair("email", login + "@" + login + ".com")
        ));

        HtmlPage p = wc.getPage(request);
        assertEquals(200, p.getWebResponse().getStatusCode());
        assertTrue(p.getDocumentElement().getElementsByAttribute("div", "class", "error").isEmpty());

        assertNotNull(User.getById(login, false));
    }

    private void createAccountByAdmin(String login) throws Exception {
        // user should not exist before
        assertNull(User.getById(login, false));

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("admin");

        spySecurityListener.loggedInUsernames.clear();

        HtmlPage page = wc.goTo("securityRealm/addUser");
        HtmlForm form = page.getForms().stream()
                .filter(htmlForm -> htmlForm.getActionAttribute().endsWith("/securityRealm/createAccountByAdmin"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Form must be present"));

        form.getInputByName("username").setValueAttribute(login);
        form.getInputByName("password1").setValueAttribute(login);
        form.getInputByName("password2").setValueAttribute(login);
        form.getInputByName("fullname").setValueAttribute(StringUtils.capitalize(login));
        form.getInputByName("email").setValueAttribute(login + "@" + login + ".com");

        HtmlPage p = j.submit(form);
        assertEquals(200, p.getWebResponse().getStatusCode());
        assertTrue(p.getDocumentElement().getElementsByAttribute("div", "class", "error").isEmpty());

        assertNotNull(User.getById(login, false));
    }

    private void selfRegistration(String login) throws Exception {
        // user should not exist before
        assertNull(User.getById(login, false));

        JenkinsRule.WebClient wc = j.createWebClient();
        SignupPage signup = new SignupPage(wc.goTo("signup"));
        signup.enterUsername(login);
        signup.enterPassword(login);
        signup.enterFullName(StringUtils.capitalize(login));
        signup.enterEmail(login + "@" + login + ".com");

        HtmlPage p = signup.submit(j);
        assertEquals(200, p.getWebResponse().getStatusCode());
        assertTrue(p.getDocumentElement().getElementsByAttribute("div", "class", "error").isEmpty());

        assertNotNull(User.getById(login, false));
    }

    @TestExtension
    public static class SpySecurityListenerImpl extends SecurityListener {
        private List<String> loggedInUsernames = new ArrayList<>();

        @Override
        protected void loggedIn(@Nonnull String username) {
            loggedInUsernames.add(username);
        }
    }
}
