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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.model.User;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

@For(UserSeedProperty.class)
class UserSeedPropertyRestartTest {

    @RegisterExtension
    private final JenkinsSessionExtension sessions = new JenkinsSessionExtension();

    @Test
    @Issue("SECURITY-901")
    void initialSeedIsSaved() throws Throwable {
        AtomicReference<String> initialSeedRef = new AtomicReference<>();

        sessions.then(j -> {
                j.jenkins.setCrumbIssuer(null);
                j.jenkins.save();

                User alice = User.getById("alice", true);
                alice.save();
                initialSeedRef.set(alice.getProperty(UserSeedProperty.class).getSeed());
        });
        sessions.then(j -> {
                User alice = User.getById("alice", false);
                String initialSeed = alice.getProperty(UserSeedProperty.class).getSeed();
                assertEquals(initialSeed, initialSeedRef.get());
        });
    }

    @Test
    @Issue("SECURITY-901")
    void renewSeedSavesTheChange() throws Throwable {
        AtomicReference<String> initialSeedRef = new AtomicReference<>();
        AtomicReference<String> seedRef = new AtomicReference<>();

        sessions.then(j -> {
                j.jenkins.setCrumbIssuer(null);
                j.jenkins.save();

                User alice = User.getById("alice", true);
                alice.save();
                initialSeedRef.set(alice.getProperty(UserSeedProperty.class).getSeed());

                requestRenewSeedForUser(alice, j);

                seedRef.set(alice.getProperty(UserSeedProperty.class).getSeed());
                assertNotEquals(initialSeedRef.get(), seedRef.get());
        });
        sessions.then(j -> {
                User alice = User.getById("alice", false);
                assertNotNull(alice);
                String currentSeed = alice.getProperty(UserSeedProperty.class).getSeed();
                assertEquals(currentSeed, seedRef.get());
        });
    }

    private static void requestRenewSeedForUser(User user, JenkinsRule j) throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest request = new WebRequest(new URI(j.jenkins.getRootUrl() + user.getUrl() + "/descriptorByName/" + UserSeedProperty.class.getName() + "/renewSessionSeed/").toURL(), HttpMethod.POST);
        wc.getPage(request);
    }
}
