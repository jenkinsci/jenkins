/*
 * The MIT License
 *
 * Copyright (c) 2018 CloudBees, Inc.
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

package jenkins.util.io;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import hudson.Functions;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.Issue;

public class PathRemoverTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    @Rule public Timeout timeout = new Timeout(30, TimeUnit.SECONDS);
    @Rule public FileLockerRule locker = new FileLockerRule();

    @Test
    public void testForceRemoveFile() throws IOException {
        File file = tmp.newFile();
        touchWithFileName(file);

        PathRemover remover = PathRemover.newSimpleRemover();
        remover.forceRemoveFile(file.toPath());

        assertFalse("Unable to delete file: " + file, file.exists());
    }

    @Test
    public void testForceRemoveFile_LockedFile() throws Exception {
        String filename = "/var/lock/jenkins.lock";
        File file = mock(File.class);
        Path path = mock(Path.class);
        FileSystem fs = mock(FileSystem.class);
        FileSystemProvider fsProvider = mock(FileSystemProvider.class);
        BasicFileAttributes attributes = mock(BasicFileAttributes.class);

        given(file.getPath()).willReturn(filename);
        given(file.toPath()).willReturn(path);
        given(path.toString()).willReturn(filename);
        given(path.toFile()).willReturn(file);
        given(path.getFileSystem()).willReturn(fs);
        given(path.normalize()).willReturn(path);
        given(fs.provider()).willReturn(fsProvider);
        given(fsProvider.deleteIfExists(path)).willThrow(new FileSystemException(filename));
        given(fsProvider.readAttributes(path, BasicFileAttributes.class)).willReturn(attributes);
        given(attributes.isDirectory()).willReturn(false);

        PathRemover remover = PathRemover.newSimpleRemover();
        final IOException e = assertThrows(IOException.class, () -> remover.forceRemoveFile(file.toPath()));
        assertThat(calcExceptionHierarchy(e), hasItem(FileSystemException.class));
        assertThat(e.getMessage(), containsString(filename));
    }

    private static List<Class<?>> calcExceptionHierarchy(Throwable t) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (; t != null; t = t.getCause()) {
            hierarchy.add(t.getClass());
            // with a composite exception, we might be hiding some other causes
            if (t instanceof CompositeIOException) {
                CompositeIOException e = (CompositeIOException) t;
                e.getExceptions().forEach(ex -> hierarchy.addAll(calcExceptionHierarchy(ex)));
            }
        }
        return hierarchy;
    }

    @Test
    public void testForceRemoveFile_ReadOnly() throws IOException {
        File dir = tmp.newFolder();
        File file = new File(dir, "file.tmp");
        touchWithFileName(file);
        assertTrue("Unable to make file read-only: " + file, file.setWritable(false));

        PathRemover remover = PathRemover.newSimpleRemover();
        remover.forceRemoveFile(file.toPath());

        assertFalse("Unable to delete file: " + file, file.exists());
    }

    @Test
    public void testForceRemoveFile_DoesNotExist() throws IOException {
        File dir = tmp.newFolder();
        File file = new File(dir, "invalid.file");
        assertFalse(file.exists());

        PathRemover remover = PathRemover.newSimpleRemover();
        remover.forceRemoveFile(file.toPath());

        assertFalse("Unable to delete file: " + file, file.exists());
    }

    @Test
    public void testForceRemoveFile_SymbolicLink() throws IOException {
        assumeFalse(Functions.isWindows());
        File file = tmp.newFile();
        touchWithFileName(file);
        Path link = Files.createSymbolicLink(tmp.getRoot().toPath().resolve("test-link"), file.toPath());

        PathRemover remover = PathRemover.newSimpleRemover();
        remover.forceRemoveFile(link);

        assertTrue("Unable to delete symbolic link: " + link, Files.notExists(link, LinkOption.NOFOLLOW_LINKS));
        assertTrue("Should not have deleted target file: " + file, file.exists());
    }

    @Test
    @Issue("JENKINS-55448")
    public void testForceRemoveFile_DotsInPath() throws IOException {
        Path folder = tmp.newFolder().toPath();
        File test = tmp.newFile("test");
        touchWithFileName(test);
        Path path = folder.resolve("../test");

        PathRemover remover = PathRemover.newSimpleRemover();
        remover.forceRemoveFile(path);

        assertTrue("Unable to delete file: " + path, Files.notExists(path));
        assertFalse(test.exists());
        assertTrue("Should not have deleted directory: " + folder, Files.exists(folder));
    }

    @Test
    @Issue("JENKINS-55448")
    public void testForceRemoveFile_ParentIsSymbolicLink() throws IOException {
        assumeFalse(Functions.isWindows());
        Path realParent = tmp.newFolder().toPath();
        Path path = realParent.resolve("test-file");
        touchWithFileName(path.toFile());
        Path symParent = Files.createSymbolicLink(tmp.getRoot().toPath().resolve("sym-parent"), realParent);
        Path toDelete = symParent.resolve("test-file");

        PathRemover remover = PathRemover.newSimpleRemover();
        remover.forceRemoveFile(toDelete);

        assertTrue("Unable to delete file: " + toDelete, Files.notExists(toDelete));
        assertTrue("Should not have deleted directory: " + realParent, Files.exists(realParent));
        assertTrue("Should not have deleted symlink: " + symParent, Files.exists(symParent, LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    public void testForceRemoveDirectoryContents() throws IOException {
        File dir = tmp.newFolder();
        File d1 = new File(dir, "d1");
        File d2 = new File(dir, "d2");
        File f1 = new File(dir, "f1");
        File d1f1 = new File(d1, "d1f1");
        File d2f2 = new File(d2, "d1f2");
        mkdirs(d1, d2);
        touchWithFileName(f1, d1f1, d2f2);

        PathRemover remover = PathRemover.newSimpleRemover();
        remover.forceRemoveDirectoryContents(dir.toPath());

        assertTrue(dir.exists());
        assertFalse(d1.exists());
        assertFalse(d2.exists());
        assertFalse(f1.exists());
    }

    @Test
    public void testForceRemoveDirectoryContents_LockedFile() throws Exception {
        assumeTrue(Functions.isWindows());
        File dir = tmp.newFolder();
        File d1 = new File(dir, "d1");
        File d2 = new File(dir, "d2");
        File f1 = new File(dir, "f1");
        File d1f1 = new File(d1, "d1f1");
        File d2f2 = new File(d2, "d1f2");
        mkdirs(d1, d2);
        touchWithFileName(f1, d1f1, d2f2);
        locker.acquireLock(d1f1);
        PathRemover remover = PathRemover.newRemoverWithStrategy(retriesAttempted -> retriesAttempted < 1);
        Exception e = assertThrows(IOException.class, () -> remover.forceRemoveDirectoryContents(dir.toPath()));
        assertThat(e.getMessage(), allOf(containsString(dir.getPath()), containsString("Tried 1 time.")));
        assertFalse(d2.exists());
        assertFalse(f1.exists());
        assertFalse(d2f2.exists());
    }

    @Test
    public void testForceRemoveRecursive() throws IOException {
        File dir = tmp.newFolder();
        File d1 = new File(dir, "d1");
        File d2 = new File(dir, "d2");
        File f1 = new File(dir, "f1");
        File d1f1 = new File(d1, "d1f1");
        File d2f2 = new File(d2, "d1f2");
        mkdirs(d1, d2);
        touchWithFileName(f1, d1f1, d2f2);

        PathRemover remover = PathRemover.newSimpleRemover();
        remover.forceRemoveRecursive(dir.toPath());

        assertFalse(dir.exists());
    }

    @Test
    public void testForceRemoveRecursive_DeletesAsMuchAsPossibleWithLockedFiles() throws Exception {
        assumeTrue(Functions.isWindows());
        File dir = tmp.newFolder();
        File d1 = new File(dir, "d1");
        File d2 = new File(dir, "d2");
        File f1 = new File(dir, "f1");
        File d1f1 = new File(d1, "d1f1");
        File d2f2 = new File(d2, "d1f2");
        mkdirs(d1, d2);
        touchWithFileName(f1, d1f1, d2f2);

        locker.acquireLock(d1f1);
        PathRemover remover = PathRemover.newSimpleRemover();
        Exception e = assertThrows(IOException.class, () -> remover.forceRemoveRecursive(dir.toPath()));
        assertThat(e.getMessage(), containsString(dir.getPath()));
        assertTrue(dir.exists());
        assertTrue(d1.exists());
        assertTrue(d1f1.exists());
        assertFalse(d2.exists());
        assertFalse(d2f2.exists());
        assertFalse(f1.exists());
    }

    @Test
    public void testForceRemoveRecursive_RetryOnFailure() throws Exception {
        assumeTrue(Functions.isWindows());
        File dir = tmp.newFolder();
        File d1 = new File(dir, "d1");
        File d2 = new File(dir, "d2");
        File f1 = new File(dir, "f1");
        File d1f1 = new File(d1, "d1f1");
        File d2f2 = new File(d2, "d1f2");
        mkdirs(d1, d2);
        touchWithFileName(f1, d1f1, d2f2);
        locker.acquireLock(d2f2);
        CountDownLatch unlockLatch = new CountDownLatch(1);
        CountDownLatch deleteLatch = new CountDownLatch(1);
        AtomicBoolean lockedFileExists = new AtomicBoolean();
        Thread thread = new Thread(() -> {
            try {
                unlockLatch.await();
                locker.releaseLock(d2f2);
                deleteLatch.countDown();
            } catch (Exception ignored) {
            }
        });
        thread.start();
        PathRemover remover = PathRemover.newRemoverWithStrategy(retriesAttempted -> {
            if (retriesAttempted == 0) {
                lockedFileExists.set(d2f2.exists());
                unlockLatch.countDown();
                try {
                    deleteLatch.await();
                    return true;
                } catch (InterruptedException e) {
                    return false;
                }
            }
            return false;
        });
        remover.forceRemoveRecursive(dir.toPath());
        thread.join();
        assertTrue(lockedFileExists.get());
        assertFalse(dir.exists());
    }

    @Test
    public void testForceRemoveRecursive_FailsWhenInterrupted() throws Exception {
        assumeTrue(Functions.isWindows());
        File dir = tmp.newFolder();
        File d1 = new File(dir, "d1");
        File d2 = new File(dir, "d2");
        File f1 = new File(dir, "f1");
        File d1f1 = new File(d1, "d1f1");
        File d2f2 = new File(d2, "d1f2");
        mkdirs(d1, d2);
        touchWithFileName(f1, d1f1, d2f2);
        locker.acquireLock(d1f1);
        AtomicReference<InterruptedException> interrupted = new AtomicReference<>();
        AtomicReference<IOException> removed = new AtomicReference<>();
        PathRemover remover = PathRemover.newRemoverWithStrategy(retriesAttempted -> {
            try {
                TimeUnit.SECONDS.sleep(retriesAttempted + 1);
                return true;
            } catch (InterruptedException e) {
                interrupted.set(e);
                return false;
            }
        });
        Thread thread = new Thread(() -> {
            try {
                remover.forceRemoveRecursive(dir.toPath());
            } catch (IOException e) {
                removed.set(e);
            }
        });
        thread.start();
        TimeUnit.MILLISECONDS.sleep(100);
        thread.interrupt();
        thread.join();
        assertFalse(thread.isAlive());
        assertTrue(d1f1.exists());
        IOException ioException = removed.get();
        assertNotNull(ioException);
        assertThat(ioException.getMessage(), containsString(dir.getPath()));
        assertNotNull(interrupted.get());
    }

    @Test
    public void testForceRemoveRecursive_ContainsSymbolicLinks() throws IOException {
        assumeFalse(Functions.isWindows());
        File folder = tmp.newFolder();
        File d1 = new File(folder, "d1");
        File d1f1 = new File(d1, "d1f1");
        File f2 = new File(folder, "f2");
        mkdirs(d1);
        touchWithFileName(d1f1, f2);
        Path path = tmp.newFolder().toPath();
        Files.createSymbolicLink(path.resolve("sym-dir"), d1.toPath());
        Files.createSymbolicLink(path.resolve("sym-file"), f2.toPath());

        PathRemover remover = PathRemover.newSimpleRemover();
        remover.forceRemoveRecursive(path);

        assertTrue("Unable to delete directory: " + path, Files.notExists(path));
        for (File file : Arrays.asList(d1, d1f1, f2)) {
            assertTrue("Should not have deleted target: " + file, file.exists());
        }
    }

    @Test
    @Issue("JENKINS-55448")
    public void testForceRemoveRecursive_ContainsDotPath() throws IOException {
        File folder = tmp.newFolder();
        File d1 = new File(folder, "d1");
        File d1f1 = new File(d1, "d1f1");
        File f2 = new File(folder, "f2");
        mkdirs(d1);
        touchWithFileName(d1f1, f2);
        Path path = Paths.get(d1.getPath(), "..", "d1");

        PathRemover remover = PathRemover.newSimpleRemover();
        remover.forceRemoveRecursive(path);

        assertTrue("Unable to delete directory: " + folder, Files.notExists(path));
    }

    @Test
    @Issue("JENKINS-55448")
    public void testForceRemoveRecursive_ParentIsSymbolicLink() throws IOException {
        assumeFalse(Functions.isWindows());
        File folder = tmp.newFolder();
        File d1 = new File(folder, "d1");
        File d1f1 = new File(d1, "d1f1");
        File f2 = new File(folder, "f2");
        mkdirs(d1);
        touchWithFileName(d1f1, f2);
        Path symlink = Files.createSymbolicLink(tmp.getRoot().toPath().resolve("linked"), folder.toPath());
        Path d1p = symlink.resolve("d1");

        PathRemover remover = PathRemover.newSimpleRemover();
        remover.forceRemoveRecursive(d1p);

        assertTrue("Unable to delete directory: " + d1p, Files.notExists(d1p));
        assertFalse(d1.exists());
    }

    @Test
    @Issue("JENKINS-55448")
    public void testForceRemoveRecursive_TruncatesNumberOfExceptions() throws IOException {
        assumeTrue(Functions.isWindows());
        final int maxExceptions = CompositeIOException.EXCEPTION_LIMIT;
        final int lockedFiles = maxExceptions + 5;
        final int totalFiles = lockedFiles + 5;
        File dir = tmp.newFolder();
        File[] files = new File[totalFiles];
        for (int i = 0; i < totalFiles; i++) {
            files[i] = new File(dir, "f" + i);
        }
        touchWithFileName(files);
        for (int i = 0; i < lockedFiles; i++) {
            locker.acquireLock(files[i]);
        }

        final CompositeIOException e = assertThrows(CompositeIOException.class,
                () -> PathRemover.newSimpleRemover().forceRemoveRecursive(dir.toPath()));
        assertThat(e.getSuppressed(), arrayWithSize(maxExceptions));
        assertThat(e.getMessage(), endsWith("(Discarded " + (lockedFiles + 1 - maxExceptions) + " additional exceptions)"));

        assertTrue(dir.exists());
        assertThat(dir.listFiles().length, equalTo(lockedFiles));
    }

    private static void mkdirs(File... dirs) {
        for (File dir : dirs) {
            assertTrue("Could not mkdir " + dir, dir.mkdir());
            assertTrue(dir.isDirectory());
        }
    }

    private static void touchWithFileName(File... files) throws IOException {
        for (File file : files) {
            try (Writer writer = Files.newBufferedWriter(file.toPath(), Charset.defaultCharset())) {
                writer.append(file.getName()).append(System.lineSeparator());
            }
            assertTrue(file.isFile());
        }
    }

}
