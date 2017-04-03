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

import hudson.Launcher;
import hudson.model.User;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import static org.hamcrest.Matchers.containsString;
import org.jenkinsci.main.modules.cli.auth.ssh.UserPropertyImpl;
import org.jenkinsci.main.modules.sshd.SSHD;
import static org.junit.Assert.*;
import static org.junit.Assume.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

public class CLITest {
    
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Issue("JENKINS-41745")
    @Test
    public void strictHostKey() throws Exception {
        File home = tmp.newFolder();
        // Seems it gets created automatically but with inappropriate permissions:
        File known_hosts = new File(new File(home, ".ssh"), "known_hosts");
        assumeTrue(known_hosts.getParentFile().mkdir());
        assumeTrue(known_hosts.createNewFile());
        assumeTrue(known_hosts.setWritable(false, false));
        assumeTrue(known_hosts.setWritable(true, true));
        try {
            Files.getOwner(known_hosts.toPath());
        } catch (IOException x) {
            assumeNoException("Sometimes on Windows KnownHostsServerKeyVerifier.acceptIncompleteHostKeys says WARNING: Failed (FileSystemException) to reload server keys from …\\\\.ssh\\\\known_hosts: … Incorrect function.", x);
        }
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("admin"));
        SSHD.get().setPort(0);
        File jar = tmp.newFile("jenkins-cli.jar");
        FileUtils.copyURLToFile(r.jenkins.getJnlpJars("jenkins-cli.jar").getURL(), jar);
        File privkey = tmp.newFile("id_rsa");
        FileUtils.copyURLToFile(CLITest.class.getResource("id_rsa"), privkey);
        User.get("admin").addProperty(new UserPropertyImpl(IOUtils.toString(CLITest.class.getResource("id_rsa.pub"))));
        assertNotEquals(0, new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(
            "java", "-Duser.home=" + home, "-jar", jar.getAbsolutePath(), "-s", r.getURL().toString(), "-ssh", "-user", "admin", "-i", privkey.getAbsolutePath(), "-strictHostKey", "who-am-i"
        ).stdout(System.out).stderr(System.err).join());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertEquals(0, new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(
            "java", "-Duser.home=" + home, "-jar", jar.getAbsolutePath(), "-s", r.getURL().toString(), "-ssh", "-user", "admin", "-i", privkey.getAbsolutePath(), "-logger", "FINEST",  "who-am-i"
        ).stdout(baos).stderr(System.err).join());
        assertThat(baos.toString(), containsString("Authenticated as: admin"));
        baos = new ByteArrayOutputStream();
        assertEquals(0, new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(
            "java", "-Duser.home=" + home, "-jar", jar.getAbsolutePath(), "-s", r.getURL().toString(), "-ssh", "-user", "admin", "-i", privkey.getAbsolutePath(), "-strictHostKey", "who-am-i"
        ).stdout(baos).stderr(System.err).join());
        assertThat(baos.toString(), containsString("Authenticated as: admin"));
    }

}
