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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import hudson.Launcher;
import hudson.Proc;
import hudson.model.FreeStyleProject;
import hudson.model.UnprotectedRootAction;
import hudson.security.csrf.CrumbExclusion;
import hudson.util.StreamTaskListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.TeeOutputStream;
import org.htmlunit.WebResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

@WithJenkins
class CLITest {

    @TempDir
    private File tmp;

    private File jar;

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    private void grabCliJar() throws IOException {
        jar = File.createTempFile("jenkins-cli.jar", null, tmp);
        FileUtils.copyURLToFile(r.jenkins.getJnlpJars("jenkins-cli.jar").getURL(), jar);
    }

    @Issue("JENKINS-41745")
    @Test
    void interrupt() throws Exception {
        grabCliJar();

        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("admin"));
        FreeStyleProject p = r.createFreeStyleProject("p");
        p.getBuildersList().add(new SleepBuilder(Long.MAX_VALUE));
        doInterrupt(p, "-http", "-auth", "admin:admin");
        doInterrupt(p, "-webSocket", "-auth", "admin:admin");
    }

    private void doInterrupt(FreeStyleProject p, String... modeArgs) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        List<String> args = new ArrayList<>(Arrays.asList("java", "-jar", jar.getAbsolutePath(), "-s", r.getURL().toString()));
        args.addAll(Arrays.asList(modeArgs));
        args.addAll(Arrays.asList("build", "-s", "-v", "p"));
        Proc proc = new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(args).stdout(new TeeOutputStream(baos, System.out)).stderr(System.err).start();
        while (!baos.toString(Charset.defaultCharset()).contains("Sleeping ")) {
            if (!proc.isAlive()) {
                throw new AssertionError("Process failed to start with " + proc.join());
            }
            Thread.sleep(100);
        }
        System.err.println("Killing client");
        proc.kill();
        r.waitForCompletion(p.getLastBuild());
    }

    @Test
    @Issue("JENKINS-44361")
    void reportNotJenkins() throws Exception {
        grabCliJar();

        String url = r.getURL().toExternalForm() + "not-jenkins/";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int ret = new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(
                "java", "-jar", jar.getAbsolutePath(), "-s", url, "-http", "-user", "asdf", "who-am-i"
        ).stdout(baos).stderr(baos).join();

        assertThat(baos.toString(Charset.defaultCharset()), containsString("There's no Jenkins running at"));
        assertNotEquals(0, ret);
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
            doDynamic(Stapler.getCurrentRequest2(), Stapler.getCurrentResponse2());
            return this;
        }

        public void doDynamic(StaplerRequest2 req, StaplerResponse2 rsp) {
            rsp.setStatus(200);
        }

        @Override // Permit access to cli-proxy/XXX without CSRF checks
        public boolean process(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
            chain.doFilter(request, response);
            return true;
        }
    }

    @Test
    @Issue("JENKINS-44361")
    void redirectToEndpointShouldBeFollowed() throws Exception {
        grabCliJar();

        // Sanity check
        JenkinsRule.WebClient wc = r.createWebClient()
                .withRedirectEnabled(false)
                .withThrowExceptionOnFailingStatusCode(false);

        WebResponse rsp = wc.goTo("cli-proxy/").getWebResponse();
        assertEquals(HttpURLConnection.HTTP_MOVED_TEMP, rsp.getStatusCode(), rsp.getContentAsString());
        assertNull(rsp.getResponseHeaderValue("X-Jenkins"), rsp.getContentAsString());

        for (String transport : Arrays.asList("-http", "-webSocket")) {

            String url = r.getURL().toString() + "cli-proxy/";
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int ret = new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(
                    "java", "-jar", jar.getAbsolutePath(), "-s", url, transport, "-user", "asdf", "who-am-i"
            ).stdout(baos).stderr(baos).join();

            //assertThat(baos.toString(), containsString("There's no Jenkins running at"));
            assertThat(baos.toString(Charset.defaultCharset()), containsString("Authenticated as: anonymous"));
            assertEquals(0, ret);
        }
    }

    @Disabled("TODO sometimes fails, in CI & locally")
    @Test
    @Issue("JENKINS-54310")
    void readInputAtOnce() throws Exception {
        grabCliJar();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            int ret = new Launcher.LocalLauncher(StreamTaskListener.fromStderr())
                    .launch()
                    .cmds("java",
                            "-jar", jar.getAbsolutePath(),
                            "-s", r.getURL().toString(),
                            "list-plugins") // This CLI Command needs -auth option, so when we omit it, the CLI stops before reading the input.
                    .stdout(baos)
                    .stderr(baos)
                    .stdin(CLITest.class.getResourceAsStream("huge-stdin.txt"))
                    .join();
            assertThat(baos.toString(Charset.defaultCharset()), not(containsString("java.io.IOException: Stream is closed")));
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
            throw doDynamic(Stapler.getCurrentRequest2(), Stapler.getCurrentResponse2());
        }

        public HttpResponses.HttpResponseException doDynamic(StaplerRequest2 req, StaplerResponse2 rsp) {
            final String url = req.getRequestURIWithQueryString().replaceFirst("/cli-proxy", "");
            // Custom written redirect so no traces of Jenkins are present in headers
            return new HttpResponses.HttpResponseException() {
                @Override
                public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node) throws IOException {
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
