/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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

package hudson.util.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher.LocalLauncher;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;

class TarArchiverTest {

    @TempDir
    private File tmp;

    /**
     * Makes sure that permissions are properly stored in the tar file.
     */
    @Issue("JENKINS-9397")
    @Test
    void permission() throws Exception {
        assumeFalse(Functions.isWindows());

        File tar = File.createTempFile("test", "tar");
        File zip = File.createTempFile("test", "zip");

        FilePath dir = new FilePath(File.createTempFile("test", "dir"));

        try {
            dir.delete();
            dir.child("subdir").mkdirs();

            FilePath f = dir.child("a.txt");
            f.touch(0);
            f.chmod(0755);

            f = dir.child("subdir/b.txt");
            f.touch(0);
            f.chmod(0644);
            int dirMode = dir.child("subdir").mode();

            dir.tar(Files.newOutputStream(tar.toPath()), "**/*");
            dir.zip(Files.newOutputStream(zip.toPath()));


            FilePath e = dir.child("extract");
            e.mkdirs();

            // extract via the tar command
            run(e, "tar", "xvpf", tar.getAbsolutePath());

            assertEquals(0755, e.child("a.txt").mode());
            assertEquals(dirMode, e.child("subdir").mode());
            assertEquals(0644, e.child("subdir/b.txt").mode());


            // extract via the zip command
            e.deleteContents();
            run(e, "unzip", zip.getAbsolutePath());
            e = e.listDirectories().get(0);

            assertEquals(0755, e.child("a.txt").mode());
            assertEquals(dirMode, e.child("subdir").mode());
            assertEquals(0644, e.child("subdir/b.txt").mode());
        } finally {
            tar.delete();
            zip.delete();
            dir.deleteRecursive();
        }
    }

    private static void run(FilePath dir, String... cmds) throws InterruptedException {
        try {
            assertEquals(0, new LocalLauncher(StreamTaskListener.fromStdout()).launch().cmds(cmds).pwd(dir).join());
        } catch (IOException x) { // perhaps restrict to x.message.contains("Cannot run program")? or "error=2, No such file or directory"?
            assumeTrue(false, "failed to run " + Arrays.toString(cmds) + ": " + x);
        }
    }

    @Issue("JENKINS-14922")
    @Test
    void brokenSymlinks() throws Exception {
        assumeFalse(Functions.isWindows());
        File dir = tmp;
        Util.createSymlink(dir, "nonexistent", "link", TaskListener.NULL);
        try (OutputStream out = OutputStream.nullOutputStream()) {
            new FilePath(dir).tar(out, "**");
        }
    }

    @Disabled("TODO fails to add empty directories to archive")
    @Issue("JENKINS-73837")
    @Test
    void emptyDirectory() throws Exception {
        Path tar = File.createTempFile("test.tar", null, tmp).toPath();
        Path root = newFolder(tmp, "junit").toPath();
        Files.createDirectory(root.resolve("foo"));
        Files.createDirectory(root.resolve("bar"));
        Files.writeString(root.resolve("bar/file.txt"), "foobar", StandardCharsets.UTF_8);
        try (OutputStream out = Files.newOutputStream(tar)) {
            new FilePath(root.toFile()).tar(out, "**");
        }
        Set<String> names = new HashSet<>();
        try (InputStream is = Files.newInputStream(tar);
                TarInputStream tis = new TarInputStream(is, StandardCharsets.UTF_8.name())) {
            TarEntry te;
            while ((te = tis.getNextEntry()) != null) {
                names.add(te.getName());
            }
        }
        assertEquals(Set.of("foo/", "bar/", "bar/file.txt"), names);
    }

    /**
     * Test backing up an open file
     */

    @Issue("JENKINS-20187")
    @Test
    void growingFileTar() throws Exception {
        File file = new File(tmp, "growing.file");
        GrowingFileRunnable runnable1 = new GrowingFileRunnable(file);
        Thread t1 = new Thread(runnable1);
        t1.start();

        try (OutputStream out = OutputStream.nullOutputStream()) {
            new FilePath(tmp).tar(out, "**");
        }

        runnable1.doFinish();
        t1.join();
    }

    private static class GrowingFileRunnable implements Runnable {
        private boolean finish = false;
        private Exception ex = null;
        private File file;

        GrowingFileRunnable(File file) {
            this.file = file;
        }

        @Override
        public void run() {
            File openFile = file;
            try {
                openFile.createNewFile();
                try (OutputStream fos = Files.newOutputStream(openFile.toPath())) {
                    for (int i = 0; !finish && i < 5000000; i++) { // limit the max size, just in case.
                        fos.write(0);
                        // Thread.sleep(5);
                    }
                }
            } catch (Exception e) {
                ex = e;
            }
        }

        public void doFinish() throws Exception {
            finish = true;
            if (ex != null) {
                throw ex;
            }
        }
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
