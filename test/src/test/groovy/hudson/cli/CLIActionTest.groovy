package hudson.cli

import hudson.remoting.Channel
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.JenkinsRule

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 *
 *
 * @author Kohsuke Kawaguchi
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
}
