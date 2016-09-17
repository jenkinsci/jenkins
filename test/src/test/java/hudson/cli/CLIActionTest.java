package hudson.cli;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import hudson.Functions;
import hudson.remoting.Channel;
import hudson.remoting.ChannelBuilder;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.codehaus.groovy.runtime.Security218;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.PresetData;
import org.jvnet.hudson.test.recipes.PresetData.DataSet;

public class CLIActionTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

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
        try {
            List/*<String>*/ commands = new ArrayList();
            commands.add(new Security218());
            new CLI(j.getURL()).execute(commands);
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

}
