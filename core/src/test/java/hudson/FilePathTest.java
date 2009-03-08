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
package hudson;

import junit.framework.TestCase;
import hudson.remoting.Channel;
import hudson.util.NullStream;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.junit.Assert;

/**
 * @author Kohsuke Kawaguchi
 */
public class FilePathTest extends TestCase {
    /**
     * Two channels that are connected to each other, but shares the same classloader. 
     */
    private Channel french, british;
    private ExecutorService executors = Executors.newCachedThreadPool();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
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
        french = f1.get();
        british = f2.get();
    }

    @Override
    protected void tearDown() throws Exception {
        french.close(); // this will automatically initiate the close on the other channel, too.
        french.join();
        british.join();
        executors.shutdown();
    }

    public void testCopyTo() throws Exception {
        File tmp = File.createTempFile("testCopyTo","");
        FilePath f = new FilePath(french,tmp.getPath());
        f.copyTo(new NullStream());
        Assert.assertTrue("target does not exist", tmp.exists());
        Assert.assertTrue("could not delete target " + tmp.getPath(), tmp.delete());
    }

    /**
     * An attempt to reproduce the file descriptor leak.
     * If this operation leaks a file descriptor, 2500 should be enough, I think.
     */
    public void testCopyTo2() throws Exception {
        for (int j=0; j<2500; j++) {
            File tmp = File.createTempFile("testCopyTo","");
            FilePath f = new FilePath(tmp);
            File tmp2 = File.createTempFile("testCopyTo","");
            FilePath f2 = new FilePath(british,tmp.getPath());

            f.copyTo(f2);

            Assert.assertTrue("could not delete target " + tmp.getPath(), tmp.delete());
            Assert.assertTrue("could not delete target " + tmp2.getPath(), tmp2.delete());
        }
    }
}
