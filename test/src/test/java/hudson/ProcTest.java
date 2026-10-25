package hudson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Kohsuke Kawaguchi
 */
@Issue("JENKINS-7809")
@WithJenkins
class ProcTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Makes sure that the output flushing and {@link ProcImpl#join()} is synced.
     */
    @Test
    void remoteProcOutputSync() throws Exception {
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
                launcher.launch().cmds("cmd", "/c", "echo ", str.toString()).stdout(baos).join();
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
    void ioPumpingWithLocalLaunch() throws Exception {
        assumeFalse(Functions.isWindows(), "TODO: Implement this test for Windows");
        doIoPumpingTest(new LocalLauncher(new StreamTaskListener(System.out, Charset.defaultCharset())));
    }

    @Test
    void ioPumpingWithRemoteLaunch() throws Exception {
        assumeFalse(Functions.isWindows(), "TODO: Implement this test for Windows");
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
