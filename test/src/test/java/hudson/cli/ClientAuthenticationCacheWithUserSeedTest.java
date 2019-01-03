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

package hudson.cli;

import com.google.common.collect.Lists;
import hudson.Launcher;
import hudson.model.User;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;
import jenkins.security.seed.UserSeedProperty;
import org.acegisecurity.Authentication;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.TeeOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import test.security.realm.InMemorySecurityRealm;

import javax.annotation.CheckForNull;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@For({UserSeedProperty.class, ClientAuthenticationCache.class})
public class ClientAuthenticationCacheWithUserSeedTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Rule
    public LoggerRule logging = new LoggerRule().record(ClientAuthenticationCache.class, Level.FINER);

    @Test
    @Issue("SECURITY-1247")
    public void legacyCache_smoothlyMigratedWithUserSeed() throws Exception {
        ClientAuthenticationCache cache = new ClientAuthenticationCache(null);
        assertThat(cache.get(), is(Jenkins.ANONYMOUS));

        InMemorySecurityRealm securityRealm = new InMemorySecurityRealm();
        r.jenkins.setSecurityRealm(securityRealm);
        r.jenkins.setAuthorizationStrategy(new FullControlOnceLoggedInAuthorizationStrategy());

        User user = User.getById("user", true);

        securityRealm.createAccount(user.getId());

        try {
            // legacy value
            String legacyValue = user.getId() + ":" + cache.getMacOf(user.getId());
            cache.props.setProperty(cache.getPropertyKey(), legacyValue);

            cache.setUsingLegacyMethod(user.getId());
            String valueAfterSave = cache.props.getProperty(cache.getPropertyKey());
            assertThat(valueAfterSave, is(legacyValue));

            File jar = tmp.newFile("jenkins-cli.jar");
            FileUtils.copyURLToFile(r.jenkins.getJnlpJars("jenkins-cli.jar").getURL(), jar);

            assertCLI(0, "Authenticated as: " + user.getId(), jar, "who-am-i");

            cache = new ClientAuthenticationCache(null);
            String valueAfterUsage = cache.props.getProperty(cache.getPropertyKey());
            assertThat(valueAfterUsage, is(legacyValue));

            assertCLI(0, null, jar, "login", "--username", user.getId(), "--password", "<anyPwdForThatRealm>");

            cache = new ClientAuthenticationCache(null);
            String valueAfterLogin = cache.props.getProperty(cache.getPropertyKey());
            assertThat(valueAfterLogin, not(is(legacyValue)));

            assertCLI(0, "Authenticated as: " + user.getId(), jar, "who-am-i");
        } finally {
            cache.props.remove(cache.getPropertyKey());
        }
    }

    @Test
    @Issue("SECURITY-1247")
    public void cannotUseCacheAfterUserSeedReset() throws Exception {
        InMemorySecurityRealm securityRealm = new InMemorySecurityRealm();

        File jar = tmp.newFile("jenkins-cli.jar");
        FileUtils.copyURLToFile(r.jenkins.getJnlpJars("jenkins-cli.jar").getURL(), jar);
        r.jenkins.setSecurityRealm(securityRealm);
        r.jenkins.setAuthorizationStrategy(new FullControlOnceLoggedInAuthorizationStrategy());

        User user = User.getById("user", true);

        // who-am-i anonymous
        assertCLI(0, "Authenticated as: anonymous", jar, "who-am-i");
        try {
            // login
            assertCLI(7, null, jar, "login", "--username", user.getId(), "--password", "<anyPwdForThatRealm>");
            securityRealm.createAccount(user.getId());
            assertCLI(0, null, jar, "login", "--username", user.getId(), "--password", "<anyPwdForThatRealm>");
            // who-am-i ok
            assertCLI(0, "Authenticated as: " + user.getId(), jar, "who-am-i");
            // reset userSeed
            user.getProperty(UserSeedProperty.class).renewSeed();
            // who-am-i anonymous
            assertCLI(0, "Authenticated as: anonymous", jar, "who-am-i");
            // login
            assertCLI(0, null, jar, "login", "--username", user.getId(), "--password", "<anyPwdForThatRealm>");
            // who-am-i ok
            assertCLI(0, "Authenticated as: " + user.getId(), jar, "who-am-i");
        } finally {
            // to avoid letting traces in the AuthenticationCache 
            assertCLI(null, null, jar, "logout");
        }
    }

    @Test
    @Issue("SECURITY-1247")
    public void canStillUseCacheAfterUserSeedReset_ifDisabled() throws Exception {
        boolean previousConfig = UserSeedProperty.DISABLE_USER_SEED;
        try {
            UserSeedProperty.DISABLE_USER_SEED = true;

            InMemorySecurityRealm securityRealm = new InMemorySecurityRealm();

            File jar = tmp.newFile("jenkins-cli.jar");
            FileUtils.copyURLToFile(r.jenkins.getJnlpJars("jenkins-cli.jar").getURL(), jar);
            r.jenkins.setSecurityRealm(securityRealm);
            r.jenkins.setAuthorizationStrategy(new FullControlOnceLoggedInAuthorizationStrategy());

            User user = User.getById("user", true);

            // who-am-i anonymous
            assertCLI(0, "Authenticated as: anonymous", jar, "who-am-i");

            try {
                // login
                assertCLI(7, null, jar, "login", "--username", user.getId(), "--password", "<anyPwdForThatRealm>");
                securityRealm.createAccount(user.getId());
                assertCLI(0, null, jar, "login", "--username", user.getId(), "--password", "<anyPwdForThatRealm>");
                // who-am-i ok
                assertCLI(0, "Authenticated as: " + user.getId(), jar, "who-am-i");
                // reset userSeed
                user.getProperty(UserSeedProperty.class).renewSeed();
                // does not have any effect on the cache
                assertCLI(0, "Authenticated as: " + user.getId(), jar, "who-am-i");
            } finally {
                // to avoid letting traces in the AuthenticationCache 
                assertCLI(null, null, jar, "logout");
            }
        } finally {
            UserSeedProperty.DISABLE_USER_SEED = previousConfig;
        }
    }

    private void assertCLI(@CheckForNull Integer code, @CheckForNull String output, File jar, String... args) throws Exception {
        List<String> commands = Lists.newArrayList("java", "-jar", jar.getAbsolutePath(), "-s", r.getURL().toString(), "-noKeyAuth", "-remoting");
        commands.addAll(Arrays.asList(args));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int returnValue = new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(commands).stdout(new TeeOutputStream(System.out, baos)).stderr(System.err).join();
        if (code != null) {
            assertEquals(code.intValue(), returnValue);
        }
        if (output != null) {
            assertThat(baos.toString(), containsString(output));
        }
    }
}
