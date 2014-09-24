/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.slaves;

import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.Proc;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Node.Mode;
import hudson.model.Slave;
import hudson.remoting.Callable;
import hudson.remoting.Which;
import hudson.util.ArgumentListBuilder;

import jenkins.security.SlaveToMasterCallable;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestExtension;

import javax.inject.Inject;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.awt.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class JNLPLauncherTest extends HudsonTestCase {
    /**
     * Starts a JNLP slave agent and makes sure it successfully connects to Hudson. 
     */
    public void testLaunch() throws Exception {
        if(GraphicsEnvironment.isHeadless()) {
            System.err.println("Skipping JNLPLauncherTest.testLaunch because we are running headless");
            return;
        }

        System.err.println("Not in headless mode, continuing with JNLPLauncherTest.testLaunch...");
        Computer c = addTestSlave();
        launchJnlpAndVerify(c, buildJnlpArgs(c));
    }

    /**
     * Tests the '-headless' option.
     * (Although this test doesn't really assert that the agent really is running in a headless mdoe.)
     */
    public void testHeadlessLaunch() throws Exception {
        Computer c = addTestSlave();
        launchJnlpAndVerify(c, buildJnlpArgs(c).add("-arg","-headless"));
        // make sure that onOffline gets called just the right number of times
        assertEquals(1, listener.offlined);
    }

    @Inject
    ListenerImpl listener;

    @TestExtension
    public static class ListenerImpl extends ComputerListener {
        int offlined = 0;

        @Override
        public void onOffline(Computer c) {
            offlined++;
            assertTrue(c.isOffline());
        }
    }
    
    
    private ArgumentListBuilder buildJnlpArgs(Computer c) throws Exception {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(new File(new File(System.getProperty("java.home")),"bin/java").getPath(),"-jar");
        args.add(Which.jarFile(netx.jnlp.runtime.JNLPRuntime.class).getAbsolutePath());
        args.add("-headless","-basedir");
        args.add(createTmpDir());
        args.add("-nosecurity","-jnlp", getJnlpLink(c));
        return args;
    }

    /**
     * Launches the JNLP slave agent and asserts its basic operations.
     */
    private void launchJnlpAndVerify(Computer c, ArgumentListBuilder args) throws Exception {
        Proc proc = createLocalLauncher().launch().cmds(args).stdout(System.out).pwd(".").start();

        try {
            // verify that the connection is established, up to 20 secs
            for( int i=0; i<200; i++ ) {
                Thread.sleep(100);
                if(!c.isOffline())
                    break;
            }

            if (c.isOffline()) {
                System.out.println(c.getLog());
                fail("Slave failed to go online");
            }
            // run some trivial thing
            System.err.println("Calling task...");
            assertEquals("done", c.getChannel().callAsync(new NoopTask()).get(5 * 60, TimeUnit.SECONDS));
            System.err.println("...done.");
        } finally {
            proc.kill();
        }

        Thread.sleep(500);
        assertTrue(c.isOffline());
    }

    /**
     * Determines the link to the .jnlp file.
     */
    private String getJnlpLink(Computer c) throws Exception {
        HtmlPage p = new WebClient().goTo("computer/"+c.getName()+"/");
        String href = ((HtmlAnchor) p.getElementById("jnlp-link")).getHrefAttribute();
        href = new URL(new URL(p.getDocumentURI()),href).toExternalForm();
        return href;
    }

    /**
     * Adds a JNLP {@link Slave} to the system and returns it.
     */
    private Computer addTestSlave() throws Exception {
        List<Node> slaves = new ArrayList<Node>(jenkins.getNodes());
        File dir = Util.createTempDir();
        slaves.add(new DumbSlave("test","dummy",dir.getAbsolutePath(),"1", Mode.NORMAL, "",
                new JNLPLauncher(), RetentionStrategy.INSTANCE, new ArrayList<NodeProperty<?>>()));
        jenkins.setNodes(slaves);
        Computer c = jenkins.getComputer("test");
        assertNotNull(c);
        return c;
    }

    private static class NoopTask extends SlaveToMasterCallable<String,RuntimeException> {
        public String call() {
            return "done";
        }

        private static final long serialVersionUID = 1L;
    }

    public void testConfigRoundtrip() throws Exception {
        DumbSlave s = createSlave();
        JNLPLauncher original = new JNLPLauncher("a", "b");
        s.setLauncher(original);
        HtmlPage p = new WebClient().getPage(s, "configure");
        submit(p.getFormByName("config"));
        assertEqualBeans(original,s.getLauncher(),"tunnel,vmargs");
    }
}
