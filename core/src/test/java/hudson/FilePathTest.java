/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Alan Harder
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

import hudson.remoting.VirtualChannel;
import hudson.util.IOException2;
import hudson.util.NullStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.jvnet.hudson.test.Bug;

/**
 * @author Kohsuke Kawaguchi
 */
public class FilePathTest extends ChannelTestCase {

    public void testCopyTo() throws Exception {
        File tmp = File.createTempFile("testCopyTo","");
        FilePath f = new FilePath(french,tmp.getPath());
        f.copyTo(new NullStream());
        assertTrue("target does not exist", tmp.exists());
        assertTrue("could not delete target " + tmp.getPath(), tmp.delete());
    }

    /**
     * An attempt to reproduce the file descriptor leak.
     * If this operation leaks a file descriptor, 2500 should be enough, I think.
     */
    public void testCopyTo2() throws Exception {
        for (int j=0; j<2500; j++) {
            File tmp = File.createTempFile("testCopyFrom","");
            FilePath f = new FilePath(tmp);
            File tmp2 = File.createTempFile("testCopyTo","");
            FilePath f2 = new FilePath(british,tmp2.getPath());

            f.copyTo(f2);

            f.delete();
            f2.delete();
        }
    }

    /**
     * As we moved the I/O handling to another thread, there's a race condition in
     * {@link FilePath#copyTo(OutputStream)} &mdash; this method can return before
     * all the writes are delivered to {@link OutputStream}.
     *
     * <p>
     * To reproduce that problem, we use a large number of threads, so that we can
     * maximize the chance of out-of-order execution, and make sure we are
     * seeing the right byte count at the end.
     *
     * Also see JENKINS-7897
     */
    @Bug(7871)
    public void testCopyTo3() throws Exception {
        final File tmp = File.createTempFile("testCopyTo3","");

        FileOutputStream os = new FileOutputStream(tmp);
        final int size = 90000;
        byte[] buf = new byte[size];
        for (int i=0; i<buf.length; i++)
            buf[i] = (byte)(i%256);
        os.write(buf);
        os.close();

        ExecutorService es = Executors.newFixedThreadPool(100);
        try {
            List<java.util.concurrent.Future<Object>> r = new ArrayList<java.util.concurrent.Future<Object>>();
            for (int i=0; i<100; i++) {
                r.add(es.submit(new Callable<Object>() {
                    public Object call() throws Exception {
                        class Sink extends OutputStream {
                            private Exception closed;
                            private volatile int count;

                            private void checkNotClosed() throws IOException2 {
                                if (closed != null)
                                    throw new IOException2(closed);
                            }

                            @Override
                            public void write(int b) throws IOException {
                                count++;
                                checkNotClosed();
                            }

                            @Override
                            public void write(byte[] b) throws IOException {
                                count+=b.length;
                                checkNotClosed();
                            }

                            @Override
                            public void write(byte[] b, int off, int len) throws IOException {
                                count+=len;
                                checkNotClosed();
                            }

                            @Override
                            public void close() throws IOException {
                                closed = new Exception();
                                if (size!=count)
                                    fail();
                            }
                        }

                        FilePath f = new FilePath(french, tmp.getPath());
                        Sink sink = new Sink();
                        f.copyTo(sink);
                        assertEquals(size,sink.count);
                        return null;
                    }
                }));
            }

            for (java.util.concurrent.Future<Object> f : r)
                f.get();
        } finally {
            es.shutdown();
        }
    }



    public void testRepeatCopyRecursiveTo() throws Exception {
        // local->local copy used to return 0 if all files were "up to date"
        // should return number of files processed, whether or not they were copied or already current
        File tmp = Util.createTempDir(), src = new File(tmp, "src"), dst = new File(tmp, "dst");
        try {
            assertTrue(src.mkdir());
            assertTrue(dst.mkdir());
            File.createTempFile("foo", ".tmp", src);
            FilePath fp = new FilePath(src);
            assertEquals(1, fp.copyRecursiveTo(new FilePath(dst)));
            // copy again should still report 1
            assertEquals(1, fp.copyRecursiveTo(new FilePath(dst)));
        } finally {
            Util.deleteRecursive(tmp);
        }
    }

    public void testArchiveBug4039() throws Exception {
        File tmp = Util.createTempDir();
        try {
            FilePath d = new FilePath(french,tmp.getPath());
            d.child("test").touch(0);
            d.zip(new NullOutputStream());
            d.zip(new NullOutputStream(),"**/*");
        } finally {
            Util.deleteRecursive(tmp);
        }
    }

