package hudson.cli

import hudson.remoting.Channel
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;

import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.recipes.PresetData;
import org.jvnet.hudson.test.recipes.PresetData.DataSet;

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
