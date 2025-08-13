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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hudson.FilePath.TarCompression;
import hudson.model.TaskListener;
import hudson.os.WindowsUtil;
import hudson.remoting.Channel;
import hudson.remoting.ChannelBuilder;
import hudson.remoting.FastPipedInputStream;
import hudson.remoting.FastPipedOutputStream;
import hudson.remoting.VirtualChannel;
import hudson.slaves.WorkspaceList;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Chmod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.mockito.Mockito;

/**
 * @author Kohsuke Kawaguchi
 */
class FilePathTest {

    @TempDir
    private File temp;

    /**
     * Two channels that are connected to each other, but shares the same classloader.
     */
    private Channel french;
    private Channel british;
    private ExecutorService executors;

    @BeforeEach
    void setUp() throws Exception {
        executors = Executors.newCachedThreadPool();
        final FastPipedInputStream p1i = new FastPipedInputStream();
        final FastPipedInputStream p2i = new FastPipedInputStream();
        final FastPipedOutputStream p1o = new FastPipedOutputStream(p1i);
        final FastPipedOutputStream p2o = new FastPipedOutputStream(p2i);

        Future<Channel> f1 = executors.submit(() -> new ChannelBuilder("This side of the channel", executors).withMode(Channel.Mode.BINARY).build(p1i, p2o));
        Future<Channel> f2 = executors.submit(() -> new ChannelBuilder("The other side of the channel", executors).withMode(Channel.Mode.BINARY).build(p2i, p1o));
        french = f1.get();
        british = f2.get();
    }

    @AfterEach
    void tearDown() {
        try {
            french.close(); // this will automatically initiate the close on the other channel, too.
            french.join();
            british.join();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException x) {
            throw new AssertionError(x);
        }
        executors.shutdownNow();
    }

    @Test
    void copyTo() throws Exception {
        File tmp = Files.createTempFile(temp.toPath(), "junit", null).toFile();
        FilePath f = new FilePath(french, tmp.getPath());
        try (OutputStream out = OutputStream.nullOutputStream()) {
            f.copyTo(out);
        }
        assertTrue(tmp.exists(), "target does not exist");
        assertTrue(tmp.delete(), "could not delete target " + tmp.getPath());
    }

    /**
     * An attempt to reproduce the file descriptor leak.
     * If this operation leaks a file descriptor, 2500 should be enough, I think.
     */
    // TODO: this test is much too slow to be a traditional unit test. Should be extracted into some stress test
    // which is no part of the default test harness?
    @Test
    void noFileLeakInCopyTo() throws Exception {
        for (int j = 0; j < 2500; j++) {
            File tmp = Files.createTempFile(temp.toPath(), "junit", null).toFile();
            FilePath f = new FilePath(tmp);
            File tmp2 = Files.createTempFile(temp.toPath(), "junit", null).toFile();
            FilePath f2 = new FilePath(british, tmp2.getPath());

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
    @Test
    void noRaceConditionInCopyTo() throws Exception {
        final File tmp = Files.createTempFile(temp.toPath(), "junit", null).toFile();

           int fileSize = 90000;

            givenSomeContentInFile(tmp, fileSize);

            List<Future<Integer>> results = whenFileIsCopied100TimesConcurrently(tmp);

            // THEN copied count was always equal the expected size
            for (Future<Integer> f : results)
                assertEquals(fileSize, f.get().intValue());
    }

    private void givenSomeContentInFile(File file, int size) throws IOException {
        try (OutputStream os = Files.newOutputStream(file.toPath())) {
            byte[] buf = new byte[size];
            for (int i = 0; i < buf.length; i++)
                buf[i] = (byte) (i % 256);
            os.write(buf);
        }
    }

    private List<Future<Integer>> whenFileIsCopied100TimesConcurrently(final File file) throws InterruptedException {
        List<Callable<Integer>> r = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            r.add(() -> {
                class Sink extends OutputStream {
                    private Exception closed;
                    private final AtomicInteger count = new AtomicInteger();

                    private void checkNotClosed() throws IOException {
                        if (closed != null)
                            throw new IOException(closed);
                    }

                    @Override
                    public void write(int b) throws IOException {
                        count.incrementAndGet();
                        checkNotClosed();
                    }

                    @Override
                    public void write(byte[] b) throws IOException {
                        count.addAndGet(b.length);
                        checkNotClosed();
                    }

                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        count.addAndGet(len);
                        checkNotClosed();
                    }

                    @Override
                    public void close() {
                        closed = new Exception();
                    }
                }

                FilePath f = new FilePath(french, file.getPath());
                Sink sink = new Sink();
                f.copyTo(sink);
                return sink.count.get();
            });
        }

        ExecutorService es = Executors.newFixedThreadPool(100);
        try {
            return es.invokeAll(r);
        } finally {
            es.shutdown();
        }
    }

    @Test
    void repeatCopyRecursiveTo() throws Exception {
        // local->local copy used to return 0 if all files were "up to date"
        // should return number of files processed, whether or not they were copied or already current
        File src = newFolder(temp, "src");
        File dst = newFolder(temp, "dst");
        Files.createTempFile(src.toPath(), "foo", ".tmp").toFile();
            FilePath fp = new FilePath(src);
            assertEquals(1, fp.copyRecursiveTo(new FilePath(dst)));
            // copy again should still report 1
            assertEquals(1, fp.copyRecursiveTo(new FilePath(dst)));
    }

