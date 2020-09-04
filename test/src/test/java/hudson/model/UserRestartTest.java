/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

package hudson.model;

import com.gargoylesoftware.htmlunit.WebRequest;
import hudson.FilePath;
import hudson.tasks.Mailer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;

import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.hamcrest.MatcherAssert.assertThat;

public class UserRestartTest {

    @Rule
    public RestartableJenkinsRule rr = new RestartableJenkinsRule();

    @Test public void persistedUsers() throws Exception {
        rr.then(r -> {
            User bob = User.getById("bob", true);
            bob.setFullName("Bob");
            bob.addProperty(new Mailer.UserProperty("bob@nowhere.net"));
        });
        rr.then(r -> {
            User bob = User.getById("bob", false);
            assertNotNull(bob);
            assertEquals("Bob", bob.getFullName());
            Mailer.UserProperty email = bob.getProperty(Mailer.UserProperty.class);
            assertNotNull(email);
            assertEquals("bob@nowhere.net", email.getAddress());
        });
    }

    @Issue("JENKINS-45892")
    @Test
    public void badSerialization() {
        rr.then(r -> {
            r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
            FreeStyleProject p = r.createFreeStyleProject("p");
            User u = User.get("pqhacker");
            u.setFullName("Pat Q. Hacker");
            u.save();
            p.addProperty(new BadProperty(u));
            String text = p.getConfigFile().asString();
            assertThat(text, not(containsString("<fullName>Pat Q. Hacker</fullName>")));
            assertThat(text, containsString("<id>pqhacker</id>"));
        });
        rr.then(r -> {
            FreeStyleProject p = r.jenkins.getItemByFullName("p", FreeStyleProject.class);
            User u = p.getProperty(BadProperty.class).user; // do not inline: call User.get second
            assertEquals(User.get("pqhacker"), u);
        });
    }
    static class BadProperty extends JobProperty<FreeStyleProject> {
        final User user;
        BadProperty(User user) {
            this.user = user;
        }
    }

    @Test
    @Issue("SECURITY-897")
    public void legacyConfigMoveCannotEscapeUserFolder() {
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                rr.j.jenkins.setSecurityRealm(rr.j.createDummySecurityRealm());
                assertThat(rr.j.jenkins.isUseSecurity(), equalTo(true));

                // in order to create the folder "users"
                User.getById("admin", true).save();

                { // attempt with ".."
                    JenkinsRule.WebClient wc = rr.j.createWebClient()
                            .withThrowExceptionOnFailingStatusCode(false);

                    WebRequest request = new WebRequest(new URL(rr.j.jenkins.getRootUrl() + "whoAmI/api/xml"));
                    request.setAdditionalHeader("Authorization", base64("..", "any-password"));
                    wc.getPage(request);
                }
                { // attempt with "../users/.."
                    JenkinsRule.WebClient wc = rr.j.createWebClient()
                            .withThrowExceptionOnFailingStatusCode(false);

                    WebRequest request = new WebRequest(new URL(rr.j.jenkins.getRootUrl() + "whoAmI/api/xml"));
                    request.setAdditionalHeader("Authorization", base64("../users/..", "any-password"));
                    wc.getPage(request);
                }

                // security is still active
                assertThat(rr.j.jenkins.isUseSecurity(), equalTo(true));
                // but, the config file was moved
                FilePath rootPath = rr.j.jenkins.getRootPath();
                assertThat(rootPath.child("config.xml").exists(), equalTo(true));
            }
        });
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                assertThat(rr.j.jenkins.isUseSecurity(), equalTo(true));
                FilePath rootPath = rr.j.jenkins.getRootPath();
                assertThat(rootPath.child("config.xml").exists(), equalTo(true));
            }
        });
    }

    private String base64(String login, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((login + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