    public void testNormalization() throws Exception {
        compare("abc/def\\ghi","abc/def\\ghi"); // allow mixed separators

        {// basic '.' trimming
            compare("./abc/def","abc/def");
            compare("abc/./def","abc/def");
            compare("abc/def/.","abc/def");

            compare(".\\abc\\def","abc\\def");
            compare("abc\\.\\def","abc\\def");
            compare("abc\\def\\.","abc\\def");
        }

        compare("abc/../def","def");
        compare("abc/def/../../ghi","ghi");
        compare("abc/./def/../././../ghi","ghi");   // interleaving . and ..

        compare("../abc/def","../abc/def");     // uncollapsible ..
        compare("abc/def/..","abc");

        compare("c:\\abc\\..","c:\\");      // we want c:\\, not c:
        compare("c:\\abc\\def\\..","c:\\abc");

        compare("/abc/../","/");
        compare("abc/..",".");
        compare(".",".");

        // @Bug(5951)
        compare("C:\\Hudson\\jobs\\foo\\workspace/../../otherjob/workspace/build.xml",
                "C:\\Hudson\\jobs/otherjob/workspace/build.xml");
        // Other cases that failed before
        compare("../../abc/def","../../abc/def");
        compare("..\\..\\abc\\def","..\\..\\abc\\def");
        compare("/abc//../def","/def");
        compare("c:\\abc\\\\..\\def","c:\\def");
        compare("/../abc/def","/abc/def");
        compare("c:\\..\\abc\\def","c:\\abc\\def");
        compare("abc/def/","abc/def");
        compare("abc\\def\\","abc\\def");
        // The new code can collapse extra separator chars
        compare("abc//def/\\//\\ghi","abc/def/ghi");
        compare("\\\\host\\\\abc\\\\\\def","\\\\host\\abc\\def"); // don't collapse for \\ prefix
        compare("\\\\\\foo","\\\\foo");
        compare("//foo","/foo");
        // Other edge cases
        compare("abc/def/../../../ghi","../ghi");
        compare("\\abc\\def\\..\\..\\..\\ghi\\","\\ghi");
    }

    private void compare(String original, String answer) {
        assertEquals(answer,new FilePath((VirtualChannel)null,original).getRemote());
    }

    // @Bug(6494)
    public void testGetParent() throws Exception {
        FilePath fp = new FilePath((VirtualChannel)null, "/abc/def");
        assertEquals("/abc", (fp = fp.getParent()).getRemote());
        assertEquals("/", (fp = fp.getParent()).getRemote());
        assertNull(fp.getParent());

        fp = new FilePath((VirtualChannel)null, "abc/def\\ghi");
        assertEquals("abc/def", (fp = fp.getParent()).getRemote());
        assertEquals("abc", (fp = fp.getParent()).getRemote());
        assertNull(fp.getParent());

        fp = new FilePath((VirtualChannel)null, "C:\\abc\\def");
        assertEquals("C:\\abc", (fp = fp.getParent()).getRemote());
        assertEquals("C:\\", (fp = fp.getParent()).getRemote());
        assertNull(fp.getParent());
    }

    private FilePath createFilePath(final File base, final String... path) throws IOException {
        File building = base;
        for (final String component : path) {
            building = new File(building, component);
        }
        FileUtils.touch(building);
        return new FilePath(building);
    }

    public void testList() throws Exception {
        File baseDir = Util.createTempDir();
        try {
            final Set<FilePath> expected = new HashSet<FilePath>();
            expected.add(createFilePath(baseDir, "top", "sub", "app.log"));
            expected.add(createFilePath(baseDir, "top", "sub", "trace.log"));
            expected.add(createFilePath(baseDir, "top", "db", "db.log"));
            expected.add(createFilePath(baseDir, "top", "db", "trace.log"));
            final FilePath[] result = new FilePath(baseDir).list("**");
            assertEquals(expected, new HashSet<FilePath>(Arrays.asList(result)));
        } finally {
            Util.deleteRecursive(baseDir);
        }
    }

    public void testListWithExcludes() throws Exception {
        File baseDir = Util.createTempDir();
        try {
            final Set<FilePath> expected = new HashSet<FilePath>();
            expected.add(createFilePath(baseDir, "top", "sub", "app.log"));
            createFilePath(baseDir, "top", "sub", "trace.log");
            expected.add(createFilePath(baseDir, "top", "db", "db.log"));
            createFilePath(baseDir, "top", "db", "trace.log");
            final FilePath[] result = new FilePath(baseDir).list("**", "**/trace.log");
            assertEquals(expected, new HashSet<FilePath>(Arrays.asList(result)));
        } finally {
            Util.deleteRecursive(baseDir);
        }
    }

}
