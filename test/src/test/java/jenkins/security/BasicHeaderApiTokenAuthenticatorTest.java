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

import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import hudson.ExtensionComponent;
import hudson.model.User;
import jenkins.ExtensionFilter;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import jenkins.security.apitoken.ApiTokenPropertyConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.xml.HasXPath.hasXPath;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.hamcrest.MatcherAssert.assertThat;

public class BasicHeaderApiTokenAuthenticatorTest {
    @Rule
    public RestartableJenkinsRule rr = new RestartableJenkinsRule();
    
    @Test
    @Issue("SECURITY-896")
    public void legacyToken_regularCase() {
        AtomicReference<String> token = new AtomicReference<>();
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                enableLegacyTokenGenerationOnUserCreation();
                configureSecurity();
                
                {
                    JenkinsRule.WebClient wc = rr.j.createWebClient();
                    // default SecurityListener will save the user when adding the LastGrantedAuthoritiesProperty
                    // and so the user is persisted
                    wc.login("user1");
                    HtmlPage page = wc.goTo("user/user1/configure");
                    String tokenValue = ((HtmlTextInput) page.getDocumentElement().querySelector("#apiToken")).getText();
                    token.set(tokenValue);
                }
            }
        });
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                User user = User.getById("user1", false);
                assertNotNull(user);
                
                JenkinsRule.WebClient wc = rr.j.createWebClient();
                wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
                
                { // for invalid token, no effect
                    WebRequest request = new WebRequest(new URL(rr.j.jenkins.getRootUrl() + "whoAmI/api/xml"));
                    request.setAdditionalHeader("Authorization", base64("user1", "invalid-token"));
                    assertThat(wc.getPage(request).getWebResponse().getStatusCode(), equalTo(401));
                }
                { // for invalid user, no effect
                    WebRequest request = new WebRequest(new URL(rr.j.jenkins.getRootUrl() + "whoAmI/api/xml"));
                    request.setAdditionalHeader("Authorization", base64("user-not-valid", token.get()));
                    assertThat(wc.getPage(request).getWebResponse().getStatusCode(), equalTo(401));
                }
    
                assertNull(User.getById("user-not-valid", false));
                
                { // valid user with valid token, ok
                    WebRequest request = new WebRequest(new URL(rr.j.jenkins.getRootUrl() + "whoAmI/api/xml"));
                    request.setAdditionalHeader("Authorization", base64("user1", token.get()));
                    XmlPage xmlPage = wc.getPage(request);
                    assertThat(xmlPage, hasXPath("//name", is("user1")));
                }
            }
        });
    }
    
    /*
     * The user is not saved after login without the default SecurityListener#fireAuthenticated
     */
    @Test
    @Issue("SECURITY-896")
    public void legacyToken_withoutLastGrantedAuthorities() {
        AtomicReference<String> token = new AtomicReference<>();
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                enableLegacyTokenGenerationOnUserCreation();
                configureSecurity();
                
                {
                    JenkinsRule.WebClient wc = rr.j.createWebClient();
                    wc.login("user1");
                    HtmlPage page = wc.goTo("user/user1/configure");
                    String tokenValue = ((HtmlTextInput) page.getDocumentElement().querySelector("#apiToken")).getText();
                    token.set(tokenValue);
                }
            }
        });
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                User user = User.getById("user1", false);
                assertNull(user);
                
                JenkinsRule.WebClient wc = rr.j.createWebClient();
                wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
                
                { // for invalid token, no effect
                    WebRequest request = new WebRequest(new URL(rr.j.jenkins.getRootUrl() + "whoAmI/api/xml"));
                    request.setAdditionalHeader("Authorization", base64("user1", "invalid-token"));
                    assertThat(wc.getPage(request).getWebResponse().getStatusCode(), equalTo(401));
                }
                { // for invalid user, no effect
                    WebRequest request = new WebRequest(new URL(rr.j.jenkins.getRootUrl() + "whoAmI/api/xml"));
                    request.setAdditionalHeader("Authorization", base64("user-not-valid", token.get()));
                    assertThat(wc.getPage(request).getWebResponse().getStatusCode(), equalTo(401));
                }
    
                assertNull(User.getById("user1", false));
                assertNull(User.getById("user-not-valid", false));
                
                { // valid user with valid token, ok
                    WebRequest request = new WebRequest(new URL(rr.j.jenkins.getRootUrl() + "whoAmI/api/xml"));
                    request.setAdditionalHeader("Authorization", base64("user1", token.get()));
                    XmlPage xmlPage = wc.getPage(request);
                    assertThat(xmlPage, hasXPath("//name", is("user1")));
                }
            }
        });
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                User user = User.getById("user1", false);
                assertNull(user);
            }
        });
    }
    
    @TestExtension("legacyToken_withoutLastGrantedAuthorities")
    public static class RemoveDefaultSecurityListener extends ExtensionFilter {
        @Override
        public <T> boolean allows(Class<T> type, ExtensionComponent<T> component) {
            return !SecurityListener.class.isAssignableFrom(type);
        }
    }
    
    private void enableLegacyTokenGenerationOnUserCreation() throws Exception {
        ApiTokenPropertyConfiguration apiTokenConfiguration = GlobalConfiguration.all().getInstance(ApiTokenPropertyConfiguration.class);
        // by default it's false
        apiTokenConfiguration.setTokenGenerationOnCreationEnabled(true);
    }
    
    private void configureSecurity() throws Exception {
        rr.j.jenkins.setSecurityRealm(rr.j.createDummySecurityRealm());
        rr.j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().toEveryone());
        
        rr.j.jenkins.save();
    }
    
    private String base64(String login, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((login + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