    @Issue("JENKINS-9540")
    @Test
    void errorMessageInRemoteCopyRecursive() throws Exception {
        File src = newFolder(temp, "src");
        File dst = newFolder(temp, "dst");
            FilePath from = new FilePath(src);
            FilePath to = new FilePath(british, dst.getAbsolutePath());
            for (int i = 0; i < 10000; i++) {
                // TODO is there a simpler way to force the TarOutputStream to be flushed and the reader to start?
                // Have not found a way to make the failure guaranteed.
                try (OutputStream os = from.child("content" + i).write()) {
                    for (int j = 0; j < 1024; j++) {
                        os.write('.');
                    }
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
                toF.chmod(0700);
            }
    }

    @Issue("JENKINS-4039")
    @Test
    void archiveBug() throws Exception {
            FilePath d = new FilePath(french, temp.getPath());
            d.child("test").touch(0);
            try (OutputStream out = OutputStream.nullOutputStream()) {
                d.zip(out);
            }
            try (OutputStream out = OutputStream.nullOutputStream()) {
                d.zip(out, "**/*");
            }
    }

    @Test
    void normalization() {
        compare("abc/def\\ghi", "abc/def\\ghi"); // allow mixed separators

        { // basic '.' trimming
            compare("./abc/def", "abc/def");
            compare("abc/./def", "abc/def");
            compare("abc/def/.", "abc/def");

            compare(".\\abc\\def", "abc\\def");
            compare("abc\\.\\def", "abc\\def");
            compare("abc\\def\\.", "abc\\def");
        }

        compare("abc/../def", "def");
        compare("abc/def/../../ghi", "ghi");
        compare("abc/./def/../././../ghi", "ghi");   // interleaving . and ..

        compare("../abc/def", "../abc/def");     // uncollapsible ..
        compare("abc/def/..", "abc");

        compare("c:\\abc\\..", "c:\\");      // we want c:\\, not c:
        compare("c:\\abc\\def\\..", "c:\\abc");

        compare("/abc/../", "/");
        compare("abc/..", ".");
        compare(".", ".");

        // @Issue("JENKINS-5951")
        compare("C:\\Hudson\\jobs\\foo\\workspace/../../otherjob/workspace/build.xml",
                "C:\\Hudson\\jobs/otherjob/workspace/build.xml");
        // Other cases that failed before
        compare("../../abc/def", "../../abc/def");
        compare("..\\..\\abc\\def", "..\\..\\abc\\def");
        compare("/abc//../def", "/def");
        compare("c:\\abc\\\\..\\def", "c:\\def");
        compare("/../abc/def", "/abc/def");
        compare("c:\\..\\abc\\def", "c:\\abc\\def");
        compare("abc/def/", "abc/def");
        compare("abc\\def\\", "abc\\def");
        // The new code can collapse extra separator chars
        compare("abc//def/\\//\\ghi", "abc/def/ghi");
        compare("\\\\host\\\\abc\\\\\\def", "\\\\host\\abc\\def"); // don't collapse for \\ prefix
        compare("\\\\\\foo", "\\\\foo");
        compare("//foo", "/foo");
        // Other edge cases
        compare("abc/def/../../../ghi", "../ghi");
        compare("\\abc\\def\\..\\..\\..\\ghi\\", "\\ghi");
    }

    private void compare(String original, String answer) {
        assertEquals(answer, new FilePath((VirtualChannel) null, original).getRemote());
    }

    @Issue("JENKINS-6494")
    @Test
    void getParent() {
        FilePath fp = new FilePath((VirtualChannel) null, "/abc/def");
        assertEquals("/abc", (fp = fp.getParent()).getRemote());
        assertEquals("/", (fp = fp.getParent()).getRemote());
        assertNull(fp.getParent());

        fp = new FilePath((VirtualChannel) null, "abc/def\\ghi");
        assertEquals("abc/def", (fp = fp.getParent()).getRemote());
        assertEquals("abc", (fp = fp.getParent()).getRemote());
        assertNull(fp.getParent());

        fp = new FilePath((VirtualChannel) null, "C:\\abc\\def");
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
    @Test
    void compressTarUntarRoundTrip() throws Exception {
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
    @Disabled
    @Test
    void archiveBigFile() throws Exception {
        final long largeFileSize = 9000000000L; // >8589934591 bytes
        final String filePrefix = "JENKINS-10629";
        checkTarUntarRoundTrip(filePrefix, largeFileSize);
    }

    private void checkTarUntarRoundTrip(String filePrefix, long fileSize) throws Exception {
        final File tmpDir = newFolder(temp, filePrefix);
        final File tempFile =  new File(tmpDir, filePrefix + ".log");
        RandomAccessFile file = new RandomAccessFile(tempFile, "rw");
        final File tarFile = new File(tmpDir, filePrefix + ".tar");

        file.setLength(fileSize);
        assumeTrue(fileSize == file.length());
        file.close();

        // Compress archive
        final FilePath tmpDirPath = new FilePath(tmpDir);
        int tar = tmpDirPath.tar(Files.newOutputStream(tarFile.toPath()), tempFile.getName());
        assertEquals(1, tar, "One file should have been compressed");

        // Decompress
        FilePath outDir = new FilePath(newFolder(temp, filePrefix + "_out"));
        final FilePath outFile = outDir.child(tempFile.getName());
        tmpDirPath.child(tarFile.getName()).untar(outDir, TarCompression.NONE);
        assertEquals(new FilePath(tempFile).digest(), outFile.digest(), "Result file after the roundtrip differs from the initial file");
    }

    @Test
    void list() throws Exception {
        File baseDir = temp;
            final Set<FilePath> expected = new HashSet<>();
            expected.add(createFilePath(baseDir, "top", "sub", "app.log"));
            expected.add(createFilePath(baseDir, "top", "sub", "trace.log"));
            expected.add(createFilePath(baseDir, "top", "db", "db.log"));
            expected.add(createFilePath(baseDir, "top", "db", "trace.log"));
            final FilePath[] result = new FilePath(baseDir).list("**");
            assertEquals(expected, new HashSet<>(Arrays.asList(result)));
    }

    @Test
    void listWithExcludes() throws Exception {
        File baseDir = temp;
            final Set<FilePath> expected = new HashSet<>();
            expected.add(createFilePath(baseDir, "top", "sub", "app.log"));
            createFilePath(baseDir, "top", "sub", "trace.log");
            expected.add(createFilePath(baseDir, "top", "db", "db.log"));
            createFilePath(baseDir, "top", "db", "trace.log");
            final FilePath[] result = new FilePath(baseDir).list("**", "**/trace.log");
            assertEquals(expected, new HashSet<>(Arrays.asList(result)));
    }

    @Test
    void listWithDefaultExcludes() throws Exception {
        File baseDir = temp;
            final Set<FilePath> expected = new HashSet<>();
            expected.add(createFilePath(baseDir, "top", "sub", "backup~"));
            expected.add(createFilePath(baseDir, "top", "CVS", "somefile,v"));
            expected.add(createFilePath(baseDir, "top", ".git", "config"));
            // none of the files are included by default (default includes true)
            assertEquals(0, new FilePath(baseDir).list("**", "").length);
            final FilePath[] result = new FilePath(baseDir).list("**", "", false);
            assertEquals(expected, new HashSet<>(Arrays.asList(result)));
    }

    @Issue("JENKINS-11073")
    @Test
    void isUnix() {
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
    @Test
    void copyToWithPermission() throws IOException, InterruptedException {
        File tmp = temp;
            File child = new File(tmp, "child");
            FilePath childP = new FilePath(child);
            childP.touch(4711);

            Chmod chmodTask = new Chmod();
            chmodTask.setProject(new Project());
            chmodTask.setFile(child);
            chmodTask.setPerm("0400");
            chmodTask.execute();

            FilePath copy = new FilePath(british, tmp.getPath()).child("copy");
            childP.copyToWithPermission(copy);

            assertEquals(childP.mode(), copy.mode());
            if (!Functions.isWindows()) {
                assertEquals(childP.lastModified(), copy.lastModified());
            }

            // JENKINS-11073:
            // Windows seems to have random failures when setting the timestamp on newly generated
            // files. So test that:
            for (int i = 0; i < 100; i++) {
                copy = new FilePath(british, tmp.getPath()).child("copy" + i);
                childP.copyToWithPermission(copy);
            }
    }

    @Test
    void symlinkInTar() throws Exception {
        assumeFalse(Functions.isWindows());

        FilePath tmp = new FilePath(temp);
            FilePath in = tmp.child("in");
            in.mkdirs();
            in.child("c").touch(0);
            in.child("b").symlinkTo("c", TaskListener.NULL);

            FilePath tar = tmp.child("test.tar");
            in.tar(tar.write(), "**/*");

            FilePath dst = in.child("dst");
            tar.untar(dst, TarCompression.NONE);

            assertEquals("c", dst.child("b").readLink());
    }

    @Issue("JENKINS-13649")
    @Test
    void multiSegmentRelativePaths() {
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

    @Test
    void validateAntFileMask() throws Exception {
        File tmp = temp;
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
    }

    @SuppressWarnings("deprecation")
    private static void assertValidateAntFileMask(String expected, FilePath d, String fileMasks) throws Exception {
        assertEquals(expected, d.validateAntFileMask(fileMasks));
    }

    @Issue("JENKINS-7214")
    @SuppressWarnings("deprecation")
    @Test
    void validateAntFileMaskBounded() throws Exception {
        File tmp = temp;
            FilePath d = new FilePath(french, tmp.getPath());
            FilePath d2 = d.child("d1/d2");
            d2.mkdirs();
            for (int i = 0; i < 100; i++) {
                FilePath d3 = d2.child("d" + i);
                d3.mkdirs();
                d3.child("f.txt").touch(0);
            }
            assertNull(d.validateAntFileMask("d1/d2/**/f.txt"));
            assertNull(d.validateAntFileMask("d1/d2/**/f.txt", 10));
            assertEquals(Messages.FilePath_validateAntFileMask_portionMatchButPreviousNotMatchAndSuggest("**/*.js", "**", "**/*.js"), d.validateAntFileMask("**/*.js", 1000));
            assertThrows(InterruptedException.class, () -> d.validateAntFileMask("**/*.js", 10));
    }

    @Issue("JENKINS-5253")
    @Test
    void testValidateCaseSensitivity() throws Exception {
        File tmp = Util.createTempDir();
        try {
            FilePath d = new FilePath(french, tmp.getPath());
            d.child("d1/d2/d3").mkdirs();
            d.child("d1/d2/d3/f.txt").touch(0);
            d.child("d1/d2/d3/f.html").touch(0);
            d.child("d1/d2/f.txt").touch(0);

            assertNull(d.validateAntFileMask("**/d1/**/f.*", FilePath.VALIDATE_ANT_FILE_MASK_BOUND, true));
            assertNull(d.validateAntFileMask("**/d1/**/f.*", FilePath.VALIDATE_ANT_FILE_MASK_BOUND, false));
            assertEquals(Messages.FilePath_validateAntFileMask_matchWithCaseInsensitive("**/D1/**/F.*"), d.validateAntFileMask("**/D1/**/F.*", FilePath.VALIDATE_ANT_FILE_MASK_BOUND, true));
            assertNull(d.validateAntFileMask("**/D1/**/F.*", FilePath.VALIDATE_ANT_FILE_MASK_BOUND, false));
        } finally {
            Util.deleteRecursive(tmp);
        }
    }

    @Issue("JENKINS-15418")
    @Test
    void deleteLongPathOnWindows() throws Exception {
        File tmp = temp;
            FilePath d = new FilePath(french, tmp.getPath());

            // construct a very long path
            StringBuilder sb = new StringBuilder();
            while (sb.length() + tmp.getPath().length() < 260 - "very/".length()) {
                sb.append("very/");
            }
            sb.append("pivot/very/very/long/path");

            FilePath longPath = d.child(sb.toString());
            longPath.mkdirs();
            FilePath childInLongPath = longPath.child("file.txt");
            childInLongPath.touch(0);

            File firstDirectory = new File(tmp.getAbsolutePath() + "/very");
            Util.deleteRecursive(firstDirectory);

            assertFalse(firstDirectory.exists(), "Could not delete directory!");
    }

    @Issue("JENKINS-16215")
    @Test
    void installIfNecessaryAvoidsExcessiveDownloadsByUsingIfModifiedSince() throws Exception {
        File tmp = temp;
            final FilePath d = new FilePath(tmp);

            d.child(".timestamp").touch(123000);

            final HttpURLConnection con = mock(HttpURLConnection.class);
            final URL url = someUrlToZipFile(con);

            when(con.getResponseCode())
                .thenReturn(HttpURLConnection.HTTP_NOT_MODIFIED);

            assertFalse(d.installIfNecessaryFrom(url, null, "message if failed"));

            verify(con).setIfModifiedSince(123000);
    }

    @Issue("JENKINS-16215")
    @Test
    void installIfNecessaryPerformsInstallation() throws Exception {
        File tmp = temp;
            final FilePath d = new FilePath(tmp);

            final HttpURLConnection con = mock(HttpURLConnection.class);
            final URL url = someUrlToZipFile(con);

            when(con.getResponseCode())
              .thenReturn(HttpURLConnection.HTTP_OK);

            when(con.getInputStream())
              .thenReturn(someZippedContent());

            assertTrue(d.installIfNecessaryFrom(url, null, "message if failed"));
    }

    @Issue("JENKINS-26196")
    @Test
    void installIfNecessarySkipsDownloadWhenErroneous() throws Exception {
        File tmp = temp;
        final FilePath d = new FilePath(tmp);
        d.child(".timestamp").touch(123000);
        final HttpURLConnection con = mock(HttpURLConnection.class);
        final URL url = someUrlToZipFile(con);
        when(con.getResponseCode()).thenReturn(HttpURLConnection.HTTP_GATEWAY_TIMEOUT);
        when(con.getResponseMessage()).thenReturn("Gateway Timeout");
        when(con.getInputStream()).thenThrow(new ConnectException());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String message = "going ahead";
        assertFalse(d.installIfNecessaryFrom(url, new StreamTaskListener(baos, Charset.defaultCharset()), message));
        verify(con).setIfModifiedSince(123000);
        String log = baos.toString(Charset.defaultCharset());
        assertFalse(log.contains(message), log);
        assertTrue(log.contains("504 Gateway Timeout"), log);
    }

    @Issue("JENKINS-23507")
    @Test
    void installIfNecessaryFollowsRedirects() throws Exception {
        File tmp = temp;
        final FilePath d = new FilePath(tmp);
        FilePath.UrlFactory urlFactory = mock(FilePath.UrlFactory.class);
        d.setUrlFactory(urlFactory);
        final HttpURLConnection con = mock(HttpURLConnection.class);
        final HttpURLConnection con2 = mock(HttpURLConnection.class);
        final URL url = someUrlToZipFile(con);
        when(con.getResponseCode()).thenReturn(HttpURLConnection.HTTP_MOVED_TEMP);
        URL url2 = someUrlToZipFile(con2);
        String someUrl = url2.toExternalForm();
        when(con.getHeaderField("Location")).thenReturn(someUrl);
        when(urlFactory.newURL(someUrl)).thenReturn(url2);
        when(con2.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        when(con2.getInputStream()).thenReturn(someZippedContent());

        String message = "going ahead";
        assertTrue(d.installIfNecessaryFrom(url, null, message));
    }

    @Issue("JENKINS-72469")
    @Test
    void installIfNecessaryWithoutLastModifiedStrongValidator() throws Exception {
        String strongValidator = "\"An-ETag-strong-validator\"";
        installIfNecessaryWithoutLastModified(strongValidator);
    }

    @Issue("JENKINS-72469")
    @Test
    void installIfNecessaryWithoutLastModifiedStrongValidatorNoQuotes() throws Exception {
        // This ETag is a violation of the spec at https://httpwg.org/specs/rfc9110.html#field.etag
        // However, better safe to handle without quotes as well, just in case
        String strongValidator = "An-ETag-strong-validator-without-quotes";
        installIfNecessaryWithoutLastModified(strongValidator);
    }

    @Issue("JENKINS-72469")
    @Test
    void installIfNecessaryWithoutLastModifiedWeakValidator() throws Exception {
        String weakValidator = "W/\"An-ETag-weak-validator\"";
        installIfNecessaryWithoutLastModified(weakValidator);
    }

    @Issue("JENKINS-72469")
    @Test
    void installIfNecessaryWithoutLastModifiedStrongAndWeakValidators() throws Exception {
        String strongValidator = "\"An-ETag-validator\"";
        String weakValidator = "W/" + strongValidator;
        installIfNecessaryWithoutLastModified(strongValidator, weakValidator);
    }

    @Issue("JENKINS-72469")
    @Test
    void installIfNecessaryWithoutLastModifiedWeakAndStrongValidators() throws Exception {
        String strongValidator = "\"An-ETag-validator\"";
        String weakValidator = "W/" + strongValidator;
        installIfNecessaryWithoutLastModified(weakValidator, strongValidator);
    }

    private void installIfNecessaryWithoutLastModified(String validator) throws Exception {
        installIfNecessaryWithoutLastModified(validator, validator);
    }

    private void installIfNecessaryWithoutLastModified(String validator, String alternateValidator) throws Exception {
        final HttpURLConnection con = mock(HttpURLConnection.class);
        // getLastModified == 0 when last-modified header is not returned
        when(con.getLastModified()).thenReturn(0L);
        // An Etag is provided by Azul CDN without last-modified header
        when(con.getHeaderField("ETag")).thenReturn(validator);
        when(con.getInputStream()).thenReturn(someZippedContent());

        final URL url = someUrlToZipFile(con);

        File tmp = temp;
        final FilePath d = new FilePath(tmp);

        /* Initial download expected to occur */
        assertTrue(d.installIfNecessaryFrom(url, null, "message if failed first download"));

        /* Timestamp last modified == 0 means the header was not provided */
        assertThat(d.child(".timestamp").lastModified(), is(0L));

        /* Second download should not occur if JENKINS-72469 is fixed and NOT_MODIFIED is returned */
        when(con.getResponseCode()).thenReturn(HttpURLConnection.HTTP_NOT_MODIFIED);
        when(con.getInputStream()).thenReturn(someZippedContent());
        when(con.getHeaderField("ETag")).thenReturn(alternateValidator);
        assertFalse(d.installIfNecessaryFrom(url, null, "message if failed second download"));

        /* Third download should not occur if JENKINS-72469 is fixed and OK is returned with matching ETag */
        /* Unexpected to receive an OK and a matching ETag from a real web server, but check for safety */
        when(con.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        when(con.getInputStream()).thenReturn(someZippedContent());
        when(con.getHeaderField("ETag")).thenReturn(alternateValidator);
        assertFalse(d.installIfNecessaryFrom(url, null, "message if failed third download"));
    }

    private URL someUrlToZipFile(final URLConnection con) throws IOException {

        final URLStreamHandler urlHandler = new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL u) {
                return con;
            }
        };

        return new URL("http", "some-host", 0, "/some-path.zip", urlHandler);
    }

    private InputStream someZippedContent() throws IOException {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final ZipOutputStream zip = new ZipOutputStream(buf);

        zip.putNextEntry(new ZipEntry("abc"));
        zip.write("abc".getBytes(StandardCharsets.US_ASCII));
        zip.close();

        return new ByteArrayInputStream(buf.toByteArray());
    }

    @Issue("JENKINS-16846")
    @Test
    void moveAllChildrenTo() throws IOException, InterruptedException {
        File tmp = temp;
            final String dirname = "sub";
            final File top = new File(tmp, "test");
            final File sub = new File(top, dirname);
            final File subsub = new File(sub, dirname);

            subsub.mkdirs();

            final File subFile1 = new File(sub.getAbsolutePath() + "/file1.txt");
            subFile1.createNewFile();
            final File subFile2 = new File(subsub.getAbsolutePath() + "/file2.txt");
            subFile2.createNewFile();

            final FilePath src = new FilePath(sub);
            final FilePath dst = new FilePath(top);

            // test conflict subdir
            src.moveAllChildrenTo(dst);
    }

    @Issue("JENKINS-10629")
    @Test
    void testEOFbrokenFlush() throws IOException, InterruptedException {
        final File srcFolder = newFolder(temp, "src");
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

        final File archive = Files.createTempFile(temp.toPath(), "archive.tar", null).toFile();

        // Compress archive
        final FilePath tmpDirPath = new FilePath(srcFolder);
        int tarred = tmpDirPath.tar(Files.newOutputStream(archive.toPath()), "**");
        assertEquals(3, tarred, "One file should have been compressed");

        // Decompress
        final File dstFolder = newFolder(temp, "dst");
        dstFolder.mkdirs();
        FilePath outDir = new FilePath(dstFolder);
        // and now fail when flush is bad!
        tmpDirPath.child("../" + archive.getName()).untar(outDir, TarCompression.NONE);
    }

    @Test
    void chmod() throws Exception {
        assumeFalse(Functions.isWindows());
        File f = Files.createTempFile(temp.toPath(), "file", null).toFile();
        FilePath fp = new FilePath(f);
        int prevMode = fp.mode();
        assertEquals(0400, chmodAndMode(fp, 0400));
        assertEquals(0412, chmodAndMode(fp, 0412));
        assertEquals(0777, chmodAndMode(fp, 0777));
        assertEquals(prevMode, chmodAndMode(fp, prevMode));
    }

    @Test
    void chmodInvalidPermissions() throws Exception {
        assumeFalse(Functions.isWindows());
        File f = newFolder(temp, "folder");
        FilePath fp = new FilePath(f);
        int invalidMode = 01770; // Full permissions for owner and group plus sticky bit.
        final IOException e = assertThrows(IOException.class, () -> chmodAndMode(fp, invalidMode), "Setting sticky bit should fail");
        assertEquals("Invalid mode: " + invalidMode, e.getMessage());
    }

    private int chmodAndMode(FilePath path, int mode) throws Exception {
        path.chmod(mode);
        return path.mode();
    }

    @Issue("JENKINS-48227")
    @Test
    void testCreateTempDir() throws IOException, InterruptedException  {
        final File srcFolder = newFolder(temp, "src");
        final FilePath filePath = new FilePath(srcFolder);
        FilePath x = filePath.createTempDir("jdk", "dmg");
        FilePath y = filePath.createTempDir("jdk", "pkg");
        FilePath z = filePath.createTempDir("jdk", null);

        assertNotNull(x, "FilePath x should not be null");
        assertNotNull(y, "FilePath y should not be null");
        assertNotNull(z, "FilePath z should not be null");

        assertTrue(x.getName().contains("jdk.dmg"));
        assertTrue(y.getName().contains("jdk.pkg"));
        assertTrue(z.getName().contains("jdk.tmp"));
    }

    @Test
    void deleteRecursiveOnUnix() throws Exception {
        assumeFalse(Functions.isWindows());
        Path targetDir = newFolder(temp, "target").toPath();
        Path targetContents = Files.createFile(targetDir.resolve("contents.txt"));
        Path toDelete = newFolder(temp, "toDelete").toPath();
        Util.createSymlink(toDelete.toFile(), "../targetDir", "link", TaskListener.NULL);
        Files.createFile(toDelete.resolve("foo"));
        Files.createFile(toDelete.resolve("bar"));
        FilePath f = new FilePath(toDelete.toFile());
        f.deleteRecursive();
        assertTrue(Files.exists(targetDir), "symlink target should not be deleted");
        assertTrue(Files.exists(targetContents), "symlink target contents should not be deleted");
        assertFalse(Files.exists(toDelete), "could not delete target");
    }

    @Test
    @Issue("JENKINS-44909")
    void deleteSuffixesRecursive() throws Exception {
        File deleteSuffixesRecursiveFolder = newFolder(temp, "deleteSuffixesRecursive");
        FilePath filePath = new FilePath(deleteSuffixesRecursiveFolder);
        FilePath suffix = filePath.withSuffix(WorkspaceList.COMBINATOR + "suffixed");
        FilePath textTempFile = suffix.createTextTempFile("tmp", null, "dummy", true);

        assertThat(textTempFile.exists(), is(true));

        filePath.deleteSuffixesRecursive();
        assertThat(textTempFile.exists(), is(false));
    }

    @Test
    void deleteRecursiveOnWindows() throws Exception {
        assumeTrue(Functions.isWindows(), "Uses Windows-specific features");
        Path targetDir = newFolder(temp, "targetDir").toPath();
        Path targetContents = Files.createFile(targetDir.resolve("contents.txt"));
        Path toDelete = newFolder(temp, "toDelete").toPath();
        File junction = WindowsUtil.createJunction(toDelete.resolve("junction").toFile(), targetDir.toFile());
        Files.createFile(toDelete.resolve("foo"));
        Files.createFile(toDelete.resolve("bar"));
        FilePath f = new FilePath(toDelete.toFile());
        f.deleteRecursive();
        assertTrue(Files.exists(targetDir), "junction target should not be deleted");
        assertTrue(Files.exists(targetContents), "junction target contents should not be deleted");
        assertFalse(junction.exists(), "could not delete junction");
        assertFalse(Files.exists(toDelete), "could not delete target");
    }

    @Issue("JENKINS-13128")
    @Test
    void copyRecursivePreservesPosixFilePermissions() throws Exception {
        assumeFalse(Functions.isWindows());
        File src = newFolder(temp, "src");
        File dst = newFolder(temp, "dst");
        Path sourceFile = Files.createFile(src.toPath().resolve("test-file"));
        Set<PosixFilePermission> allRWX = EnumSet.allOf(PosixFilePermission.class);
        Files.setPosixFilePermissions(sourceFile, allRWX);
        FilePath f = new FilePath(src);
        f.copyRecursiveTo(new FilePath(dst));
        Path destinationFile = dst.toPath().resolve("test-file");
        assertTrue(Files.exists(destinationFile), "file was not copied");
        Set<PosixFilePermission> destinationPermissions = Files.getPosixFilePermissions(destinationFile);
        assertEquals(allRWX, destinationPermissions, "file permissions not copied");
    }

    @Issue("JENKINS-13128")
    @Test
    void copyRecursivePreservesLastModifiedTime() throws Exception {
        File src = newFolder(temp, "src");
        File dst = newFolder(temp, "dst");
        Path sourceFile = Files.createFile(src.toPath().resolve("test-file"));
        FileTime mtime = FileTime.from(42L, TimeUnit.SECONDS);
        Files.setLastModifiedTime(sourceFile, mtime);
        FilePath f = new FilePath(src);
        f.copyRecursiveTo(new FilePath(dst));
        Path destinationFile = dst.toPath().resolve("test-file");
        assertTrue(Files.exists(destinationFile), "file was not copied");
        assertEquals(mtime, Files.getLastModifiedTime(destinationFile), "file mtime was not preserved");
    }

    @Test
    @Issue("SECURITY-904")
    void isDescendant_regularFiles() throws IOException, InterruptedException {
        //  root
        //      /workspace
        //          /sub
        //              sub-regular.txt
        //          regular.txt
        //      /protected
        //          secret.txt
        FilePath rootFolder = new FilePath(newFolder(temp, "root"));
        FilePath workspaceFolder = rootFolder.child("workspace");
        FilePath subFolder = workspaceFolder.child("sub");
        FilePath protectedFolder = rootFolder.child("protected");

        FilePath regularFile = workspaceFolder.child("regular.txt");
        regularFile.write("regular-file", StandardCharsets.UTF_8.name());
        FilePath subRegularFile = subFolder.child("sub-regular.txt");
        subRegularFile.write("sub-regular-file", StandardCharsets.UTF_8.name());

        FilePath secretFile = protectedFolder.child("secret.txt");
        secretFile.write("secrets", StandardCharsets.UTF_8.name());

        assertTrue(workspaceFolder.isDescendant("."));
        assertTrue(workspaceFolder.isDescendant("regular.txt"));
        assertTrue(workspaceFolder.isDescendant("./regular.txt"));
        assertTrue(workspaceFolder.isDescendant("sub/sub-regular.txt"));
        assertTrue(workspaceFolder.isDescendant("sub//sub-regular.txt"));
        assertTrue(workspaceFolder.isDescendant("sub/../sub/sub-regular.txt"));
        assertTrue(workspaceFolder.isDescendant("./sub/../sub/sub-regular.txt"));

        // nonexistent files
        assertTrue(workspaceFolder.isDescendant("nonexistent.txt"));
        assertTrue(workspaceFolder.isDescendant("sub/nonexistent.txt"));
        assertTrue(workspaceFolder.isDescendant("nonexistent/nonexistent.txt"));
        assertFalse(workspaceFolder.isDescendant("../protected/nonexistent.txt"));
        assertFalse(workspaceFolder.isDescendant("../nonexistent/nonexistent.txt"));

        // the intermediate path "./.." goes out of workspace and so is refused
        assertFalse(workspaceFolder.isDescendant("./../workspace"));
        assertFalse(workspaceFolder.isDescendant("./../workspace/"));
        assertFalse(workspaceFolder.isDescendant("./../workspace/regular.txt"));
        assertFalse(workspaceFolder.isDescendant("../workspace/regular.txt"));
        assertFalse(workspaceFolder.isDescendant("./../../root/workspace/regular.txt"));

        // attempt to reach other folder
        assertFalse(workspaceFolder.isDescendant("../protected/secret.txt"));
        assertFalse(workspaceFolder.isDescendant("./../protected/secret.txt"));
    }

    @Test
    @Issue("SECURITY-904")
    void isDescendant_regularSymlinks() throws IOException, InterruptedException {
        assumeFalse(Functions.isWindows());
        //  root
        //      /workspace
        //          /a
        //              a.txt
        //          /b
        //              _a => symlink to ../a
        //              _atxt => symlink to ../a/a.txt
        //          regular.txt
        //          _nonexistent => symlink to nonexistent (nonexistent folder)
        //          _nonexistentUp => symlink to ../nonexistent (nonexistent folder + illegal)
        //          _protected => symlink to ../protected (illegal)
        //          _secrettxt => symlink to ../protected/secret.txt (illegal)
        //      /protected
        //          secret.txt
        FilePath rootFolder = new FilePath(newFolder(temp, "root"));
        FilePath workspaceFolder = rootFolder.child("workspace");
        FilePath aFolder = workspaceFolder.child("a");
        FilePath bFolder = workspaceFolder.child("b");

        FilePath regularFile = workspaceFolder.child("regular.txt");
        regularFile.write("regular-file", StandardCharsets.UTF_8.name());
        FilePath aFile = aFolder.child("a.txt");
        aFile.write("a-file", StandardCharsets.UTF_8.name());
        FilePath bFile = bFolder.child("a.txt");
        bFile.write("b-file", StandardCharsets.UTF_8.name());
        bFolder.child("_a").symlinkTo("../a", null);
        bFolder.child("_atxt").symlinkTo("../a/a.txt", null);
        // illegal symlinks
        workspaceFolder.child("_protected").symlinkTo("../protected", null);
        workspaceFolder.child("_nonexistent").symlinkTo("nonexistent", null);
        workspaceFolder.child("_nonexistentUp").symlinkTo("../nonexistent", null);
        workspaceFolder.child("_secrettxt").symlinkTo("../protected/secret.txt", null);

        FilePath protectedFolder = rootFolder.child("protected");
        FilePath secretFile = protectedFolder.child("secret.txt");
        secretFile.write("secrets", StandardCharsets.UTF_8.name());

        assertTrue(workspaceFolder.isDescendant("regular.txt"));
        assertTrue(workspaceFolder.isDescendant("_nonexistent"));
        assertTrue(workspaceFolder.isDescendant("a"));
        assertTrue(workspaceFolder.isDescendant("a/a.txt"));
        assertTrue(workspaceFolder.isDescendant("a/../a/a.txt"));
        assertTrue(workspaceFolder.isDescendant("b/../a/a.txt"));
        assertTrue(workspaceFolder.isDescendant("b"));
        assertTrue(workspaceFolder.isDescendant("./b"));
        assertTrue(workspaceFolder.isDescendant("b/_a/a.txt"));
        assertTrue(workspaceFolder.isDescendant("b/_a/../a/a.txt"));
        assertTrue(workspaceFolder.isDescendant("b/_atxt"));

        // nonexistent but illegal
        assertFalse(workspaceFolder.isDescendant("_nonexistentUp"));
        // illegal symlinks
        assertFalse(workspaceFolder.isDescendant("_protected"));
        assertFalse(workspaceFolder.isDescendant("_protected/"));
        assertFalse(workspaceFolder.isDescendant("_protected/secret.txt"));
        assertFalse(workspaceFolder.isDescendant("./_protected/secret.txt"));
        assertFalse(workspaceFolder.isDescendant("_secrettxt"));
        assertFalse(workspaceFolder.isDescendant("./_secrettxt"));
    }

    @Test
    @Issue("SECURITY-904")
    void isDescendant_windowsSpecificSymlinks() throws Exception {
        assumeTrue(Functions.isWindows());
        //  root
        //      /workspace
        //          /a
        //              a.txt
        //          /b
        //              b.txt
        //              _a => junction to ../a
        //          regular.txt
        //          _nonexistent => junction to nonexistent (nonexistent folder)
        //          _nonexistentUp => junction to ../nonexistent (nonexistent and illegal)
        //          _protected => junction to ../protected (illegal)
        //      /protected
        //          secret.txt
        File root = newFolder(temp, "root");
        FilePath rootFolder = new FilePath(root);
        FilePath workspaceFolder = rootFolder.child("workspace");
        FilePath aFolder = workspaceFolder.child("a");
        FilePath bFolder = workspaceFolder.child("b");

        FilePath regularFile = workspaceFolder.child("regular.txt");
        regularFile.write("regular-file", StandardCharsets.UTF_8.name());
        FilePath aFile = aFolder.child("a.txt");
        aFile.write("a-file", StandardCharsets.UTF_8.name());
        FilePath bFile = bFolder.child("a.txt");
        bFile.write("b-file", StandardCharsets.UTF_8.name());

        createJunction(new File(root, "/workspace/b/_a"), new File(root, "/workspace/a"));
        createJunction(new File(root, "/workspace/_nonexistent"), new File(root, "/workspace/nonexistent"));
        createJunction(new File(root, "/workspace/_nonexistentUp"), new File(root, "/nonexistent"));
        createJunction(new File(root, "/workspace/_protected"), new File(root, "/protected"));

        FilePath protectedFolder = rootFolder.child("protected");
        FilePath secretFile = protectedFolder.child("secret.txt");
        secretFile.write("secrets", StandardCharsets.UTF_8.name());

        assertTrue(workspaceFolder.isDescendant("b"));
        assertTrue(workspaceFolder.isDescendant("b/_a/a.txt"));
        assertTrue(workspaceFolder.isDescendant("b\\_a\\a.txt"));
        assertTrue(workspaceFolder.isDescendant("b\\_a\\../a/a.txt"));
        assertTrue(workspaceFolder.isDescendant("b\\_a\\..\\a\\a.txt"));
        assertTrue(workspaceFolder.isDescendant(".\\b\\_a\\..\\a\\a.txt"));
        assertTrue(workspaceFolder.isDescendant("b/_a/../a/a.txt"));
        assertTrue(workspaceFolder.isDescendant("./b/_a/../a/a.txt"));

        // nonexistent and not proven illegal, the junction links are not resolved
        // by Util.resolveSymlinkToFile / neither Path.toRealPath under Windows
        assertTrue(workspaceFolder.isDescendant("_nonexistent"));
        assertTrue(workspaceFolder.isDescendant("_nonexistent/"));
        assertTrue(workspaceFolder.isDescendant("_nonexistent/.."));
        assertTrue(workspaceFolder.isDescendant("_nonexistentUp"));

        // illegal symlinks
        assertFalse(workspaceFolder.isDescendant("_protected"));
        assertFalse(workspaceFolder.isDescendant("_protected/../a"));
    }

    private void createJunction(File from, File to) throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{"cmd", "/c", "mklink", "/J", from.getAbsolutePath(), to.getAbsolutePath()});
        p.waitFor(2, TimeUnit.SECONDS);
    }

    @Issue("SECURITY-904")
    public void isDescendant_throwIfParentDoesNotExist_symlink() throws Exception {
        assumeFalse(Functions.isWindows());
        FilePath rootFolder = new FilePath(newFolder(temp, "root"));
        FilePath aFolder = rootFolder.child("a");
        aFolder.mkdirs();
        FilePath linkToNonexistent = aFolder.child("linkToNonexistent");
        linkToNonexistent.symlinkTo("__nonexistent__", null);

        assertThat(linkToNonexistent.isDescendant("."), is(false));
    }

    @Issue("SECURITY-904")
    public void isDescendant_throwIfParentDoesNotExist_directNonexistent() throws Exception {
        FilePath rootFolder = new FilePath(newFolder(temp, "root"));
        FilePath nonexistent = rootFolder.child("nonexistent");
        assertThat(nonexistent.isDescendant("."), is(false));
    }

    @Test
    @Issue("SECURITY-904")
    void isDescendant_throwIfAbsolutePathGiven() throws Exception {
        FilePath rootFolder = new FilePath(newFolder(temp, "root"));
        rootFolder.mkdirs();
        assertThrows(IllegalArgumentException.class, () -> rootFolder.isDescendant(Files.createTempFile(temp.toPath(), "junit", null).toFile().getAbsolutePath()));
    }

    @Test
    @Issue("SECURITY-904")
    void isDescendant_worksEvenInSymbolicWorkspace() throws Exception {
        assumeFalse(Functions.isWindows());
        //  root
        //      /w
        //          /_workspace => symlink to ../workspace
        //      /workspace
        //          /a
        //              a.txt
        //          /b
        //              _a => symlink to ../a
        //              _atxt => symlink to ../a/a.txt
        //          regular.txt
        //          _nonexistent => symlink to nonexistent (nonexistent folder)
        //          _nonexistentUp => symlink to ../nonexistent (nonexistent folder + illegal)
        //          _protected => symlink to ../protected (illegal)
        //          _secrettxt => symlink to ../protected/secret.txt (illegal)
        //      /protected
        //          secret.txt
        FilePath rootFolder = new FilePath(newFolder(temp, "root"));
        FilePath workspaceFolder = rootFolder.child("workspace");
        FilePath aFolder = workspaceFolder.child("a");
        FilePath bFolder = workspaceFolder.child("b");

        FilePath regularFile = workspaceFolder.child("regular.txt");
        regularFile.write("regular-file", StandardCharsets.UTF_8.name());
        FilePath aFile = aFolder.child("a.txt");
        aFile.write("a-file", StandardCharsets.UTF_8.name());
        FilePath bFile = bFolder.child("a.txt");
        bFile.write("b-file", StandardCharsets.UTF_8.name());
        bFolder.child("_a").symlinkTo("../a", null);
        bFolder.child("_atxt").symlinkTo("../a/a.txt", null);
        // illegal symlinks
        workspaceFolder.child("_protected").symlinkTo("../protected", null);
        workspaceFolder.child("_protected2").symlinkTo("../../protected", null);
        workspaceFolder.child("_nonexistent").symlinkTo("nonexistent", null);
        workspaceFolder.child("_nonexistentUp").symlinkTo("../nonexistent", null);
        workspaceFolder.child("_secrettxt").symlinkTo("../protected/secret.txt", null);
        workspaceFolder.child("_secrettxt2").symlinkTo("../../protected/secret.txt", null);

        FilePath wFolder = rootFolder.child("w");
        wFolder.mkdirs();
        FilePath symbolicWorkspace = wFolder.child("_w");
        symbolicWorkspace.symlinkTo("../workspace", null);

        FilePath protectedFolder = rootFolder.child("protected");
        FilePath secretFile = protectedFolder.child("secret.txt");
        secretFile.write("secrets", StandardCharsets.UTF_8.name());

        assertTrue(symbolicWorkspace.isDescendant("regular.txt"));
        assertTrue(symbolicWorkspace.isDescendant("_nonexistent"));
        assertTrue(symbolicWorkspace.isDescendant("a"));
        assertTrue(symbolicWorkspace.isDescendant("a/a.txt"));
        assertTrue(symbolicWorkspace.isDescendant("b"));
        assertTrue(symbolicWorkspace.isDescendant("b/_a/a.txt"));
        assertTrue(symbolicWorkspace.isDescendant("b/_atxt"));

        // nonexistent but illegal
        assertFalse(symbolicWorkspace.isDescendant("_nonexistentUp"));
        // illegal symlinks
        assertFalse(symbolicWorkspace.isDescendant("_protected"));
        assertFalse(symbolicWorkspace.isDescendant("_protected/"));
        assertFalse(symbolicWorkspace.isDescendant("_protected/secret.txt"));
        assertFalse(symbolicWorkspace.isDescendant("./_protected/secret.txt"));
        assertFalse(symbolicWorkspace.isDescendant("_protected2"));
        assertFalse(symbolicWorkspace.isDescendant("_protected2/secret.txt"));
        assertFalse(symbolicWorkspace.isDescendant("_secrettxt"));
        assertFalse(symbolicWorkspace.isDescendant("./_secrettxt"));
        assertFalse(symbolicWorkspace.isDescendant("_secrettxt2"));
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }
}
