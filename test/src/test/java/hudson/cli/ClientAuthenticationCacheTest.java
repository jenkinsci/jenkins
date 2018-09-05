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

package hudson.cli;

import com.google.common.collect.Lists;

import hudson.ExtensionList;
import hudson.Launcher;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import hudson.util.Secret;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.CheckForNull;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.TeeOutputStream;
import static org.hamcrest.Matchers.containsString;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

public class ClientAuthenticationCacheTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Rule
    public LoggerRule logging = new LoggerRule().record(ClientAuthenticationCache.class, Level.FINER);

    @Before
    public void setUp() {
        Set<String> agentProtocols = new HashSet<>(r.jenkins.getAgentProtocols());
        agentProtocols.add(ExtensionList.lookupSingleton(CliProtocol2.class).getName());
        r.jenkins.setAgentProtocols(agentProtocols);
    }

    @Issue("SECURITY-466")
    @Test
    public void login() throws Exception {
        File jar = tmp.newFile("jenkins-cli.jar");
        FileUtils.copyURLToFile(r.jenkins.getJnlpJars("jenkins-cli.jar").getURL(), jar);
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new FullControlOnceLoggedInAuthorizationStrategy());
        assertCLI(0, "Authenticated as: anonymous", jar, "who-am-i");
        assertCLI(0, null, jar, "login", "--username", "dev", "--password", "dev");
        try {
            assertCLI(0, "Authenticated as: dev", jar, "who-am-i");
            ClientAuthenticationCache cache = new ClientAuthenticationCache(null);
            String val = cache.props.getProperty(cache.getPropertyKey());
            assertNotNull(val);
            System.err.println(val);
            Secret s = Secret.decrypt(val);
            if (s != null && s.getPlainText().equals("dev")) {
                val = Secret.fromString("admin").getEncryptedValue();
            }
            System.err.println(val);
            val = val.replace("dev", "admin");
            System.err.println(val);
            cache.props.put(cache.getPropertyKey(), val);
            cache.save();
            assertCLI(0, "Authenticated as: anonymous", jar, "who-am-i");
        } finally {
            assertCLI(0, null, jar, "logout");
        }
    }

    @Ignore("TODO fails unless CLICommand.main patched to replace (auth==Jenkins.ANONYMOUS) with (auth instanceof AnonymousAuthenticationToken), not just (Jenkins.ANONYMOUS.equals(auth)), since SecurityFilters.groovy sets userAttribute='anonymous,' so UserAttributeEditor.setAsText configures AnonymousProcessingFilter with a token with an empty authority which fails AbstractAuthenticationToken.equals")
    @Test
    public void overHttp() throws Exception {
        File jar = tmp.newFile("jenkins-cli.jar");
        FileUtils.copyURLToFile(r.jenkins.getJnlpJars("jenkins-cli.jar").getURL(), jar);
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new FullControlOnceLoggedInAuthorizationStrategy());
        r.jenkins.setSlaveAgentPort(-1);
        assertCLI(0, "Authenticated as: anonymous", jar, "who-am-i");
        assertCLI(0, null, jar, "login", "--username", "admin", "--password", "admin");
        try {
            assertCLI(0, "Authenticated as: admin", jar, "who-am-i");
        } finally {
            assertCLI(0, null, jar, "logout");
        }
    }

    @Test
    public void getPropertyKey() throws Exception {
        ClientAuthenticationCache cache = new ClientAuthenticationCache(null);
        assertEquals(r.getURL().toString(), cache.getPropertyKey());
        JenkinsLocationConfiguration.get().setUrl(null);
        String key = cache.getPropertyKey();
        assertEquals(r.jenkins.getLegacyInstanceId(), key);
    }
    
    @Test
    @Issue("JENKINS-47426")
    public void getPropertyKey_mustBeEquivalentOverTime() throws Exception {
        ClientAuthenticationCache cache = new ClientAuthenticationCache(null);

        String key1 = cache.getPropertyKey();
        String key2 = cache.getPropertyKey();

        assertEquals("Two calls to the getPropertyKey() must be equivalent over time, with rootUrl", key1, key2);

        JenkinsLocationConfiguration.get().setUrl(null);

        key1 = cache.getPropertyKey();
        key2 = cache.getPropertyKey();

        assertEquals("Two calls to the getPropertyKey() must be equivalent over time, without rootUrl", key1, key2);
    }

    private void assertCLI(int code, @CheckForNull String output, File jar, String... args) throws Exception {
        List<String> commands = Lists.newArrayList("java", "-jar", jar.getAbsolutePath(), "-s", r.getURL().toString(), "-noKeyAuth", "-remoting");
        commands.addAll(Arrays.asList(args));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertEquals(code, new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(commands).stdout(new TeeOutputStream(System.out, baos)).stderr(System.err).join());
        if (output != null) {
            assertThat(baos.toString(), containsString(output));
        }
    }

}
