/*
 * The MIT License
 *
 * Copyright 2009 Sun Microsystems, Inc., Kohsuke Kawaguchi, Jesse Glick.
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

package hudson;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.util.ProcessTree;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import jenkins.security.MasterToSlaveCallable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;

public class LauncherTest {

    @Rule public ChannelRule channels = new ChannelRule();
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Issue("JENKINS-4611")
    @Test public void remoteKill() throws Exception {
        assumeFalse(Functions.isWindows());

        File tmp = temp.newFile();

            FilePath f = new FilePath(channels.french, tmp.getPath());
            Launcher l = f.createLauncher(StreamTaskListener.fromStderr());
            Proc p = l.launch().cmds("sh", "-c", "echo $$$$ > " + tmp + "; sleep 30").stdout(System.out).stderr(System.err).start();
            while (!tmp.exists())
                Thread.sleep(100);
            long start = System.currentTimeMillis();
            p.kill();
            assertNotEquals(0, p.join());
            long end = System.currentTimeMillis();
            long terminationTime = end - start;
            assertTrue("Join did not finish promptly. The completion time (" + terminationTime + "ms) is longer than expected 15s", terminationTime < 15000);
            channels.french.call(new NoopCallable()); // this only returns after the other side of the channel has finished executing cancellation
            Thread.sleep(2000); // more delay to make sure it's gone
            assertNull("process should be gone", ProcessTree.get().get(Integer.parseInt(Files.readString(tmp.toPath(), Charset.defaultCharset()).trim())));

            // Manual version of test: set up instance w/ one agent. Now in script console
            // new hudson.FilePath(new java.io.File("/tmp")).createLauncher(new hudson.util.StreamTaskListener(System.err)).
            //   launch().cmds("sleep", "1d").stdout(System.out).stderr(System.err).start().kill()
            // returns immediately and pgrep sleep => nothing. But without fix
            // hudson.model.Hudson.instance.nodes[0].rootPath.createLauncher(new hudson.util.StreamTaskListener(System.err)).
            //   launch().cmds("sleep", "1d").stdout(System.out).stderr(System.err).start().kill()
            // hangs and on agent machine pgrep sleep => one process; after manual kill, script returns.
    }

    private static class NoopCallable extends MasterToSlaveCallable<Object, RuntimeException> {
        @Override
        public Object call() throws RuntimeException {
            return null;
        }
    }

    @Issue("JENKINS-15733")
    @Test public void decorateByEnv() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        TaskListener l = new StreamBuildListener(baos, Charset.defaultCharset());
        Launcher base = new Launcher.LocalLauncher(l);
        EnvVars env = new EnvVars("key1", "val1");
        Launcher decorated = base.decorateByEnv(env);
        int res = decorated.launch().envs("key2=val2").cmds(Functions.isWindows() ? new String[] {"cmd", "/q", "/c", "echo %key1% %key2%"} : new String[] {"sh", "-c", "echo $key1 $key2"}).stdout(l).join();
        String log = baos.toString(Charset.defaultCharset());
        assertEquals(log, 0, res);
        assertTrue(log, log.contains("val1 val2"));
    }

    @Issue("JENKINS-18368")
    @Test public void decoratedByEnvMaintainsIsUnix() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TaskListener listener = new StreamBuildListener(output, Charset.defaultCharset());
        Launcher remoteLauncher = new Launcher.RemoteLauncher(listener, FilePath.localChannel, false);
        Launcher decorated = remoteLauncher.decorateByEnv(new EnvVars());
        assertFalse(decorated.isUnix());
        remoteLauncher = new Launcher.RemoteLauncher(listener, FilePath.localChannel, true);
        decorated = remoteLauncher.decorateByEnv(new EnvVars());
        assertTrue(decorated.isUnix());
    }

    @Issue("JENKINS-18368")
    @Test public void decoratedByPrefixMaintainsIsUnix() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TaskListener listener = new StreamBuildListener(output, Charset.defaultCharset());
        Launcher remoteLauncher = new Launcher.RemoteLauncher(listener, FilePath.localChannel, false);
        Launcher decorated = remoteLauncher.decorateByPrefix("test");
        assertFalse(decorated.isUnix());
        remoteLauncher = new Launcher.RemoteLauncher(listener, FilePath.localChannel, true);
        decorated = remoteLauncher.decorateByPrefix("test");
        assertTrue(decorated.isUnix());
    }

}
