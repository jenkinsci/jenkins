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
import hudson.Functions;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.FreeStyleProject;
import hudson.model.User;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.TeeOutputStream;
import static org.hamcrest.Matchers.*;
import org.jenkinsci.main.modules.cli.auth.ssh.UserPropertyImpl;
import org.jenkinsci.main.modules.sshd.SSHD;
import static org.junit.Assert.*;
import static org.junit.Assume.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.SleepBuilder;

public class CLITest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** Sets up a fake {@code user.home} so that tests {@code -ssh} mode does not get confused by the developer’s real {@code ~/.ssh/known_hosts}. */
    private File tempHome() throws IOException {
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
        /* TODO impossible to do this until the bundled sshd module uses a sufficiently new version of sshd-core:
        assumeThat("or on Windows DefaultKnownHostsServerKeyVerifier.reloadKnownHosts says invalid file permissions: Owner violation (Administrators)",
            ModifiableFileWatcher.validateStrictConfigFilePermissions(known_hosts.toPath()), nullValue());
        */
        assumeFalse(Functions.isWindows()); // TODO can remove when above check is restored
        return home;
    }

    @Issue("JENKINS-41745")
    @Test
    public void strictHostKey() throws Exception {
        File home = tempHome();
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
            "java", "-Duser.home=" + home, "-jar", jar.getAbsolutePath(), "-s", r.getURL().toString()./* just checking */replaceFirst("/$", ""), "-ssh", "-user", "admin", "-i", privkey.getAbsolutePath(), "-strictHostKey", "who-am-i"
        ).stdout(baos).stderr(System.err).join());
        assertThat(baos.toString(), containsString("Authenticated as: admin"));
    }

    @Issue("JENKINS-41745")
    @Test
    public void interrupt() throws Exception {
        File home = tempHome();
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("admin"));
        SSHD.get().setPort(0);
        File jar = tmp.newFile("jenkins-cli.jar");
        FileUtils.copyURLToFile(r.jenkins.getJnlpJars("jenkins-cli.jar").getURL(), jar);
        File privkey = tmp.newFile("id_rsa");
        FileUtils.copyURLToFile(CLITest.class.getResource("id_rsa"), privkey);
        User.get("admin").addProperty(new UserPropertyImpl(IOUtils.toString(CLITest.class.getResource("id_rsa.pub"))));
        FreeStyleProject p = r.createFreeStyleProject("p");
        p.getBuildersList().add(new SleepBuilder(TimeUnit.MINUTES.toMillis(5)));
        doInterrupt(jar, home, p, "-remoting", "-i", privkey.getAbsolutePath());
        doInterrupt(jar, home, p, "-ssh", "-user", "admin", "-i", privkey.getAbsolutePath());
        doInterrupt(jar, home, p, "-http", "-auth", "admin:admin");
    }
    private void doInterrupt(File jar, File home, FreeStyleProject p, String... modeArgs) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        List<String> args = Lists.newArrayList("java", "-Duser.home=" + home, "-jar", jar.getAbsolutePath(), "-s", r.getURL().toString());
        args.addAll(Arrays.asList(modeArgs));
        args.addAll(Arrays.asList("build", "-s", "-v", "p"));
        Proc proc = new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(args).stdout(new TeeOutputStream(baos, System.out)).stderr(System.err).start();
        while (!baos.toString().contains("Sleeping ")) {
            Thread.sleep(100);
        }
        System.err.println("Killing client");
        proc.kill();
        r.waitForCompletion(p.getLastBuild());
    }

}
