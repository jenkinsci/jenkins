package hudson.slaves;

import hudson.Util;
import hudson.FilePath;
import hudson.Proc;
import hudson.util.ArgumentListBuilder;
import hudson.remoting.Callable;
import hudson.remoting.Which;
import hudson.model.Node.Mode;
import hudson.model.Slave;
import hudson.model.Computer;
import org.jvnet.hudson.test.HudsonTestCase;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.net.URL;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;

/**
 * @author Kohsuke Kawaguchi
 */
public class JNLPLauncherTest extends HudsonTestCase {
    /**
     * Starts a JNLP slave agent and makes sure it successfully connects to Hudson. 
     */
    public void testLaunch() throws Exception {
        List<Slave> slaves = new ArrayList<Slave>(hudson.getSlaves());
        File dir = Util.createTempDir();
        slaves.add(new Slave("test","dummy",dir.getAbsolutePath(),"1", Mode.NORMAL, "",
                new JNLPLauncher(), RetentionStrategy.INSTANCE));
        hudson.setSlaves(slaves);
        Computer c = hudson.getComputer("test");
        assertNotNull(c);

        HtmlPage p = new WebClient().goTo("computer/test/");
        String href = ((HtmlAnchor) p.getElementById("jnlp-link")).getHrefAttribute();
        href = new URL(p.getWebResponse().getUrl(),href).toExternalForm();

        // launch slave agent
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("java","-jar");
        args.add(Which.jarFile(netx.jnlp.runtime.JNLPRuntime.class).getAbsolutePath());
        args.add("-nosecurity","-jnlp",href);
        Proc proc = createLocalLauncher().launch(args.toCommandArray(),
                new String[0], System.out, new FilePath(new File(".")));

        try {
        
            // verify that the connection is established, up to 10 secs
            for( int i=0; i<100; i++ ) {
                Thread.sleep(100);
                if(!c.isOffline())
                    break;
            }

            assertFalse("Slave failed to go online", c.isOffline());
            // run some trivial thing
            c.getChannel().call(new NoopTask());
        } finally {
            proc.kill();
        }

        Thread.sleep(500);
        assertTrue(c.isOffline());
    }

    private static class NoopTask implements Callable<String,RuntimeException> {
        public String call() {
            return null;
        }

        private static final long serialVersionUID = 1L;
    }
}
