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

import com.gargoylesoftware.htmlunit.WebResponse;
import com.google.common.collect.Lists;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.FreeStyleProject;
import hudson.model.UnprotectedRootAction;
import hudson.model.User;
import hudson.security.csrf.CrumbExclusion;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.sshd.common.util.io.ModifiableFileWatcher;
import static org.hamcrest.Matchers.*;
import org.jenkinsci.main.modules.cli.auth.ssh.UserPropertyImpl;
import org.jenkinsci.main.modules.sshd.SSHD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.hamcrest.MatcherAssert.assertThat;
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
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Ignore;

public class CLITest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File home;
    private File jar;

    /** Sets up a fake {@code user.home} so that tests {@code -ssh} mode does not get confused by the developer’s real {@code ~/.ssh/known_hosts}. */
    private File tempHome() throws IOException {
        home = tmp.newFolder();
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
        assumeThat("or on Windows DefaultKnownHostsServerKeyVerifier.reloadKnownHosts says invalid file permissions: Owner violation (Administrators)",
            ModifiableFileWatcher.validateStrictConfigFilePermissions(known_hosts.toPath()), nullValue());
        return home;
    }

    @Issue("JENKINS-41745")
    @Test
    public void strictHostKey() throws Exception {
        home = tempHome();
        grabCliJar();

        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("admin"));
        SSHD.get().setPort(0);
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

    private void grabCliJar() throws IOException {
        jar = tmp.newFile("jenkins-cli.jar");
        FileUtils.copyURLToFile(r.jenkins.getJnlpJars("jenkins-cli.jar").getURL(), jar);
    }

    @Issue("JENKINS-41745")
    @Test
    public void interrupt() throws Exception {
        home = tempHome();
        grabCliJar();

        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("admin"));
        SSHD.get().setPort(0);
        File privkey = tmp.newFile("id_rsa");
        FileUtils.copyURLToFile(CLITest.class.getResource("id_rsa"), privkey);
        User.get("admin").addProperty(new UserPropertyImpl(IOUtils.toString(CLITest.class.getResource("id_rsa.pub"))));
        FreeStyleProject p = r.createFreeStyleProject("p");
        p.getBuildersList().add(new SleepBuilder(TimeUnit.MINUTES.toMillis(5)));
        doInterrupt(p, "-ssh", "-user", "admin", "-i", privkey.getAbsolutePath());
        doInterrupt(p, "-http", "-auth", "admin:admin");
        doInterrupt(p, "-webSocket", "-auth", "admin:admin");
    }
    private void doInterrupt(FreeStyleProject p, String... modeArgs) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        List<String> args = Lists.newArrayList("java", "-Duser.home=" + home, "-jar", jar.getAbsolutePath(), "-s", r.getURL().toString());
        args.addAll(Arrays.asList(modeArgs));
        args.addAll(Arrays.asList("build", "-s", "-v", "p"));
        Proc proc = new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(args).stdout(new TeeOutputStream(baos, System.out)).stderr(System.err).start();
        while (!baos.toString().contains("Sleeping ")) {
            if (!proc.isAlive()) {
                throw new AssertionError("Process failed to start with " + proc.join());
            }
            Thread.sleep(100);
        }
        System.err.println("Killing client");
        proc.kill();
        r.waitForCompletion(p.getLastBuild());
    }

    @Test @Issue("JENKINS-44361")
    public void reportNotJenkins() throws Exception {
        home = tempHome();
        grabCliJar();

        String url = r.getURL().toExternalForm() + "not-jenkins/";
        for (String transport : Arrays.asList("-http", "-ssh")) {

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int ret = new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(
                    "java", "-Duser.home=" + home, "-jar", jar.getAbsolutePath(), "-s", url, transport, "-user", "asdf", "who-am-i"
            ).stdout(baos).stderr(baos).join();

            assertThat(baos.toString(), containsString("There's no Jenkins running at"));
            assertNotEquals(0, ret);
        }
        // TODO -webSocket currently produces a stack trace
    }
    @TestExtension("reportNotJenkins")
    public static final class NoJenkinsAction extends CrumbExclusion implements UnprotectedRootAction, StaplerProxy {

        @Override public String getIconFileName() {
            return "not-jenkins";
        }

        @Override public String getDisplayName() {
            return "not-jenkins";
        }

        @Override public String getUrlName() {
            return "not-jenkins";
        }

        @Override public Object getTarget() {
            doDynamic(Stapler.getCurrentRequest(), Stapler.getCurrentResponse());
            return this;
        }

        public void doDynamic(StaplerRequest req, StaplerResponse rsp) {
            rsp.setStatus(200);
        }

        @Override // Permit access to cli-proxy/XXX without CSRF checks
        public boolean process(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
            chain.doFilter(request, response);
            return true;
        }
    }

    @Test @Issue("JENKINS-44361")
    public void redirectToEndpointShouldBeFollowed() throws Exception {
        home = tempHome();
        grabCliJar();

        // Enable CLI over SSH
        SSHD sshd = GlobalConfiguration.all().get(SSHD.class);
        sshd.setPort(0); // random
        sshd.start();

        // Sanity check
        JenkinsRule.WebClient wc = r.createWebClient()
                .withRedirectEnabled(false)
                .withThrowExceptionOnFailingStatusCode(false);
        
        WebResponse rsp = wc.goTo("cli-proxy/").getWebResponse();
        assertEquals(rsp.getContentAsString(), HttpURLConnection.HTTP_MOVED_TEMP, rsp.getStatusCode());
        assertNull(rsp.getContentAsString(), rsp.getResponseHeaderValue("X-Jenkins"));
        assertNull(rsp.getContentAsString(), rsp.getResponseHeaderValue("X-Jenkins-CLI-Port"));
        assertNull(rsp.getContentAsString(), rsp.getResponseHeaderValue("X-SSH-Endpoint"));

        for (String transport: Arrays.asList("-http", "-ssh", "-webSocket")) {

            String url = r.getURL().toString() + "cli-proxy/";
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int ret = new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(
                    "java", "-Duser.home=" + home, "-jar", jar.getAbsolutePath(), "-s", url, transport, "-user", "asdf", "who-am-i"
            ).stdout(baos).stderr(baos).join();

            //assertThat(baos.toString(), containsString("There's no Jenkins running at"));
            assertThat(baos.toString(), containsString("Authenticated as: anonymous"));
            assertEquals(0, ret);
        }
    }

    @Ignore("TODO sometimes fails, in CI & locally")
    @Test
    @Issue("JENKINS-54310")
    public void readInputAtOnce() throws Exception {
        home = tempHome();
        grabCliJar();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            int ret = new Launcher.LocalLauncher(StreamTaskListener.fromStderr())
                    .launch()
                    .cmds("java",
                            "-Duser.home=" + home,
                            "-jar", jar.getAbsolutePath(),
                            "-s", r.getURL().toString(),
                            "list-plugins") // This CLI Command needs -auth option, so when we omit it, the CLI stops before reading the input.
                    .stdout(baos)
                    .stderr(baos)
                    .stdin(CLITest.class.getResourceAsStream("huge-stdin.txt"))
                    .join();
            assertThat(baos.toString(), not(containsString("java.io.IOException: Stream is closed")));
            assertEquals(0, ret);
        }
    }

    @TestExtension("redirectToEndpointShouldBeFollowed")
    public static final class CliProxyAction extends CrumbExclusion implements UnprotectedRootAction, StaplerProxy {

        @Override public String getIconFileName() {
            return "cli-proxy";
        }

        @Override public String getDisplayName() {
            return "cli-proxy";
        }

        @Override public String getUrlName() {
            return "cli-proxy";
        }

        @Override public Object getTarget() {
            throw doDynamic(Stapler.getCurrentRequest(), Stapler.getCurrentResponse());
        }

        public HttpResponses.HttpResponseException doDynamic(StaplerRequest req, StaplerResponse rsp) {
            final String url = req.getRequestURIWithQueryString().replaceFirst("/cli-proxy", "");
            // Custom written redirect so no traces of Jenkins are present in headers
            return new HttpResponses.HttpResponseException() {
                @Override
                public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
                    rsp.setHeader("Location", url);
                    rsp.setContentType("text/html");
                    rsp.setStatus(HttpURLConnection.HTTP_MOVED_TEMP);
                    PrintWriter w = rsp.getWriter();
                    w.append("Redirect to ").append(url);
                }
            };
        }

        @Override // Permit access to cli-proxy/XXX without CSRF checks
        public boolean process(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
            chain.doFilter(request, response);
            return true;
        }
    }
}
