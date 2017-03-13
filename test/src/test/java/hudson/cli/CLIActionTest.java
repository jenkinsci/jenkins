package hudson.cli;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.google.common.collect.Lists;
import hudson.Functions;
import hudson.Launcher;
import hudson.model.Item;
import hudson.model.User;
import hudson.remoting.Channel;
import hudson.remoting.ChannelBuilder;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jenkins.model.Jenkins;
import jenkins.security.ApiTokenProperty;
import org.apache.commons.io.FileUtils;
import org.codehaus.groovy.runtime.Security218;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.recipes.PresetData;
import org.jvnet.hudson.test.recipes.PresetData.DataSet;

public class CLIActionTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private ExecutorService pool;

    /**
     * Makes sure that the /cli endpoint is functioning.
     */
    @Test
    public void testDuplexHttp() throws Exception {
        pool = Executors.newCachedThreadPool();
        try {
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
            FullDuplexHttpStream con = new FullDuplexHttpStream(new URL(j.getURL(), "cli"), null);
            Channel ch = new ChannelBuilder("test connection", pool).build(con.getInputStream(), con.getOutputStream());
            ch.call(new Security218());
            fail("Expected the call to be rejected");
        } catch (Exception e) {
            assertThat(Functions.printThrowable(e), containsString("Rejected: " + Security218.class.getName()));
        } finally {
            pool.shutdown();
        }

    }

    @SuppressWarnings({"unchecked", "rawtypes"}) // intentionally passing an unreifiable argument here
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
        // so we check the access by emulating the CLI connection post request
        WebRequest settings = new WebRequest(new URL(j.getURL(), "cli"));
        settings.setHttpMethod(HttpMethod.POST);
        settings.setAdditionalHeader("Session", UUID.randomUUID().toString());
        settings.setAdditionalHeader("Side", "download"); // We try to download something to init the duplex channel

        Page page = wc.getPage(settings);
        WebResponse webResponse = page.getWebResponse();
        assertEquals("We expect that the proper POST request from CLI gets processed successfully",
            200, webResponse.getStatusCode());
    }

    @Test
    public void serveCliActionToAnonymousUserWithAnonymousUserWithPermissions() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        wc.goTo("cli");
    }

    @Issue({"JENKINS-12543", "JENKINS-41745"})
    @Test
    public void authentication() throws Exception {
        File jar = tmp.newFile("jenkins-cli.jar");
        FileUtils.copyURLToFile(j.jenkins.getJnlpJars("jenkins-cli.jar").getURL(), jar);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("admin"));
        j.createFreeStyleProject("p");
        // CLICommand with @Argument:
        assertExitCode(3, false, jar, "-remoting", "get-job", "p"); // IllegalArgumentException from GenericItemOptionHandler
        assertExitCode(3, false, jar, "get-job", "p"); // ditto under new protocol
        assertExitCode(3, false, jar, "-remoting", "get-job", "--username", "admin", "--password", "admin", "p"); // JENKINS-12543: too late
        assertExitCode(3, false, jar, "get-job", "--username", "admin", "--password", "admin", "p"); // same
        assertExitCode(0, false, jar, "-remoting", "login", "--username", "admin", "--password", "admin");
        try {
            assertExitCode(3, false, jar, "-remoting", "get-job", "p"); // ClientAuthenticationCache also used too late
        } finally {
            assertExitCode(0, false, jar, "-remoting", "logout");
        }
        assertExitCode(3, true, jar, "-remoting", "get-job", "p"); // does not work with API tokens
        assertExitCode(0, true, jar, "get-job", "p"); // but does under new protocol
        // @CLIMethod:
        assertExitCode(6, false, jar, "-remoting", "disable-job", "p"); // AccessDeniedException from CLIRegisterer?
        assertExitCode(6, false, jar, "disable-job", "p");
        assertExitCode(0, false, jar, "-remoting", "disable-job", "--username", "admin", "--password", "admin", "p"); // works from CliAuthenticator
        assertExitCode(0, false, jar, "disable-job", "--username", "admin", "--password", "admin", "p");
        assertExitCode(0, false, jar, "-remoting", "login", "--username", "admin", "--password", "admin");
        try {
            assertExitCode(0, false, jar, "-remoting", "disable-job", "p"); // or from ClientAuthenticationCache
        } finally {
            assertExitCode(0, false, jar, "-remoting", "logout");
        }
        assertExitCode(6, true, jar, "-remoting", "disable-job", "p");
        assertExitCode(0, true, jar, "disable-job", "p");
        // If we have anonymous read access, then the situation is simpler.
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("admin").grant(Jenkins.READ, Item.READ).everywhere().toEveryone());
        assertExitCode(6, false, jar, "-remoting", "get-job", "p"); // AccessDeniedException from AbstractItem.writeConfigDotXml
        assertExitCode(6, false, jar, "get-job", "p");
        assertExitCode(0, false, jar, "-remoting", "get-job", "--username", "admin", "--password", "admin", "p");
        assertExitCode(0, false, jar, "get-job", "--username", "admin", "--password", "admin", "p");
        assertExitCode(0, false, jar, "-remoting", "login", "--username", "admin", "--password", "admin");
        try {
            assertExitCode(0, false, jar, "-remoting", "get-job", "p");
        } finally {
            assertExitCode(0, false, jar, "-remoting", "logout");
        }
        assertExitCode(6, true, jar, "-remoting", "get-job", "p"); // does not work with API tokens
        assertExitCode(0, true, jar, "get-job", "p"); // but does under new protocol
        assertExitCode(6, false, jar, "-remoting", "disable-job", "p"); // AccessDeniedException from AbstractProject.doDisable
        assertExitCode(6, false, jar, "disable-job", "p");
        assertExitCode(0, false, jar, "-remoting", "disable-job", "--username", "admin", "--password", "admin", "p");
        assertExitCode(0, false, jar, "disable-job", "--username", "admin", "--password", "admin", "p");
        assertExitCode(0, false, jar, "-remoting", "login", "--username", "admin", "--password", "admin");
        try {
            assertExitCode(0, false, jar, "-remoting", "disable-job", "p");
        } finally {
            assertExitCode(0, false, jar, "-remoting", "logout");
        }
        assertExitCode(6, true, jar, "-remoting", "disable-job", "p");
        assertExitCode(0, true, jar, "disable-job", "p");
    }

    private void assertExitCode(int code, boolean useApiToken, File jar, String... args) throws IOException, InterruptedException {
        String url = j.getURL().toString();
        if (useApiToken) {
            url = url.replace("://localhost:", "://admin:" + User.get("admin").getProperty(ApiTokenProperty.class).getApiToken() + "@localhost:");
        }
        List<String> commands = Lists.newArrayList("java", "-jar", jar.getAbsolutePath(), "-s", url, /* not covering SSH keys in this test */ "-noKeyAuth");
        commands.addAll(Arrays.asList(args));
        assertEquals(code, new Launcher.LocalLauncher(StreamTaskListener.fromStderr()).launch().cmds(commands).stdout(System.out).stderr(System.err).join());
    }

}
