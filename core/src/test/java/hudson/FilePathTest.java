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

import static org.mockito.Mockito.*;
import hudson.FilePath.TarCompression;
import hudson.model.TaskListener;
import hudson.remoting.LocalChannel;
import hudson.remoting.VirtualChannel;
import hudson.util.IOException2;
import hudson.util.NullStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Chmod;
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
    // TODO: this test is much too slow to be a traditional unit test. Should be extracted into some stress test
    // which is no part of the default test harness?
    public void testNoFileLeakInCopyTo() throws Exception {
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
    public void testNoRaceConditionInCopyTo() throws Exception {
        final File tmp = File.createTempFile("testNoRaceConditionInCopyTo","");

        try {
           int fileSize = 90000;
        
            givenSomeContentInFile(tmp, fileSize);
        
            List<Future<Integer>> results = whenFileIsCopied100TimesConcurrently(tmp);

            // THEN copied count was always equal the expected size
            for (Future<Integer> f : results)
                assertEquals(fileSize,f.get().intValue());
        } finally {
            tmp.delete();
        }
    }

    private void givenSomeContentInFile(File file, int size) throws IOException {
        FileOutputStream os = new FileOutputStream(file);
        byte[] buf = new byte[size];
        for (int i=0; i<buf.length; i++)
            buf[i] = (byte)(i%256);
        os.write(buf);
        os.close();
    }
    
    private List<Future<Integer>> whenFileIsCopied100TimesConcurrently(final File file) throws InterruptedException {
        List<Callable<Integer>> r = new ArrayList<Callable<Integer>>();
        for (int i=0; i<100; i++) {
            r.add(new Callable<Integer>() {
                public Integer call() throws Exception {
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
                            //if (size!=count)
                            //    fail();
                        }
                    }

                    FilePath f = new FilePath(french, file.getPath());
                    Sink sink = new Sink();
                    f.copyTo(sink);
                    return sink.count;
                }
            });
        }

        ExecutorService es = Executors.newFixedThreadPool(100);
        try {
            return es.invokeAll(r);
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

    @Bug(9540)
    public void testErrorMessageInRemoteCopyRecursive() throws Exception {
        File tmp = Util.createTempDir();
        try {
            File src = new File(tmp, "src");
            File dst = new File(tmp, "dst");
            FilePath from = new FilePath(src);
            FilePath to = new FilePath(british, dst.getAbsolutePath());
            for (int i = 0; i < 10000; i++) {
                // TODO is there a simpler way to force the TarOutputStream to be flushed and the reader to start?
                // Have not found a way to make the failure guaranteed.
                OutputStream os = from.child("content" + i).write();
                try {
                    for (int j = 0; j < 1024; j++) {
                        os.write('.');
                    }
                } finally {
                    os.close();
                }
            }
            FilePath toF = to.child("content0");
            toF.write().close();
            toF.chmod(0400);
            try {
                from.copyRecursiveTo(to);
                // on Windows this may just succeed; OK, test did not prove anything then
            } catch (IOException x) {
                if (Functions.printThrowable(x).contains("content0")) {
                    // Fine, error message talks about permission denied.
                } else {
                    throw x;
                }
            } finally {
                toF.chmod(700);
            }
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

    public void testListWithDefaultExcludes() throws Exception {
        File baseDir = Util.createTempDir();
        try {
            final Set<FilePath> expected = new HashSet<FilePath>();
            expected.add(createFilePath(baseDir, "top", "sub", "backup~"));
            expected.add(createFilePath(baseDir, "top", "CVS", "somefile,v"));
            expected.add(createFilePath(baseDir, "top", ".git", "config"));
            // none of the files are included by default (default includes true)
            assertEquals(0, new FilePath(baseDir).list("**", "").length);
            final FilePath[] result = new FilePath(baseDir).list("**", "", false);
            assertEquals(expected, new HashSet<FilePath>(Arrays.asList(result)));
        } finally {
            Util.deleteRecursive(baseDir);
        }
    }

    @Bug(11073)
    public void testIsUnix() {
        FilePath winPath = new FilePath(new LocalChannel(null),
                " c:\\app\\hudson\\workspace\\3.8-jelly-db\\jdk/jdk1.6.0_21/label/sqlserver/profile/sqlserver\\acceptance-tests\\distribution.zip");
        assertFalse(winPath.isUnix());

        FilePath base = new FilePath(new LocalChannel(null),
                "c:\\app\\hudson\\workspace\\3.8-jelly-db");
        FilePath middle = new FilePath(base, "jdk/jdk1.6.0_21/label/sqlserver/profile/sqlserver");
        FilePath full = new FilePath(middle, "acceptance-tests\\distribution.zip");
        assertFalse(full.isUnix());
        
        
        FilePath unixPath = new FilePath(new LocalChannel(null),
                "/home/test");
        assertTrue(unixPath.isUnix());
    }
    
    /**
     * Tests that permissions are kept when using {@link FilePath#copyToWithPermission(FilePath)}.
     * Also tries to check that a problem with setting the last-modified date on Windows doesn't fail the whole copy
     * - well at least when running this test on a Windows OS. See JENKINS-11073
     */
    public void testCopyToWithPermission() throws IOException, InterruptedException {
        File tmp = Util.createTempDir();
        try {
            File child = new File(tmp,"child");
            FilePath childP = new FilePath(child);
            childP.touch(4711);
            
            Chmod chmodTask = new Chmod();
            chmodTask.setProject(new Project());
            chmodTask.setFile(child);
            chmodTask.setPerm("0400");
            chmodTask.execute();
            
            FilePath copy = new FilePath(british,tmp.getPath()).child("copy");
            childP.copyToWithPermission(copy);
            
            assertEquals(childP.mode(),copy.mode());
            if (!Functions.isWindows()) {
                assertEquals(childP.lastModified(),copy.lastModified());
            }
            
            // JENKINS-11073:
            // Windows seems to have random failures when setting the timestamp on newly generated
            // files. So test that:
            for (int i=0; i<100; i++) {
                copy = new FilePath(british,tmp.getPath()).child("copy"+i);
                childP.copyToWithPermission(copy);
            }
        } finally {
            Util.deleteRecursive(tmp);
        }
    }

    public void testSymlinkInTar() throws Exception {
        if (Functions.isWindows())  return; // can't test on Windows

        FilePath tmp = new FilePath(Util.createTempDir());
        try {
            FilePath in = tmp.child("in");
            in.mkdirs();
            in.child("c").touch(0);
            in.child("b").symlinkTo("c", TaskListener.NULL);
                        
            FilePath tar = tmp.child("test.tar");
            in.tar(tar.write(), "**/*");

            FilePath dst = in.child("dst");
            tar.untar(dst, TarCompression.NONE);

            assertEquals("c",dst.child("b").readLink());
        } finally {
            tmp.deleteRecursive();
        }
    }

    @Bug(13649)
    public void testMultiSegmentRelativePaths() throws Exception {
        FilePath winPath = new FilePath(new LocalChannel(null), "c:\\app\\jenkins\\workspace");
        FilePath nixPath = new FilePath(new LocalChannel(null), "/opt/jenkins/workspace");

        assertEquals("c:\\app\\jenkins\\workspace\\foo\\bar\\manchu", new FilePath(winPath, "foo/bar/manchu").getRemote());
        assertEquals("c:\\app\\jenkins\\workspace\\foo\\bar\\manchu", new FilePath(winPath, "foo\\bar/manchu").getRemote());
        assertEquals("c:\\app\\jenkins\\workspace\\foo\\bar\\manchu", new FilePath(winPath, "foo\\bar\\manchu").getRemote());
        assertEquals("/opt/jenkins/workspace/foo/bar/manchu", new FilePath(nixPath, "foo\\bar\\manchu").getRemote());
        assertEquals("/opt/jenkins/workspace/foo/bar/manchu", new FilePath(nixPath, "foo/bar\\manchu").getRemote());
        assertEquals("/opt/jenkins/workspace/foo/bar/manchu", new FilePath(nixPath, "foo/bar/manchu").getRemote());
    }

    public void testValidateAntFileMask() throws Exception {
        File tmp = Util.createTempDir();
        try {
            FilePath d = new FilePath(french, tmp.getPath());
            d.child("d1/d2/d3").mkdirs();
            d.child("d1/d2/d3/f.txt").touch(0);
            d.child("d1/d2/d3/f.html").touch(0);
            d.child("d1/d2/f.txt").touch(0);
            assertValidateAntFileMask(null, d, "**/*.txt");
            assertValidateAntFileMask(null, d, "d1/d2/d3/f.txt");
            assertValidateAntFileMask(null, d, "**/*.html");
            assertValidateAntFileMask(Messages.FilePath_validateAntFileMask_portionMatchButPreviousNotMatchAndSuggest("**/*.js", "**", "**/*.js"), d, "**/*.js");
            assertValidateAntFileMask(Messages.FilePath_validateAntFileMask_doesntMatchAnything("index.htm"), d, "index.htm");
            assertValidateAntFileMask(Messages.FilePath_validateAntFileMask_doesntMatchAndSuggest("f.html", "d1/d2/d3/f.html"), d, "f.html");
            // TODO lots more to test, e.g. multiple patterns separated by commas; ought to have full code coverage for this method
        } finally {
            Util.deleteRecursive(tmp);
        }
    }

    private static void assertValidateAntFileMask(String expected, FilePath d, String fileMasks) throws Exception {
        assertEquals(expected, d.validateAntFileMask(fileMasks));
    }

    @Bug(7214)
    public void testValidateAntFileMaskBounded() throws Exception {
        File tmp = Util.createTempDir();
        try {
            FilePath d = new FilePath(french, tmp.getPath());
            FilePath d2 = d.child("d1/d2");
            d2.mkdirs();
            for (int i = 0; i < 100; i++) {
                FilePath d3 = d2.child("d" + i);
                d3.mkdirs();
                d3.child("f.txt").touch(0);
            }
            assertEquals(null, d.validateAntFileMask("d1/d2/**/f.txt"));
            assertEquals(null, d.validateAntFileMask("d1/d2/**/f.txt", 10));
            assertEquals(Messages.FilePath_validateAntFileMask_portionMatchButPreviousNotMatchAndSuggest("**/*.js", "**", "**/*.js"), d.validateAntFileMask("**/*.js", 1000));
            try {
                d.validateAntFileMask("**/*.js", 10);
                fail();
            } catch (InterruptedException x) {
                // good
            }
        } finally {
            Util.deleteRecursive(tmp);
        }
    }
   
    @Bug(15418)
    public void testDeleteLongPathOnWindows() throws Exception {
        File tmp = Util.createTempDir();
        try {
            FilePath d = new FilePath(french, tmp.getPath());
            
            // construct a very long path
            StringBuilder sb = new StringBuilder();
            while(sb.length() + tmp.getPath().length() < 260 - "very/".length()) {
                sb.append("very/");
            }
            sb.append("pivot/very/very/long/path");
            
            FilePath longPath = d.child(sb.toString()); 
            longPath.mkdirs();
            FilePath childInLongPath = longPath.child("file.txt");
            childInLongPath.touch(0);
            
            File firstDirectory = new File(tmp.getAbsolutePath() + "/very");
            Util.deleteRecursive(firstDirectory);
            
            assertFalse("Could not delete directory!", firstDirectory.exists());
            
        } finally {
            Util.deleteRecursive(tmp);
        }
    }

    @Bug(16215)
    public void testInstallIfNecessaryAvoidsExcessiveDownloadsByUsingIfModifiedSince() throws Exception {
        final File tmp = Util.createTempDir();
        try {
            final FilePath d = new FilePath(tmp);

            d.child(".timestamp").touch(123000);

            final HttpURLConnection con = mock(HttpURLConnection.class);
            final URL url = someUrlToZipFile(con);

            when(con.getResponseCode())
                .thenReturn(HttpURLConnection.HTTP_NOT_MODIFIED);

            assertFalse(d.installIfNecessaryFrom(url, null, null));

            verify(con).setIfModifiedSince(123000);
        } finally {
            Util.deleteRecursive(tmp);
        }
    }

    @Bug(16215)
    public void testInstallIfNecessaryPerformsInstallation() throws Exception {
        final File tmp = Util.createTempDir();
        try {
            final FilePath d = new FilePath(tmp);

            final HttpURLConnection con = mock(HttpURLConnection.class);
            final URL url = someUrlToZipFile(con);

            when(con.getResponseCode())
              .thenReturn(HttpURLConnection.HTTP_OK);

            when(con.getInputStream())
              .thenReturn(someZippedContent());

            assertTrue(d.installIfNecessaryFrom(url, null, null));
        } finally {
          Util.deleteRecursive(tmp);
        }
    }

    private URL someUrlToZipFile(final URLConnection con) throws IOException {

        final URLStreamHandler urlHandler = new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL u) throws IOException {
                return con;
            }
        };

        return new URL("http", "some-host", 0, "/some-path.zip", urlHandler);
    }

    private InputStream someZippedContent() throws IOException {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final ZipOutputStream zip = new ZipOutputStream(buf);

        zip.putNextEntry(new ZipEntry("abc"));
        zip.write("abc".getBytes());
        zip.close();

        return new ByteArrayInputStream(buf.toByteArray());
    }
}
