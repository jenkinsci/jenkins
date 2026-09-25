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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.FilePath;
import hudson.tasks.Mailer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

class UserRestartTest {

    @RegisterExtension
    private final JenkinsSessionExtension sessions = new JenkinsSessionExtension();

    @Test
    void persistedUsers() throws Throwable {
        sessions.then(r -> {
            User bob = User.getById("bob", true);
            bob.setFullName("Bob");
            bob.addProperty(new Mailer.UserProperty("bob@nowhere.net"));
        });
        sessions.then(r -> {
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
    void badSerialization() throws Throwable {
        sessions.then(r -> {
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
        sessions.then(r -> {
            FreeStyleProject p = r.jenkins.getItemByFullName("p", FreeStyleProject.class);
            /*
             * User.Replacer.readResolve() is racy, as its comments acknowledge. The loading of jobs
             * and the initialization of UserIdMapper run concurrently, and UserIdMapper may not
             * have been initialized yet when we are loading the job. The only way for this test to
             * work reliably is to reload the job after UserIdMapper has been initialized, thus
             * assuring that the job's reference to the user can be properly deserialized.
             */
            p.doReload();
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
    void legacyConfigMoveCannotEscapeUserFolder() throws Throwable {
        sessions.then(r -> {
                r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
                assertThat(r.jenkins.isUseSecurity(), equalTo(true));

                // in order to create the folder "users"
                User.getById("admin", true).save();

                { // attempt with ".."
                    JenkinsRule.WebClient wc = r.createWebClient()
                            .withThrowExceptionOnFailingStatusCode(false);

                    WebRequest request = new WebRequest(new URI(r.jenkins.getRootUrl() + "whoAmI/api/xml").toURL());
                    request.setAdditionalHeader("Authorization", base64("..", "any-password"));
                    wc.getPage(request);
                }
                { // attempt with "../users/.."
                    JenkinsRule.WebClient wc = r.createWebClient()
                            .withThrowExceptionOnFailingStatusCode(false);

                    WebRequest request = new WebRequest(new URI(r.jenkins.getRootUrl() + "whoAmI/api/xml").toURL());
                    request.setAdditionalHeader("Authorization", base64("../users/..", "any-password"));
                    wc.getPage(request);
                }

                // security is still active
                assertThat(r.jenkins.isUseSecurity(), equalTo(true));
                // but, the config file was moved
                FilePath rootPath = r.jenkins.getRootPath();
                assertThat(rootPath.child("config.xml").exists(), equalTo(true));
        });
        sessions.then(r -> {
                assertThat(r.jenkins.isUseSecurity(), equalTo(true));
                FilePath rootPath = r.jenkins.getRootPath();
                assertThat(rootPath.child("config.xml").exists(), equalTo(true));
        });
    }

    private String base64(String login, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((login + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
