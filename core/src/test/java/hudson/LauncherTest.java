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

import hudson.util.ProcessTree;
import hudson.util.StreamTaskListener;
import hudson.remoting.Callable;
import org.apache.commons.io.FileUtils;

import java.io.File;

public class LauncherTest extends ChannelTestCase {
    //@Bug(4611)
    public void testRemoteKill() throws Exception {
        if (File.pathSeparatorChar != ':') {
            System.err.println("Skipping, currently Unix-specific test");
            return;
        }

        File tmp = File.createTempFile("testRemoteKill", "");
        tmp.delete();

        try {
            FilePath f = new FilePath(french, tmp.getPath());
            Launcher l = f.createLauncher(StreamTaskListener.fromStderr());
            Proc p = l.launch().cmds("sh", "-c", "echo $$$$ > "+tmp+"; sleep 30").stdout(System.out).stderr(System.err).start();
            while (!tmp.exists())
                Thread.sleep(100);
            long start = System.currentTimeMillis();
            p.kill();
            assertTrue(p.join()!=0);
            long end = System.currentTimeMillis();
            assertTrue("join finished promptly", (end - start < 5000));
            french.call(NOOP); // this only returns after the other side of the channel has finished executing cancellation
            Thread.sleep(2000); // more delay to make sure it's gone
            assertNull("process should be gone",ProcessTree.get().get(Integer.parseInt(FileUtils.readFileToString(tmp).trim())));

            // Manual version of test: set up instance w/ one slave. Now in script console
            // new hudson.FilePath(new java.io.File("/tmp")).createLauncher(new hudson.util.StreamTaskListener(System.err)).
            //   launch().cmds("sleep", "1d").stdout(System.out).stderr(System.err).start().kill()
            // returns immediately and pgrep sleep => nothing. But without fix
            // hudson.model.Hudson.instance.nodes[0].rootPath.createLauncher(new hudson.util.StreamTaskListener(System.err)).
            //   launch().cmds("sleep", "1d").stdout(System.out).stderr(System.err).start().kill()
            // hangs and on slave machine pgrep sleep => one process; after manual kill, script returns.
        } finally {
            tmp.delete();
        }
    }

    private static final Callable<Object,RuntimeException> NOOP = new Callable() {
        public Object call() throws Exception {
            return null;
        }
    };
}
