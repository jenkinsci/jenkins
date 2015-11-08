package hudson.cli

import hudson.Functions
import hudson.remoting.Channel
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy
import org.codehaus.groovy.runtime.Security218
import org.junit.Assert;
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.recipes.PresetData;
import org.jvnet.hudson.test.recipes.PresetData.DataSet;

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import static org.junit.Assert.fail

/**
 * @author Kohsuke Kawaguchi
 * @author christ66
 */
class CLIActionTest {
    @Rule
    public JenkinsRule j = new JenkinsRule()

    ExecutorService pool;

    /**
     * Makes sure that the /cli endpoint is functioning.
     */
    @Test
    public void testDuplexHttp() {
        pool = Executors.newCachedThreadPool()
        try {
            FullDuplexHttpStream con = new FullDuplexHttpStream(new URL(j.URL,"cli"));
            Channel ch = new Channel("test connection", pool, con.inputStream, con.outputStream);
            ch.close();
        } finally {
            pool.shutdown();
        }
    }

    @Test
    public void security218() throws Exception {
        pool = Executors.newCachedThreadPool()
        try {
            FullDuplexHttpStream con = new FullDuplexHttpStream(new URL(j.URL, "cli"));
            Channel ch = new Channel("test connection", pool, con.inputStream, con.outputStream);
            ch.call(new Security218());
            fail("Expected the call to be rejected");
        } catch (Exception e) {
            assert Functions.printThrowable(e).contains("Rejected: "+Security218.class.name);
        } finally {
            pool.shutdown();
        }

    }

    @Test
    public void security218_take2() throws Exception {
        pool = Executors.newCachedThreadPool()
        try {
            new CLI(j.URL).execute([new Security218()]);
            fail("Expected the call to be rejected");
        } catch (Exception e) {
            assert Functions.printThrowable(e).contains("Rejected: "+Security218.class.name);
        } finally {
            pool.shutdown();
        }
    }

    //TODO: Integrate the tests into existing ones in CLIActionTest2
    @Test
    @PresetData(DataSet.NO_ANONYMOUS_READACCESS)
    public void serveCliActionToAnonymousUserWithoutPermissions() throws Exception {
        def wc = j.createWebClient();
        // The behavior changed due to SECURITY-192. index page is no longer accessible to anonymous
        wc.assertFails("cli", HttpURLConnection.HTTP_FORBIDDEN);
    }
    
    @Test
    public void serveCliActionToAnonymousUser() throws Exception {
        def wc = j.createWebClient();
        wc.goTo("cli");
    }
}
