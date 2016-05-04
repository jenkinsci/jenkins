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

import hudson.FilePath.TarCompression;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.util.NullStream;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.ConnectException;
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
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static org.junit.Assume.assumeFalse;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.jvnet.hudson.test.Issue;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class FilePathTest {

    @Rule public ChannelRule channels = new ChannelRule();
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void copyTo() throws Exception {
        File tmp = temp.newFile();
        FilePath f = new FilePath(channels.french, tmp.getPath());
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
    @Test public void noFileLeakInCopyTo() throws Exception {
        for (int j=0; j<2500; j++) {
            File tmp = temp.newFile();
            FilePath f = new FilePath(tmp);
            File tmp2 = temp.newFile();
            FilePath f2 = new FilePath(channels.british, tmp2.getPath());

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
    @Issue("JENKINS-7871")
    @Test public void noRaceConditionInCopyTo() throws Exception {
        final File tmp = temp.newFile();

           int fileSize = 90000;
        
            givenSomeContentInFile(tmp, fileSize);
        
            List<Future<Integer>> results = whenFileIsCopied100TimesConcurrently(tmp);

            // THEN copied count was always equal the expected size
            for (Future<Integer> f : results)
                assertEquals(fileSize,f.get().intValue());
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

                        private void checkNotClosed() throws IOException {
                            if (closed != null)
                                throw new IOException(closed);
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

                    FilePath f = new FilePath(channels.french, file.getPath());
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

    @Test public void repeatCopyRecursiveTo() throws Exception {
        // local->local copy used to return 0 if all files were "up to date"
        // should return number of files processed, whether or not they were copied or already current
        File src = temp.newFolder("src");
        File dst = temp.newFolder("dst");
            File.createTempFile("foo", ".tmp", src);
            FilePath fp = new FilePath(src);
            assertEquals(1, fp.copyRecursiveTo(new FilePath(dst)));
            // copy again should still report 1
            assertEquals(1, fp.copyRecursiveTo(new FilePath(dst)));
    }

    @Issue("JENKINS-9540")
    @Test public void errorMessageInRemoteCopyRecursive() throws Exception {
        File src = temp.newFolder("src");
        File dst = temp.newFolder("dst");
            FilePath from = new FilePath(src);
            FilePath to = new FilePath(channels.british, dst.getAbsolutePath());
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
    }

    @Issue("JENKINS-4039")
    @Test public void archiveBug() throws Exception {
            FilePath d = new FilePath(channels.french, temp.getRoot().getPath());
            d.child("test").touch(0);
            d.zip(new NullOutputStream());
            d.zip(new NullOutputStream(),"**/*");
    }

    @Test public void normalization() throws Exception {
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

        // @Issue("JENKINS-5951")
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

    @Issue("JENKINS-6494")
    @Test public void getParent() throws Exception {
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
    
    /**
     * Performs round-trip archiving for Tar handling methods.
     * @throws Exception test failure
     */
    @Test public void compressTarUntarRoundTrip() throws Exception {
        checkTarUntarRoundTrip("compressTarUntarRoundTrip_zero", 0);   
        checkTarUntarRoundTrip("compressTarUntarRoundTrip_small", 100); 
        checkTarUntarRoundTrip("compressTarUntarRoundTrip_medium", 50000); 
    }
            
    /**
     * Checks that big files (greater than 8GB) can be archived and then unpacked.
     * This test is disabled by default due the impact on RAM.
     * The actual file size limit is 8589934591 bytes.
     * @throws Exception test failure
     */
    @Issue("JENKINS-10629")
    @Ignore
    @Test public void archiveBigFile() throws Exception {
        final long largeFileSize = 9000000000L; // >8589934591 bytes
        final String filePrefix = "JENKINS-10629";
        checkTarUntarRoundTrip(filePrefix, largeFileSize);
    }
     
    private void checkTarUntarRoundTrip(String filePrefix, long fileSize) throws Exception {
        final File tmpDir = temp.newFolder(filePrefix);
        final File tempFile =  new File(tmpDir, filePrefix + ".log");
        RandomAccessFile file = new RandomAccessFile(tempFile, "rw");
        final File tarFile = new File(tmpDir, filePrefix + ".tar");

        file.setLength(fileSize);
        assumeTrue(fileSize == file.length());
        file.close();

        // Compress archive
        final FilePath tmpDirPath = new FilePath(tmpDir);
        int tar = tmpDirPath.tar(new FileOutputStream(tarFile), tempFile.getName());
        assertEquals("One file should have been compressed", 1, tar);

        // Decompress
        FilePath outDir = new FilePath(temp.newFolder(filePrefix + "_out"));
        final FilePath outFile = outDir.child(tempFile.getName());
        tmpDirPath.child(tarFile.getName()).untar(outDir, TarCompression.NONE);
        assertEquals("Result file after the roundtrip differs from the initial file",
                new FilePath(tempFile).digest(), outFile.digest());
    }

    @Test public void list() throws Exception {
        File baseDir = temp.getRoot();
            final Set<FilePath> expected = new HashSet<FilePath>();
            expected.add(createFilePath(baseDir, "top", "sub", "app.log"));
            expected.add(createFilePath(baseDir, "top", "sub", "trace.log"));
            expected.add(createFilePath(baseDir, "top", "db", "db.log"));
            expected.add(createFilePath(baseDir, "top", "db", "trace.log"));
            final FilePath[] result = new FilePath(baseDir).list("**");
            assertEquals(expected, new HashSet<FilePath>(Arrays.asList(result)));
    }

    @Test public void listWithExcludes() throws Exception {
        File baseDir = temp.getRoot();
            final Set<FilePath> expected = new HashSet<FilePath>();
            expected.add(createFilePath(baseDir, "top", "sub", "app.log"));
            createFilePath(baseDir, "top", "sub", "trace.log");
            expected.add(createFilePath(baseDir, "top", "db", "db.log"));
            createFilePath(baseDir, "top", "db", "trace.log");
            final FilePath[] result = new FilePath(baseDir).list("**", "**/trace.log");
            assertEquals(expected, new HashSet<FilePath>(Arrays.asList(result)));
    }

    @Test public void listWithDefaultExcludes() throws Exception {
        File baseDir = temp.getRoot();
            final Set<FilePath> expected = new HashSet<FilePath>();
            expected.add(createFilePath(baseDir, "top", "sub", "backup~"));
            expected.add(createFilePath(baseDir, "top", "CVS", "somefile,v"));
            expected.add(createFilePath(baseDir, "top", ".git", "config"));
            // none of the files are included by default (default includes true)
            assertEquals(0, new FilePath(baseDir).list("**", "").length);
            final FilePath[] result = new FilePath(baseDir).list("**", "", false);
            assertEquals(expected, new HashSet<FilePath>(Arrays.asList(result)));
    }

    @Issue("JENKINS-11073")
    @Test public void isUnix() {
        VirtualChannel dummy = Mockito.mock(VirtualChannel.class);
        FilePath winPath = new FilePath(dummy,
                " c:\\app\\hudson\\workspace\\3.8-jelly-db\\jdk/jdk1.6.0_21/label/sqlserver/profile/sqlserver\\acceptance-tests\\distribution.zip");
        assertFalse(winPath.isUnix());

        FilePath base = new FilePath(dummy,
                "c:\\app\\hudson\\workspace\\3.8-jelly-db");
        FilePath middle = new FilePath(base, "jdk/jdk1.6.0_21/label/sqlserver/profile/sqlserver");
        FilePath full = new FilePath(middle, "acceptance-tests\\distribution.zip");
        assertFalse(full.isUnix());
        
        
        FilePath unixPath = new FilePath(dummy,
                "/home/test");
        assertTrue(unixPath.isUnix());
    }
    
    /**
     * Tests that permissions are kept when using {@link FilePath#copyToWithPermission(FilePath)}.
     * Also tries to check that a problem with setting the last-modified date on Windows doesn't fail the whole copy
     * - well at least when running this test on a Windows OS. See JENKINS-11073
     */
    @Test public void copyToWithPermission() throws IOException, InterruptedException {
        File tmp = temp.getRoot();
            File child = new File(tmp,"child");
            FilePath childP = new FilePath(child);
            childP.touch(4711);
            
            Chmod chmodTask = new Chmod();
            chmodTask.setProject(new Project());
            chmodTask.setFile(child);
            chmodTask.setPerm("0400");
            chmodTask.execute();
            
            FilePath copy = new FilePath(channels.british, tmp.getPath()).child("copy");
            childP.copyToWithPermission(copy);
            
            assertEquals(childP.mode(),copy.mode());
            if (!Functions.isWindows()) {
                assertEquals(childP.lastModified(),copy.lastModified());
            }
            
            // JENKINS-11073:
            // Windows seems to have random failures when setting the timestamp on newly generated
            // files. So test that:
            for (int i=0; i<100; i++) {
                copy = new FilePath(channels.british, tmp.getPath()).child("copy"+i);
                childP.copyToWithPermission(copy);
            }
    }

    @Test public void symlinkInTar() throws Exception {
        assumeFalse("can't test on Windows", Functions.isWindows());

        FilePath tmp = new FilePath(temp.getRoot());
            FilePath in = tmp.child("in");
            in.mkdirs();
            in.child("c").touch(0);
            in.child("b").symlinkTo("c", TaskListener.NULL);
                        
            FilePath tar = tmp.child("test.tar");
            in.tar(tar.write(), "**/*");

            FilePath dst = in.child("dst");
            tar.untar(dst, TarCompression.NONE);

            assertEquals("c",dst.child("b").readLink());
    }

    @Issue("JENKINS-13649")
    @Test public void multiSegmentRelativePaths() throws Exception {
        VirtualChannel d = Mockito.mock(VirtualChannel.class);
        FilePath winPath = new FilePath(d, "c:\\app\\jenkins\\workspace");
        FilePath nixPath = new FilePath(d, "/opt/jenkins/workspace");

        assertEquals("c:\\app\\jenkins\\workspace\\foo\\bar\\manchu", new FilePath(winPath, "foo/bar/manchu").getRemote());
        assertEquals("c:\\app\\jenkins\\workspace\\foo\\bar\\manchu", new FilePath(winPath, "foo\\bar/manchu").getRemote());
        assertEquals("c:\\app\\jenkins\\workspace\\foo\\bar\\manchu", new FilePath(winPath, "foo\\bar\\manchu").getRemote());
        assertEquals("/opt/jenkins/workspace/foo/bar/manchu", new FilePath(nixPath, "foo\\bar\\manchu").getRemote());
        assertEquals("/opt/jenkins/workspace/foo/bar/manchu", new FilePath(nixPath, "foo/bar\\manchu").getRemote());
        assertEquals("/opt/jenkins/workspace/foo/bar/manchu", new FilePath(nixPath, "foo/bar/manchu").getRemote());
    }

    @Test public void validateAntFileMask() throws Exception {
        File tmp = temp.getRoot();
            FilePath d = new FilePath(channels.french, tmp.getPath());
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
    }

    @SuppressWarnings("deprecation")
    private static void assertValidateAntFileMask(String expected, FilePath d, String fileMasks) throws Exception {
        assertEquals(expected, d.validateAntFileMask(fileMasks));
    }

    @Issue("JENKINS-7214")
    @SuppressWarnings("deprecation")
    @Test public void validateAntFileMaskBounded() throws Exception {
        File tmp = temp.getRoot();
            FilePath d = new FilePath(channels.french, tmp.getPath());
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
    }
    
    @Issue("JENKINS-5253")
    public void testValidateCaseSensitivity() throws Exception {
        File tmp = Util.createTempDir();
        try {
            FilePath d = new FilePath(channels.french, tmp.getPath());
            d.child("d1/d2/d3").mkdirs();
            d.child("d1/d2/d3/f.txt").touch(0);
            d.child("d1/d2/d3/f.html").touch(0);
            d.child("d1/d2/f.txt").touch(0);
            
            assertEquals(null, d.validateAntFileMask("**/d1/**/f.*", FilePath.VALIDATE_ANT_FILE_MASK_BOUND, true));
            assertEquals(null, d.validateAntFileMask("**/d1/**/f.*", FilePath.VALIDATE_ANT_FILE_MASK_BOUND, false));
            assertEquals(Messages.FilePath_validateAntFileMask_matchWithCaseInsensitive("**/D1/**/F.*"), d.validateAntFileMask("**/D1/**/F.*", FilePath.VALIDATE_ANT_FILE_MASK_BOUND, true));
            assertEquals(null, d.validateAntFileMask("**/D1/**/F.*", FilePath.VALIDATE_ANT_FILE_MASK_BOUND, false));
        } finally {
            Util.deleteRecursive(tmp);
        }
    }
   
    @Issue("JENKINS-15418")
    @Test public void deleteLongPathOnWindows() throws Exception {
        File tmp = temp.getRoot();
            FilePath d = new FilePath(channels.french, tmp.getPath());
            
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
    }

    @Issue("JENKINS-16215")
    @Test public void installIfNecessaryAvoidsExcessiveDownloadsByUsingIfModifiedSince() throws Exception {
        File tmp = temp.getRoot();
            final FilePath d = new FilePath(tmp);

            d.child(".timestamp").touch(123000);

            final HttpURLConnection con = mock(HttpURLConnection.class);
            final URL url = someUrlToZipFile(con);

            when(con.getResponseCode())
                .thenReturn(HttpURLConnection.HTTP_NOT_MODIFIED);

            assertFalse(d.installIfNecessaryFrom(url, null, null));

            verify(con).setIfModifiedSince(123000);
    }

    @Issue("JENKINS-16215")
    @Test public void installIfNecessaryPerformsInstallation() throws Exception {
        File tmp = temp.getRoot();
            final FilePath d = new FilePath(tmp);

            final HttpURLConnection con = mock(HttpURLConnection.class);
            final URL url = someUrlToZipFile(con);

            when(con.getResponseCode())
              .thenReturn(HttpURLConnection.HTTP_OK);

            when(con.getInputStream())
              .thenReturn(someZippedContent());

            assertTrue(d.installIfNecessaryFrom(url, null, null));
    }

    @Issue("JENKINS-26196")
    @Test public void installIfNecessarySkipsDownloadWhenErroneous() throws Exception {
        File tmp = temp.getRoot();
        final FilePath d = new FilePath(tmp);
        d.child(".timestamp").touch(123000);
        final HttpURLConnection con = mock(HttpURLConnection.class);
        final URL url = someUrlToZipFile(con);
        when(con.getResponseCode()).thenReturn(HttpURLConnection.HTTP_GATEWAY_TIMEOUT);
        when(con.getResponseMessage()).thenReturn("Gateway Timeout");
        when(con.getInputStream()).thenThrow(new ConnectException());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String message = "going ahead";
        assertFalse(d.installIfNecessaryFrom(url, new StreamTaskListener(baos), message));
        verify(con).setIfModifiedSince(123000);
        String log = baos.toString();
        assertFalse(log, log.contains(message));
        assertTrue(log, log.contains("504 Gateway Timeout"));
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

    @Issue("JENKINS-16846")
    @Test public void moveAllChildrenTo() throws IOException, InterruptedException {
        File tmp = temp.getRoot();
            final String dirname = "sub";
            final File top = new File(tmp, "test");
            final File sub = new File(top, dirname);
            final File subsub = new File(sub, dirname);

            subsub.mkdirs();

            final File subFile1 = new File( sub.getAbsolutePath() + "/file1.txt" );
            subFile1.createNewFile();
            final File subFile2 = new File( subsub.getAbsolutePath() + "/file2.txt" );
            subFile2.createNewFile();

            final FilePath src = new FilePath(sub);
            final FilePath dst = new FilePath(top);
            
            // test conflict subdir
            src.moveAllChildrenTo(dst);
    }

    @Issue("JENKINS-10629")
    @Test
    public void testEOFbrokenFlush() throws IOException, InterruptedException {
        final File srcFolder = temp.newFolder("src");
        // simulate magic structure with magic sizes:
        // |- dir/pom.xml   (2049)
        // |- pom.xml       (2049)
        // \- small.tar     (1537)
        final File smallTar = new File(srcFolder, "small.tar");
        givenSomeContentInFile(smallTar, 1537);
        final File dir = new File(srcFolder, "dir");
        dir.mkdirs();
        final File pomFile = new File(dir, "pom.xml");
        givenSomeContentInFile(pomFile, 2049);
        FileUtils.copyFileToDirectory(pomFile, srcFolder);

        final File archive = temp.newFile("archive.tar");

        // Compress archive
        final FilePath tmpDirPath = new FilePath(srcFolder);
        int tarred = tmpDirPath.tar(new FileOutputStream(archive), "**");
        assertEquals("One file should have been compressed", 3, tarred);

        // Decompress
        final File dstFolder = temp.newFolder("dst");
        dstFolder.mkdirs();
        FilePath outDir = new FilePath(dstFolder);
        // and now fail when flush is bad!
        tmpDirPath.child("../" + archive.getName()).untar(outDir, TarCompression.NONE);
    }
}
