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
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Functions;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class PathRemoverTest {

    @TempDir
    private File tmp;

    private final Map<File, Closeable> locks = new HashMap<>();

    @AfterEach
    void tearDown() {
        List<IOException> exceptions = new ArrayList<>();
        Iterator<Closeable> it = locks.values().iterator();
        while (it.hasNext()) {
            try (Closeable ignored = it.next()) {
                it.remove();
            } catch (IOException e) {
                exceptions.add(e);
            }
        }
        assertTrue(exceptions.isEmpty(), "Could not unlock all files" + StringUtils.join(exceptions, '\n'));
    }

    private synchronized void acquireLock(@NonNull File file) throws IOException {
        assertTrue(Functions.isWindows());
        assertThat(file + " is already locked.", locks, not(hasKey(file)));
        Closeable lock = new FileInputStream(file);
        locks.put(file, lock);
    }

    private synchronized void releaseLock(@NonNull File file) throws Exception {
        assertTrue(Functions.isWindows());
        assertThat(file + " is not locked.", locks, hasKey(file));
        locks.remove(file).close();
    }

    @Test
    void testForceRemoveFile() throws IOException {
        File file = File.createTempFile("junit", null, tmp);
        touchWithFileName(file);

        PathRemover remover = PathRemover.newSimpleRemover();
        remover.forceRemoveFile(file.toPath());

        assertFalse(file.exists(), "Unable to delete file: " + file);
    }

    @Test
    void testForceRemoveFile_LockedFile() throws Exception {
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
            if (t instanceof CompositeIOException e) {
                e.getExceptions().forEach(ex -> hierarchy.addAll(calcExceptionHierarchy(ex)));
            }
        }
        return hierarchy;
    }

    @Test
    void testForceRemoveFile_ReadOnly() throws IOException {
        File dir = newFolder(tmp, "junit-" + System.currentTimeMillis());
        File file = new File(dir, "file.tmp");
        touchWithFileName(file);
        assertTrue(file.setWritable(false), "Unable to make file read-only: " + file);

        PathRemover remover = PathRemover.newSimpleRemover();
        remover.forceRemoveFile(file.toPath());

        assertFalse(file.exists(), "Unable to delete file: " + file);
    }

    @Test
    void testForceRemoveFile_DoesNotExist() throws IOException {
        File dir = newFolder(tmp, "junit-" + System.currentTimeMillis());
        File file = new File(dir, "invalid.file");
        assertFalse(file.exists());

        PathRemover remover = PathRemover.newSimpleRemover();
        remover.forceRemoveFile(file.toPath());

        assertFalse(file.exists(), "Unable to delete file: " + file);
    }

    @Test
    void testForceRemoveFile_SymbolicLink() throws IOException {
        assumeFalse(Functions.isWindows());
        File file = File.createTempFile("junit", null, tmp);
        touchWithFileName(file);
        Path link = Files.createSymbolicLink(tmp.toPath().resolve("test-link"), file.toPath());

        PathRemover remover = PathRemover.newSimpleRemover();
        remover.forceRemoveFile(link);

        assertTrue(Files.notExists(link, LinkOption.NOFOLLOW_LINKS), "Unable to delete symbolic link: " + link);
        assertTrue(file.exists(), "Should not have deleted target file: " + file);
    }

    @Test
    @Issue("JENKINS-55448")
    void testForceRemoveFile_DotsInPath() throws IOException {
        Path folder = newFolder(tmp, "junit-" + System.currentTimeMillis()).toPath();
        File test = File.createTempFile("test", null, tmp);
        touchWithFileName(test);
        Path path = folder.resolve("../" + test.getName());

        PathRemover remover = PathRemover.newSimpleRemover();
        remover.forceRemoveFile(path);

        assertTrue(Files.notExists(path), "Unable to delete file: " + path);
        assertFalse(test.exists());
        assertTrue(Files.exists(folder), "Should not have deleted directory: " + folder);
    }

    @Test
    @Issue("JENKINS-55448")
    void testForceRemoveFile_ParentIsSymbolicLink() throws IOException {
        assumeFalse(Functions.isWindows());
        Path realParent = newFolder(tmp, "junit-" + System.currentTimeMillis()).toPath();
        Path path = realParent.resolve("test-file");
        touchWithFileName(path.toFile());
        Path symParent = Files.createSymbolicLink(tmp.toPath().resolve("sym-parent"), realParent);
        Path toDelete = symParent.resolve("test-file");

        PathRemover remover = PathRemover.newSimpleRemover();
        remover.forceRemoveFile(toDelete);

        assertTrue(Files.notExists(toDelete), "Unable to delete file: " + toDelete);
        assertTrue(Files.exists(realParent), "Should not have deleted directory: " + realParent);
        assertTrue(Files.exists(symParent, LinkOption.NOFOLLOW_LINKS), "Should not have deleted symlink: " + symParent);
    }

    @Test
    void testForceRemoveDirectoryContents() throws IOException {
        File dir = newFolder(tmp, "junit-" + System.currentTimeMillis());
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
    void testForceRemoveDirectoryContents_LockedFile() throws Exception {
        assumeTrue(Functions.isWindows());
        File dir = newFolder(tmp, "junit-" + System.currentTimeMillis());
        File d1 = new File(dir, "d1");
        File d2 = new File(dir, "d2");
        File f1 = new File(dir, "f1");
        File d1f1 = new File(d1, "d1f1");
        File d2f2 = new File(d2, "d1f2");
        mkdirs(d1, d2);
        touchWithFileName(f1, d1f1, d2f2);
        acquireLock(d1f1);
        PathRemover remover = PathRemover.newRemoverWithStrategy(retriesAttempted -> retriesAttempted < 1);
        Exception e = assertThrows(IOException.class, () -> remover.forceRemoveDirectoryContents(dir.toPath()));
        assertThat(e.getMessage(), allOf(containsString(dir.getPath()), containsString("Tried 1 time.")));
        assertFalse(d2.exists());
        assertFalse(f1.exists());
        assertFalse(d2f2.exists());
    }

    @Test
    void testForceRemoveRecursive() throws IOException {
        File dir = newFolder(tmp, "junit-" + System.currentTimeMillis());
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
    void testForceRemoveRecursive_DeletesAsMuchAsPossibleWithLockedFiles() throws Exception {
        assumeTrue(Functions.isWindows());
        File dir = newFolder(tmp, "junit-" + System.currentTimeMillis());
        File d1 = new File(dir, "d1");
        File d2 = new File(dir, "d2");
        File f1 = new File(dir, "f1");
        File d1f1 = new File(d1, "d1f1");
        File d2f2 = new File(d2, "d1f2");
        mkdirs(d1, d2);
        touchWithFileName(f1, d1f1, d2f2);

        acquireLock(d1f1);
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
    void testForceRemoveRecursive_RetryOnFailure() throws Exception {
        assumeTrue(Functions.isWindows());
        File dir = newFolder(tmp, "junit-" + System.currentTimeMillis());
        File d1 = new File(dir, "d1");
        File d2 = new File(dir, "d2");
        File f1 = new File(dir, "f1");
        File d1f1 = new File(d1, "d1f1");
        File d2f2 = new File(d2, "d1f2");
        mkdirs(d1, d2);
        touchWithFileName(f1, d1f1, d2f2);
        acquireLock(d2f2);
        CountDownLatch unlockLatch = new CountDownLatch(1);
        CountDownLatch deleteLatch = new CountDownLatch(1);
        AtomicBoolean lockedFileExists = new AtomicBoolean();
        Thread thread = new Thread(() -> {
            try {
                unlockLatch.await();
                releaseLock(d2f2);
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
    void testForceRemoveRecursive_FailsWhenInterrupted() throws Exception {
        assumeTrue(Functions.isWindows());
        File dir = newFolder(tmp, "junit-" + System.currentTimeMillis());
        File d1 = new File(dir, "d1");
        File d2 = new File(dir, "d2");
        File f1 = new File(dir, "f1");
        File d1f1 = new File(d1, "d1f1");
        File d2f2 = new File(d2, "d1f2");
        mkdirs(d1, d2);
        touchWithFileName(f1, d1f1, d2f2);
        acquireLock(d1f1);
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
    void testForceRemoveRecursive_ContainsSymbolicLinks() throws IOException {
        assumeFalse(Functions.isWindows());
        File folder = newFolder(tmp, "junit" + System.currentTimeMillis());
        File d1 = new File(folder, "d1");
        File d1f1 = new File(d1, "d1f1");
        File f2 = new File(folder, "f2");
        mkdirs(d1);
        touchWithFileName(d1f1, f2);
        Path path = newFolder(tmp, "junit-" + System.currentTimeMillis()).toPath();
        Files.createSymbolicLink(path.resolve("sym-dir"), d1.toPath());
        Files.createSymbolicLink(path.resolve("sym-file"), f2.toPath());

        PathRemover remover = PathRemover.newSimpleRemover();
        remover.forceRemoveRecursive(path);

        assertTrue(Files.notExists(path), "Unable to delete directory: " + path);
        for (File file : Arrays.asList(d1, d1f1, f2)) {
            assertTrue(file.exists(), "Should not have deleted target: " + file);
        }
    }

    @Test
    @Issue("JENKINS-55448")
    void testForceRemoveRecursive_ContainsDotPath() throws IOException {
        File folder = newFolder(tmp, "junit" + System.currentTimeMillis());
        File d1 = new File(folder, "d1");
        File d1f1 = new File(d1, "d1f1");
        File f2 = new File(folder, "f2");
        mkdirs(d1);
        touchWithFileName(d1f1, f2);
        Path path = Paths.get(d1.getPath(), "..", "d1");

        PathRemover remover = PathRemover.newSimpleRemover();
        remover.forceRemoveRecursive(path);

        assertTrue(Files.notExists(path), "Unable to delete directory: " + folder);
    }

    @Test
    @Issue("JENKINS-55448")
    void testForceRemoveRecursive_ParentIsSymbolicLink() throws IOException {
        assumeFalse(Functions.isWindows());
        File folder = newFolder(tmp, "junit" + System.currentTimeMillis());
        File d1 = new File(folder, "d1");
        File d1f1 = new File(d1, "d1f1");
        File f2 = new File(folder, "f2");
        mkdirs(d1);
        touchWithFileName(d1f1, f2);
        Path symlink = Files.createSymbolicLink(tmp.toPath().resolve("linked"), folder.toPath());
        Path d1p = symlink.resolve("d1");

        PathRemover remover = PathRemover.newSimpleRemover();
        remover.forceRemoveRecursive(d1p);

        assertTrue(Files.notExists(d1p), "Unable to delete directory: " + d1p);
        assertFalse(d1.exists());
    }

    @Test
    @Issue("JENKINS-55448")
    void testForceRemoveRecursive_TruncatesNumberOfExceptions() throws IOException {
        assumeTrue(Functions.isWindows());
        final int maxExceptions = CompositeIOException.EXCEPTION_LIMIT;
        final int lockedFiles = maxExceptions + 5;
        final int totalFiles = lockedFiles + 5;
        File dir = newFolder(tmp, "junit-" + System.currentTimeMillis());
        File[] files = new File[totalFiles];
        for (int i = 0; i < totalFiles; i++) {
            files[i] = new File(dir, "f" + i);
        }
        touchWithFileName(files);
        for (int i = 0; i < lockedFiles; i++) {
            acquireLock(files[i]);
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
            assertTrue(dir.mkdir(), "Could not mkdir " + dir);
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

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.exists() && !result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }

}
