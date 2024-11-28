/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import hudson.ExtensionComponent;
import hudson.model.User;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import jenkins.ExtensionFilter;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import jenkins.security.apitoken.ApiTokenPropertyConfiguration;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlTextInput;
import org.htmlunit.xml.XmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsSessionRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;

public class BasicHeaderApiTokenAuthenticatorTest {
    @Rule
    public JenkinsSessionRule sessions = new JenkinsSessionRule();

    @Test
    @Issue("SECURITY-896")
    public void legacyToken_regularCase() throws Throwable {
        AtomicReference<String> token = new AtomicReference<>();
        sessions.then(j -> {
                enableLegacyTokenGenerationOnUserCreation();
                configureSecurity(j);

                {
                    JenkinsRule.WebClient wc = j.createWebClient();
                    // default SecurityListener will save the user when adding the LastGrantedAuthoritiesProperty
                    // and so the user is persisted
                    wc.login("user1");
                    HtmlPage page = wc.goTo("user/user1/security/");
                    String tokenValue = ((HtmlTextInput) page.getDocumentElement().querySelector("#apiToken")).getText();
                    token.set(tokenValue);
                }
        });
        sessions.then(j -> {
                User user = User.getById("user1", false);
                assertNotNull(user);

                JenkinsRule.WebClient wc = j.createWebClient();
                wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

                { // for invalid token, no effect
                    WebRequest request = new WebRequest(new URI(j.jenkins.getRootUrl() + "whoAmI/api/xml").toURL());
                    request.setAdditionalHeader("Authorization", base64("user1", "invalid-token"));
                    assertThat(wc.getPage(request).getWebResponse().getStatusCode(), equalTo(401));
                }
                { // for invalid user, no effect
                    WebRequest request = new WebRequest(new URI(j.jenkins.getRootUrl() + "whoAmI/api/xml").toURL());
                    request.setAdditionalHeader("Authorization", base64("user-not-valid", token.get()));
                    assertThat(wc.getPage(request).getWebResponse().getStatusCode(), equalTo(401));
                }

                assertNull(User.getById("user-not-valid", false));

                { // valid user with valid token, ok
                    WebRequest request = new WebRequest(new URI(j.jenkins.getRootUrl() + "whoAmI/api/xml").toURL());
                    request.setAdditionalHeader("Authorization", base64("user1", token.get()));
                    XmlPage xmlPage = wc.getPage(request);
                    assertThat(xmlPage, hasXPath("//name", is("user1")));
                }
        });
    }

    /*
     * The user is not saved after login without the default SecurityListener#fireAuthenticated
     */
    @Test
    @Issue("SECURITY-896")
    public void legacyToken_withoutLastGrantedAuthorities() throws Throwable {
        AtomicReference<String> token = new AtomicReference<>();
        sessions.then(j -> {
                enableLegacyTokenGenerationOnUserCreation();
                configureSecurity(j);

                {
                    JenkinsRule.WebClient wc = j.createWebClient();
                    wc.login("user1");
                    HtmlPage page = wc.goTo("user/user1/security/");
                    String tokenValue = ((HtmlTextInput) page.getDocumentElement().querySelector("#apiToken")).getText();
                    token.set(tokenValue);
                }
        });
        sessions.then(j -> {
                User user = User.getById("user1", false);
                assertNull(user);

                JenkinsRule.WebClient wc = j.createWebClient();
                wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

                { // for invalid token, no effect
                    WebRequest request = new WebRequest(new URI(j.jenkins.getRootUrl() + "whoAmI/api/xml").toURL());
                    request.setAdditionalHeader("Authorization", base64("user1", "invalid-token"));
                    assertThat(wc.getPage(request).getWebResponse().getStatusCode(), equalTo(401));
                }
                { // for invalid user, no effect
                    WebRequest request = new WebRequest(new URI(j.jenkins.getRootUrl() + "whoAmI/api/xml").toURL());
                    request.setAdditionalHeader("Authorization", base64("user-not-valid", token.get()));
                    assertThat(wc.getPage(request).getWebResponse().getStatusCode(), equalTo(401));
                }

                assertNull(User.getById("user1", false));
                assertNull(User.getById("user-not-valid", false));

                { // valid user with valid token, ok
                    WebRequest request = new WebRequest(new URI(j.jenkins.getRootUrl() + "whoAmI/api/xml").toURL());
                    request.setAdditionalHeader("Authorization", base64("user1", token.get()));
                    XmlPage xmlPage = wc.getPage(request);
                    assertThat(xmlPage, hasXPath("//name", is("user1")));
                }
        });
        sessions.then(j -> {
                User user = User.getById("user1", false);
                assertNull(user);
        });
    }

    @TestExtension("legacyToken_withoutLastGrantedAuthorities")
    public static class RemoveDefaultSecurityListener extends ExtensionFilter {
        @Override
        public <T> boolean allows(Class<T> type, ExtensionComponent<T> component) {
            return !SecurityListener.class.isAssignableFrom(type);
        }
    }

    private static void enableLegacyTokenGenerationOnUserCreation() {
        ApiTokenPropertyConfiguration apiTokenConfiguration = GlobalConfiguration.all().getInstance(ApiTokenPropertyConfiguration.class);
        // by default it's false
        apiTokenConfiguration.setTokenGenerationOnCreationEnabled(true);
    }

    private static void configureSecurity(JenkinsRule j) throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().toEveryone());

        j.jenkins.save();
    }

    private static String base64(String login, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((login + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
