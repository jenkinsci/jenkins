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

import hudson.Functions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Timeout;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class PathRemoverTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    @Rule public ExpectedException expectedException = ExpectedException.none();
    @Rule public Timeout timeout = new Timeout(10, TimeUnit.SECONDS);
    @Rule public FileLockerRule locker = new FileLockerRule();

    @Test
    public void testForceRemoveFile() throws IOException {
        File file = tmp.newFile();
        touchWithFileName(file);

        PathRemover remover = PathRemover.newSimpleRemover();
        remover.forceRemoveFile(file.toPath());

        assertFalse(file.exists());
    }

    @Test
    public void testForceRemoveFile_LockedFile() throws Exception {
        assumeTrue(Functions.isWindows());
        File file = tmp.newFile();
        touchWithFileName(file);
        locker.acquireLock(file);

        PathRemover remover = PathRemover.newSimpleRemover();
        expectedException.expectMessage(containsString(file.getPath()));

        remover.forceRemoveFile(file.toPath());
    }

    @Test
    public void testForceRemoveFile_ReadOnly() throws IOException {
        File dir = tmp.newFolder();
        File file = new File(dir, "file.tmp");
        touchWithFileName(file);
        assertTrue(file.setWritable(false));
        assertTrue(dir.setWritable(false));

        PathRemover remover = PathRemover.newSimpleRemover();
        remover.forceRemoveFile(file.toPath());

        assertFalse(file.exists());
    }

    @Test
    public void testForceRemoveFile_DoesNotExist() throws IOException {
        File dir = tmp.newFolder();
        File file = new File(dir, "invalid.file");
        assertFalse(file.exists());

        PathRemover remover = PathRemover.newSimpleRemover();
        remover.forceRemoveFile(file.toPath());

        assertFalse(file.exists());
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
        expectedException.expectMessage(allOf(
                containsString(dir.getPath()),
                containsString("Tried 1 time.")
        ));
        remover.forceRemoveDirectoryContents(dir.toPath());
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
        expectedException.expectMessage(containsString(dir.getPath()));
        remover.forceRemoveRecursive(dir.toPath());
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
        Object readyToUnlockSignal = new Object();
        Object readyToDeleteSignal = new Object();
        AtomicBoolean lockedFileExists = new AtomicBoolean();
        Thread thread = new Thread(() -> {
            try {
                readyToUnlockSignal.wait();
                locker.releaseLock(d2f2);
                readyToDeleteSignal.notifyAll();
            } catch (Exception ignored) {
            }
        });
        thread.start();
        PathRemover remover = PathRemover.newRemoverWithStrategy(retriesAttempted -> {
            if (retriesAttempted == 0) {
                lockedFileExists.set(d2f2.exists());
                readyToUnlockSignal.notifyAll();
                try {
                    readyToDeleteSignal.wait();
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

    private static void mkdirs(File... dirs) {
        for (File dir : dirs) {
            assertTrue("Could not mkdir " + dir, dir.mkdir());
            assertTrue(dir.isDirectory());
        }
    }

    private static void touchWithFileName(File... files) throws IOException {
        for (File file : files) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.append(file.getName()).append(System.lineSeparator());
            }
            assertTrue(file.isFile());
        }
    }

}