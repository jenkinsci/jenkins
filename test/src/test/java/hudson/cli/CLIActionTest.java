package hudson.cli;

import com.google.common.collect.Lists;
import hudson.Functions;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AllView;
import hudson.model.Item;
import hudson.model.User;
import hudson.remoting.Channel;
import hudson.remoting.ChannelBuilder;
import hudson.util.ProcessTree;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;
import jenkins.security.apitoken.ApiTokenPropertyConfiguration;
import jenkins.util.FullDuplexHttpService;
import jenkins.util.Timer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.TeeOutputStream;
import org.codehaus.groovy.runtime.Security218;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.PresetData;
import org.jvnet.hudson.test.recipes.PresetData.DataSet;

public class CLIActionTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();
    { // authentication() can take a while on a loaded machine
        j.timeout = System.getProperty("maven.surefire.debug") == null ? 300 : 0;
    }

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Rule
    public LoggerRule logging = new LoggerRule();

    private ExecutorService pool;

    /**
     * Makes sure that the /cli endpoint is functioning.
     */
    @Test
    public void testDuplexHttp() throws Exception {
        pool = Executors.newCachedThreadPool();
        try {
            @SuppressWarnings("deprecation") // to verify compatibility of original constructor
            FullDuplexHttpStream con = new FullDuplexHttpStream(new URL(j.getURL(), "cli"), null);
            Channel ch = new ChannelBuilder("test connection", pool).build(con.getInputStream(), con.getOutputStream());
            ch.close();
        } finally {
            pool.shutdown();
        }
    }

    @Test
    public void security218() throws Exception {
        pool = Executors.newCachedThreadPool();
        try {
            FullDuplexHttpStream con = new FullDuplexHttpStream(j.getURL(), "cli", null);
            Channel ch = new ChannelBuilder("test connection", pool).build(con.getInputStream(), con.getOutputStream());
            ch.call(new Security218());
            fail("Expected the call to be rejected");
        } catch (Exception e) {
            assertThat(Functions.printThrowable(e), containsString("Rejected: " + Security218.class.getName()));
        } finally {
            pool.shutdown();
        }

    }

    @SuppressWarnings({"unchecked", "rawtypes", "deprecation"}) // intentionally passing an unreifiable argument here; Remoting-based constructor intentional
    @Test
    public void security218_take2() throws Exception {
        pool = Executors.newCachedThreadPool();
        try (CLI cli = new CLI(j.getURL())) {
            List/*<String>*/ commands = new ArrayList();
            commands.add(new Security218());
            cli.execute(commands);
            fail("Expected the call to be rejected");
        } catch (Exception e) {
            assertThat(Functions.printThrowable(e), containsString("Rejected: " + Security218.class.getName()));
        } finally {
            pool.shutdown();
        }
    }

    @Test
    @PresetData(DataSet.NO_ANONYMOUS_READACCESS)
    @Issue("SECURITY-192")
    public void serveCliActionToAnonymousUserWithoutPermissions() throws Exception {
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
        ApiTokenPropertyConfiguration.get().setTokenGenerationOnCreationEnabled(true);
        
        logging.record(PlainCLIProtocol.class, Level.FINE);
        File jar = tmp.newFile("jenkins-cli.jar");
        FileUtils.copyURLToFile(j.jenkins.getJnlpJars("jenkins-cli.jar").getURL(), jar);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to(ADMIN));
        j.createFreeStyleProject("p");
        // CLICommand with @Argument:
        assertExitCode(6, false, jar, "-remoting", "get-job", "p"); // SECURITY-754 requires Overall/Read for nearly all CLICommands.
        assertExitCode(6, false, jar, "get-job", "p"); // ditto under new protocol
        assertExitCode(6, false, jar, "-remoting", "get-job", "--username", ADMIN, "--password", ADMIN, "p"); // SECURITY-754 and JENKINS-12543: too late
        assertExitCode(6, false, jar, "get-job", "--username", ADMIN, "--password", ADMIN, "p"); // same
        assertExitCode(0, false, jar, "-remoting", "login", "--username", ADMIN, "--password", ADMIN);
        try {
            assertExitCode(6, false, jar, "-remoting", "get-job", "p"); // SECURITY-754: ClientAuthenticationCache also used too late
        } finally {
            assertExitCode(0, false, jar, "-remoting", "logout");
        }
        assertExitCode(6, true, jar, "-remoting", "get-job", "p"); // SECURITY-754: does not work with API tokens
        assertExitCode(0, true, jar, "get-job", "p"); // but does under new protocol
        // @CLIMethod:
        assertExitCode(6, false, jar, "-remoting", "disable-job", "p"); // AccessDeniedException from CLIRegisterer?
        assertExitCode(6, false, jar, "disable-job", "p");
        assertExitCode(0, false, jar, "-remoting", "disable-job", "--username", ADMIN, "--password", ADMIN, "p"); // works from CliAuthenticator
        assertExitCode(0, false, jar, "disable-job", "--username", ADMIN, "--password", ADMIN, "p");
        assertExitCode(0, false, jar, "-remoting", "login", "--username", ADMIN, "--password", ADMIN);
        try {
            assertExitCode(0, false, jar, "-remoting", "disable-job", "p"); // or from ClientAuthenticationCache
        } finally {
            assertExitCode(0, false, jar, "-remoting", "logout");
        }
        assertExitCode(6, true, jar, "-remoting", "disable-job", "p");
        assertExitCode(0, true, jar, "disable-job", "p");
        // If we have anonymous read access, then the situation is simpler.
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to(ADMIN).grant(Jenkins.READ, Item.READ).everywhere().toEveryone());
        assertExitCode(6, false, jar, "-remoting", "get-job", "p"); // AccessDeniedException from AbstractItem.writeConfigDotXml
        assertExitCode(6, false, jar, "get-job", "p");
        assertExitCode(0, false, jar, "-remoting", "get-job", "--username", ADMIN, "--password", ADMIN, "p");
        assertExitCode(0, false, jar, "get-job", "--username", ADMIN, "--password", ADMIN, "p");
        assertExitCode(0, false, jar, "-remoting", "login", "--username", ADMIN, "--password", ADMIN);
        try {
            assertExitCode(0, false, jar, "-remoting", "get-job", "p");
        } finally {
            assertExitCode(0, false, jar, "-remoting", "logout");
        }
        assertExitCode(6, true, jar, "-remoting", "get-job", "p"); // does not work with API tokens
        assertExitCode(0, true, jar, "get-job", "p"); // but does under new protocol
        assertExitCode(6, false, jar, "-remoting", "disable-job", "p"); // AccessDeniedException from AbstractProject.doDisable
        assertExitCode(6, false, jar, "disable-job", "p");
        assertExitCode(0, false, jar, "-remoting", "disable-job", "--username", ADMIN, "--password", ADMIN, "p");
        assertExitCode(0, false, jar, "disable-job", "--username", ADMIN, "--password", ADMIN, "p");
        assertExitCode(0, false, jar, "-remoting", "login", "--username", ADMIN, "--password", ADMIN);
        try {
            assertExitCode(0, false, jar, "-remoting", "disable-job", "p");
        } finally {
            assertExitCode(0, false, jar, "-remoting", "logout");
        }
        assertExitCode(6, true, jar, "-remoting", "disable-job", "p");
        assertExitCode(0, true, jar, "disable-job", "p");
        // Show that API tokens do work in Remoting-over-HTTP mode (just not over the JNLP port):
        j.jenkins.setSlaveAgentPort(-1);
        assertExitCode(0, true, jar, "-remoting", "get-job", "p");
        assertExitCode(0, true, jar, "-remoting", "disable-job", "p");
    }

    private static final String ADMIN = "admin@mycorp.com";

    private void assertExitCode(int code, boolean useApiToken, File jar, String... args) throws IOException, InterruptedException {
        List<String> commands = Lists.newArrayList("java", "-jar", jar.getAbsolutePath(), "-s", j.getURL().toString(), /* not covering SSH keys in this test */ "-noKeyAuth");
        if (useApiToken) {
            commands.add("-auth");
            commands.add(ADMIN + ":" + User.get(ADMIN).getProperty(ApiTokenProperty.class).getApiToken());
        }
        commands.addAll(Arrays.asList(args));
        final Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(StreamTaskListener.fromStderr());
        final Proc proc = launcher.launch().cmds(commands).stdout(System.out).stderr(System.err).start();
        if (!Functions.isWindows()) {
            // Try to get a thread dump of the client if it hangs.
            Timer.get().schedule(new Runnable() {
                @Override
                public void run() {
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
                }
            }, 1, TimeUnit.MINUTES);
        }
        assertEquals(code, proc.join());
    }

    @Issue("JENKINS-41745")
    @Test
    public void encodingAndLocale() throws Exception {
        File jar = tmp.newFile("jenkins-cli.jar");
        FileUtils.copyURLToFile(j.jenkins.getJnlpJars("jenkins-cli.jar").getURL(), jar);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertEquals(0, new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(
            "java", "-Dfile.encoding=ISO-8859-2", "-Duser.language=cs", "-Duser.country=CZ", "-jar", jar.getAbsolutePath(),
                "-s", j.getURL().toString()./* just checking */replaceFirst("/$", ""), "-noKeyAuth", "test-diagnostic").
            stdout(baos).stderr(System.err).join());
        assertEquals("encoding=ISO-8859-2 locale=cs_CZ", baos.toString().trim());
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
        PrintWriter pw = new PrintWriter(new TeeOutputStream(pos, System.err), true);
        Proc proc = new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(
            "java", "-jar", jar.getAbsolutePath(), "-s", j.getURL().toString(), "-noKeyAuth", "groovysh").
            stdout(new TeeOutputStream(baos, System.out)).stderr(System.err).stdin(pis).start();
        while (!baos.toString().contains("000")) { // cannot just search for, say, "groovy:000> " since there are ANSI escapes there (cf. StringEscapeUtils.escapeJava)
            Thread.sleep(100);
        }
        pw.println("11 * 11");
        while (!baos.toString().contains("121")) { // ditto not "===> 121"
            Thread.sleep(100);
        }
        Thread.sleep(31_000); // aggravate org.eclipse.jetty.io.IdleTimeout (cf. AbstractConnector._idleTimeout)
        pw.println("11 * 11 * 11");
        while (!baos.toString().contains("1331")) {
            Thread.sleep(100);
        }
        pw.println(":q");
        assertEquals(0, proc.join());
    }

    @Test
    @Issue("JENKINS-50324")
    public void userWithoutReadCanLogout() throws Exception {
        String userWithRead = "userWithRead";
        String userWithoutRead = "userWithoutRead";
        
        File jar = tmp.newFile("jenkins-cli.jar");
        FileUtils.copyURLToFile(j.jenkins.getJnlpJars("jenkins-cli.jar").getURL(), jar);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to(ADMIN)
                .grant(Jenkins.READ).everywhere().to(userWithRead)
                // nothing to userWithoutRead
        );
    
        checkCanLogout(jar, ADMIN);
        checkCanLogout(jar, userWithRead);
        checkCanLogout(jar, userWithoutRead);
    }
    
    private void checkCanLogout(File cliJar, String userLoginAndPassword) throws Exception {
        assertExitCode(0, false, cliJar, "-remoting", "login", "--username", userLoginAndPassword, "--password", userLoginAndPassword);
        assertExitCode(0, false, cliJar, "-remoting", "who-am-i");
        assertExitCode(0, false, cliJar, "-remoting", "logout");
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

}
