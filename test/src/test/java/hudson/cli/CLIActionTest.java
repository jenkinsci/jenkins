package hudson.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

import hudson.Functions;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AllView;
import hudson.model.Item;
import hudson.model.User;
import hudson.util.ProcessTree;
import hudson.util.StreamTaskListener;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;
import jenkins.security.apitoken.ApiTokenPropertyConfiguration;
import jenkins.util.FullDuplexHttpService;
import jenkins.util.Timer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.io.output.TeeOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.args4j.Option;

public class CLIActionTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Rule
    public LoggerRule logging = new LoggerRule();

    @Test
    @Issue("SECURITY-192")
    public void serveCliActionToAnonymousUserWithoutPermissions() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy());
        JenkinsRule.WebClient wc = j.createWebClient();
        // The behavior changed due to SECURITY-192. index page is no longer accessible to anonymous
        wc.assertFails("cli", HttpURLConnection.HTTP_FORBIDDEN);
    }

    @Test
    public void serveCliActionToAnonymousUserWithAnonymousUserWithPermissions() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.goTo("cli");
    }

    @Issue({"JENKINS-12543", "JENKINS-41745"})
    @Test
    public void authentication() throws Exception {
        ApiTokenPropertyConfiguration config = ApiTokenPropertyConfiguration.get();
        config.setTokenGenerationOnCreationEnabled(true);

        logging.record(PlainCLIProtocol.class, Level.FINE);
        File jar = tmp.newFile("jenkins-cli.jar");
        FileUtils.copyURLToFile(j.jenkins.getJnlpJars("jenkins-cli.jar").getURL(), jar);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to(ADMIN));
        j.createFreeStyleProject("p");
        // CLICommand with @Argument:
        assertExitCode(6, false, jar, "get-job", "p"); // SECURITY-754 requires Overall/Read for nearly all CLICommands.
        assertExitCode(0, true, jar, "get-job", "p"); // but API tokens do work under HTTP protocol
        // @CLIMethod:
        assertExitCode(6, false, jar, "disable-job", "p"); // AccessDeniedException from CLIRegisterer?
        assertExitCode(0, true, jar, "disable-job", "p");
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to(ADMIN).grant(Jenkins.READ, Item.READ).everywhere().toEveryone());
        assertExitCode(6, false, jar, "get-job", "p"); // AccessDeniedException from AbstractItem.writeConfigDotXml
        assertExitCode(0, true, jar, "get-job", "p"); // works with API tokens
        assertExitCode(6, false, jar, "disable-job", "p"); // AccessDeniedException from AbstractProject.doDisable
        assertExitCode(0, true, jar, "disable-job", "p");
    }

    private static final String ADMIN = "admin@mycorp.com";

    private void assertExitCode(int code, boolean useApiToken, File jar, String... args) throws IOException, InterruptedException {
        List<String> commands = new ArrayList<>(Arrays.asList("java", "-jar", jar.getAbsolutePath(), "-s", j.getURL().toString()));
        if (useApiToken) {
            commands.add("-auth");
            commands.add(ADMIN + ":" + User.getOrCreateByIdOrFullName(ADMIN).getProperty(ApiTokenProperty.class).getApiToken());
        }
        commands.addAll(Arrays.asList(args));
        final Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(StreamTaskListener.fromStderr());
        final Proc proc = launcher.launch().cmds(commands).stdout(System.out).stderr(System.err).start();
        if (!Functions.isWindows()) {
            // Try to get a thread dump of the client if it hangs.
            Timer.get().schedule(() -> {
                try {
                    if (proc.isAlive()) {
                        Field procF = Proc.LocalProc.class.getDeclaredField("proc");
                        procF.setAccessible(true);
                        ProcessTree.OSProcess osp = ProcessTree.get().get((Process) procF.get(proc));
                        if (osp != null) {
                            launcher.launch().cmds("kill", "-QUIT", Integer.toString(osp.getPid())).stdout(System.out).stderr(System.err).join();
                        }
                    }
                } catch (Exception x) {
                    throw new AssertionError(x);
                }
            }, 1, TimeUnit.MINUTES);
        }
        assertEquals(code, proc.join());
    }

    @Test public void authenticationFailed() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().toAuthenticated());
        var jar = tmp.newFile("jenkins-cli.jar");
        FileUtils.copyURLToFile(j.jenkins.getJnlpJars("jenkins-cli.jar").getURL(), jar);
        var baos = new ByteArrayOutputStream();
        var exitStatus = new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(
            "java", "-jar", jar.getAbsolutePath(), "-s", j.getURL().toString(), "-auth", "user:bogustoken", "who-am-i"
        ).stdout(baos).start().join();
        assertThat(baos.toString(), allOf(containsString("status code 401"), containsStringIgnoringCase("server: Jetty")));
        assertThat(exitStatus, is(15));
    }

    @Issue("JENKINS-41745")
    @Test
    public void encodingAndLocale() throws Exception {
        logging.record(CLIAction.class, Level.FINE);
        File jar = tmp.newFile("jenkins-cli.jar");
        FileUtils.copyURLToFile(j.jenkins.getJnlpJars("jenkins-cli.jar").getURL(), jar);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertEquals(0, new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(
            "java", "-Dfile.encoding=ISO-8859-2", "-Duser.language=cs", "-Duser.country=CZ", "-jar", jar.getAbsolutePath(),
                "-s", j.getURL().toString()./* just checking */replaceFirst("/$", ""), "test-diagnostic").
            stdout(new TeeOutputStream(baos, System.out)).stderr(System.err).join());
        assertEquals("encoding=ISO-8859-2 locale=cs_CZ", baos.toString(Charset.forName("ISO-8859-2")).trim());
        // TODO test that stdout/stderr are in expected encoding (not true of -remoting mode!)
        // -ssh mode does not pass client locale or encoding
    }

    @Issue("JENKINS-41745")
    @Test
    public void interleavedStdio() throws Exception {
        logging.record(PlainCLIProtocol.class, Level.FINE).record(FullDuplexHttpService.class, Level.FINE);
        File jar = tmp.newFile("jenkins-cli.jar");
        FileUtils.copyURLToFile(j.jenkins.getJnlpJars("jenkins-cli.jar").getURL(), jar);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PipedInputStream pis = new PipedInputStream();
        PipedOutputStream pos = new PipedOutputStream(pis);
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(new TeeOutputStream(pos, System.err), Charset.defaultCharset()), true);
        Proc proc = new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(
            "java", "-jar", jar.getAbsolutePath(), "-s", j.getURL().toString(),
                "groovysh").
            stdout(new TeeOutputStream(baos, System.out)).stderr(System.err).stdin(pis).start();
        while (!baos.toString(Charset.defaultCharset()).contains("000")) { // cannot just search for, say, "groovy:000> " since there are ANSI escapes there (cf. StringEscapeUtils.escapeJava)
            Thread.sleep(100);
        }
        pw.println("11 * 11");
        while (!baos.toString(Charset.defaultCharset()).contains("121")) { // ditto not "===> 121"
            Thread.sleep(100);
        }
        pw.println("11 * 11 * 11");
        while (!baos.toString(Charset.defaultCharset()).contains("1331")) {
            Thread.sleep(100);
        }
        pw.println(":q");
        assertEquals(0, proc.join());
    }

    @Issue("SECURITY-754")
    @Test
    public void noPreAuthOptionHandlerInfoLeak() throws Exception {
        File jar = tmp.newFile("jenkins-cli.jar");
        FileUtils.copyURLToFile(j.jenkins.getJnlpJars("jenkins-cli.jar").getURL(), jar);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.addView(new AllView("v1"));
        j.jenkins.addNode(j.createSlave("n1", null, null));
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to(ADMIN));
        // No anonymous read access
        assertExitCode(6, false, jar, "get-view", "v1");
        assertExitCode(6, false, jar, "get-view", "v2"); // Error code 3 before SECURITY-754
        assertExitCode(6, false, jar, "get-node", "n1");
        assertExitCode(6, false, jar, "get-node", "n2"); // Error code 3 before SECURITY-754
        // Authenticated with no read access
        assertExitCode(6, false, jar, "-auth", "user:user", "get-view", "v1");
        assertExitCode(6, false, jar, "-auth", "user:user", "get-view", "v2"); // Error code 3 before SECURITY-754
        assertExitCode(6, false, jar, "-auth", "user:user", "get-node", "n1");
        assertExitCode(6, false, jar, "-auth", "user:user", "get-node", "n2"); // Error code 3 before SECURITY-754
        // Anonymous read access
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to(ADMIN).grant(Jenkins.READ, Item.READ).everywhere().toEveryone());
        assertExitCode(6, false, jar, "get-view", "v1");
        assertExitCode(6, false, jar, "get-view", "v2"); // Error code 3 before SECURITY-754
    }

    @TestExtension("encodingAndLocale")
    public static class TestDiagnosticCommand extends CLICommand {

        @Override
        public String getShortDescription() {
            return "Print information about the command environment.";
        }

        @Override
        protected int run() throws Exception {
            stdout.println("encoding=" + getClientCharset() + " locale=" + locale);
            return 0;
        }

    }

    @Issue("JENKINS-64294")
    @Test
    public void largeTransferWebSocket() throws Exception {
        logging.record(CLIAction.class, Level.FINE);
        File jar = tmp.newFile("jenkins-cli.jar");
        FileUtils.copyURLToFile(j.jenkins.getJnlpJars("jenkins-cli.jar").getURL(), jar);
        long size = /*999_*/999_999;
        try (OutputStream nos = OutputStream.nullOutputStream(); CountingOutputStream cos = new CountingOutputStream(nos)) {
            // Download:
            assertEquals(0, new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(
                "java", "-jar", jar.getAbsolutePath(),
                    "-webSocket",
                    "-s", j.getURL().toString(),
                    "large-download",
                    "-size", Long.toString(size)).
                stdout(cos).stderr(System.err).join());
            assertEquals(size, cos.getByteCount());
        }
        // Upload:
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertEquals(0, new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(
            "java", "-jar", jar.getAbsolutePath(),
                "-webSocket",
                "-s", j.getURL().toString(),
                "large-upload").
            stdin(new NullInputStream(size)).
            stdout(baos).stderr(System.err).join());
        assertEquals("received " + size + " bytes", baos.toString(Charset.defaultCharset()).trim());
    }

    @TestExtension("largeTransferWebSocket")
    public static final class LargeUploadCommand extends CLICommand {
        @Override
        protected int run() throws Exception {
            try (InputStream is = new BufferedInputStream(stdin); OutputStream nos = OutputStream.nullOutputStream(); CountingOutputStream cos = new CountingOutputStream(nos)) {
                System.err.println("starting upload");
                long start = System.nanoTime();
                IOUtils.copyLarge(is, cos);
                System.err.printf("finished upload in %.1fs%n", (System.nanoTime() - start) / 1_000_000_000.0);
                stdout.println("received " + cos.getByteCount() + " bytes");
                stdout.flush();
            }
            return 0;
        }

        @Override
        public String getShortDescription() {
            return "";
        }
    }

    @TestExtension("largeTransferWebSocket")
    public static final class LargeDownloadCommand extends CLICommand {
        @Option(name = "-size", required = true)
        public int size;

        @Override
        protected int run() throws Exception {
            try (OutputStream os = new BufferedOutputStream(stdout)) {
                System.err.println("starting download");
                long start = System.nanoTime();
                IOUtils.copyLarge(new NullInputStream(size), os);
                System.err.printf("finished download in %.1fs%n", (System.nanoTime() - start) / 1_000_000_000.0);
            }
            return 0;
        }

        @Override
        public String getShortDescription() {
            return "";
        }
    }

}
