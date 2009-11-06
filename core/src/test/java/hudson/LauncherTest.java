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

import hudson.remoting.Channel;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import junit.framework.TestCase;

public class LauncherTest extends TestCase {

    public LauncherTest(String n) {
        super(n);
    }

    //@Bug(4611)
    public void testRemoteKill() throws Exception {
        if (File.pathSeparatorChar != ':') {
            System.err.println("Skipping, currently Unix-specific test");
            return;
        }

        // XXX setup stolen from FilePathTest; should it be refactored into common test utility?
        final ExecutorService executors = Executors.newCachedThreadPool();
        final PipedInputStream p1i = new PipedInputStream();
        final PipedInputStream p2i = new PipedInputStream();
        final PipedOutputStream p1o = new PipedOutputStream(p1i);
        final PipedOutputStream p2o = new PipedOutputStream(p2i);
        Future<Channel> f1 = executors.submit(new Callable<Channel>() {
            public Channel call() throws Exception {
                return new Channel("This side of the channel", executors, p1i, p2o);
            }
        });
        Future<Channel> f2 = executors.submit(new Callable<Channel>() {
            public Channel call() throws Exception {
                return new Channel("The other side of the channel", executors, p2i, p1o);
            }
        });
        Channel french = f1.get();
        Channel british = f2.get();

        File tmp = File.createTempFile("testRemoteKill", "");
        FilePath f = new FilePath(french, tmp.getPath());
        Launcher l = f.createLauncher(new StreamTaskListener(System.err));
        Proc p = l.launch().cmds("sh", "-c", "sleep 2; rm " + tmp.getPath()).stdout(System.out).stderr(System.err).start();
        long start = System.currentTimeMillis();
        p.kill();
        assertTrue(p.join()!=0);
        long end = System.currentTimeMillis();
        assertTrue("join finished promptly", (end - start < 1000));
        Thread.sleep(2500);
        assertTrue("was in fact killed before completion", tmp.isFile());

        // Manual version of test: set up instance w/ one slave. Now in script console
        // new hudson.FilePath(new java.io.File("/tmp")).createLauncher(new hudson.util.StreamTaskListener(System.err)).
        //   launch().cmds("sleep", "1d").stdout(System.out).stderr(System.err).start().kill()
        // returns immediately and pgrep sleep => nothing. But without fix
        // hudson.model.Hudson.instance.nodes[0].rootPath.createLauncher(new hudson.util.StreamTaskListener(System.err)).
        //   launch().cmds("sleep", "1d").stdout(System.out).stderr(System.err).start().kill()
        // hangs and on slave machine pgrep sleep => one process; after manual kill, script returns.
    }

}
