package hudson;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeFalse;

import hudson.Launcher.LocalLauncher;
import hudson.Launcher.RemoteLauncher;
import hudson.Launcher.RemoteLauncher.ProcImpl;
import hudson.model.TaskListener;
import hudson.remoting.Pipe;
import hudson.remoting.VirtualChannel;
import hudson.slaves.DumbSlave;
import hudson.util.IOUtils;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import jenkins.security.MasterToSlaveCallable;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Kohsuke Kawaguchi
 */
@Issue("JENKINS-7809")
public class ProcTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Makes sure that the output flushing and {@link ProcImpl#join()} is synced.
     */
    @Test
    public void remoteProcOutputSync() throws Exception {
        VirtualChannel ch = createSlaveChannel();

        // keep the pipe fairly busy
        final Pipe p = Pipe.createRemoteToLocal();
        for (int i = 0; i < 10; i++)
            ch.callAsync(new ChannelFiller(p.getOut()));
        new Thread(() -> {
            try {
                IOUtils.drain(p.getIn());
            } catch (IOException e) {
            }
        }).start();

        RemoteLauncher launcher = new RemoteLauncher(TaskListener.NULL, ch, !Functions.isWindows());

        StringBuilder str = new StringBuilder();
        str.append("oxox".repeat(256));

        for (int i = 0; i < 1000; i++) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if (Functions.isWindows()) {
                launcher.launch().cmds(new String[] {"cmd", "/c", "echo ", str.toString()}).stdout(baos).join();
            }
            else {
                launcher.launch().cmds("echo", str.toString()).stdout(baos).join();
            }
            assertEquals(str.toString(), baos.toString(Charset.defaultCharset()).trim());
        }

        ch.close();
    }

    private VirtualChannel createSlaveChannel() throws Exception {
        DumbSlave s = j.createSlave();
        s.toComputer().connect(false).get();
        VirtualChannel ch = null;
        while (ch == null) {
            ch = s.toComputer().getChannel();
            Thread.sleep(100);
        }
        return ch;
    }

    private static class ChannelFiller extends MasterToSlaveCallable<Void, IOException> {
        private final OutputStream o;

        private ChannelFiller(OutputStream o) {
            this.o = o;
        }

        @Override
        public Void call() throws IOException {
            while (!Thread.interrupted()) {
                o.write(new byte[256]);
            }
            return null;
        }
    }

    @Test
    public void ioPumpingWithLocalLaunch() throws Exception {
        assumeFalse("TODO: Implement this test for Windows", Functions.isWindows());
        doIoPumpingTest(new LocalLauncher(new StreamTaskListener(System.out, Charset.defaultCharset())));
    }

    @Test
    public void ioPumpingWithRemoteLaunch() throws Exception {
        assumeFalse("TODO: Implement this test for Windows", Functions.isWindows());
        doIoPumpingTest(new RemoteLauncher(
                new StreamTaskListener(System.out, Charset.defaultCharset()),
                createSlaveChannel(), true));
    }

    private void doIoPumpingTest(Launcher l) throws IOException, InterruptedException {
        String[] ECHO_BACK_CMD = {"cat"};   // TODO: what is the echo back command for Windows? "cmd /C copy CON CON"?

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        l.launch().cmds(ECHO_BACK_CMD).stdin(new ByteArrayInputStream("Hello".getBytes(Charset.defaultCharset()))).stdout(out).join();
        assertEquals("Hello", out.toString(Charset.defaultCharset()));

        Proc p = l.launch().cmds(ECHO_BACK_CMD).stdin(new ByteArrayInputStream("Hello".getBytes(Charset.defaultCharset()))).readStdout().start();
        p.join();
        assertEquals("Hello", org.apache.commons.io.IOUtils.toString(p.getStdout(), Charset.defaultCharset()));
        assertNull(p.getStderr());
        assertNull(p.getStdin());


        p = l.launch().cmds(ECHO_BACK_CMD).writeStdin().readStdout().start();
        p.getStdin().write("Hello".getBytes(Charset.defaultCharset()));
        p.getStdin().close();
        p.join();
        assertEquals("Hello", org.apache.commons.io.IOUtils.toString(p.getStdout(), Charset.defaultCharset()));
        assertNull(p.getStderr());
    }
}
