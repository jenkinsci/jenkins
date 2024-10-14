package hudson.util.io;

import static org.junit.Assert.assertEquals;

import hudson.FilePath;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;

public class ZipArchiverTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Issue("JENKINS-9942")
    @Test
    public void backwardsSlashesOnWindows() throws IOException {
        // create foo/bar/baz/Test.txt
        Path baz = tmp.newFolder().toPath().resolve("foo").resolve("bar").resolve("baz");
        Files.createDirectories(baz);
        Path tmpFile = baz.resolve("Test.txt");
        Files.createFile(tmpFile);

        // a file to store the zip archive in
        Path zipFile = Files.createTempFile(tmp.getRoot().toPath(), "test", ".zip");

        // create zip from tmpDir
        try (ZipArchiver archiver = new ZipArchiver(Files.newOutputStream(zipFile))) {
            archiver.visit(tmpFile.toFile(), "foo\\bar\\baz\\Test.txt");
        }

        // examine zip contents and assert that none of the entry names (paths) have
        // back-slashes ("\")
        try (ZipFile zipFileVerify = new ZipFile(zipFile.toFile())) {
            assertEquals(1, zipFileVerify.size());
            ZipEntry zipEntry = zipFileVerify.entries().nextElement();
            assertEquals("foo/bar/baz/Test.txt", zipEntry.getName());
        }
    }

    @Test
    public void huge64bitFile() throws IOException {
        // create huge64bitFileTest.txt
        Path hugeFile = tmp.newFolder().toPath().resolve("huge64bitFileTest.txt");
        long length = 4L * 1024 * 1024 * 1024 + 2;
        try (RandomAccessFile largeFile = new RandomAccessFile(hugeFile.toFile(), "rw")) {
            largeFile.setLength(length);
        } catch (IOException e) {
            // We probably don't have enough free disk space. That's ok, we'll skip this test...
            Assume.assumeNoException(e);
        }

        // a file to store the zip archive in
        Path zipFile = Files.createTempFile(tmp.getRoot().toPath(), "test", ".zip");

        // create zip from tmpDir
        try (ZipArchiver archiver = new ZipArchiver(Files.newOutputStream(zipFile))) {
            archiver.visit(hugeFile.toFile(), "huge64bitFileTest.txt");
        }

        // examine zip contents and assert that there's an item there...
        try (ZipFile zipFileVerify = new ZipFile(zipFile.toFile())) {
            assertEquals(1, zipFileVerify.size());
            ZipEntry zipEntry = zipFileVerify.entries().nextElement();
            assertEquals("huge64bitFileTest.txt", zipEntry.getName());
            assertEquals(length, zipEntry.getSize());
        }
    }

    @Issue("JENKINS-49296")
    @Test
    public void emptyDirectory() throws Exception {
        Path zip = tmp.newFile("test.zip").toPath();
        Path root = tmp.newFolder().toPath();
        Files.createDirectory(root.resolve("a"));
        Files.createDirectory(root.resolve("b"));
        Files.writeString(root.resolve("a/file.txt"), "empty_dir_zip_test");
        try (OutputStream out = Files.newOutputStream(zip)) {
            new FilePath(root.toFile()).zip(out, "**");
        }
        Set<String> actual = new HashSet<>();
        Set<String> expected = Set.of("a/", "b/", "a/file.txt");
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                actual.add(zipEntry.getName());
            }
        }
        assertEquals(expected, actual);
    }
}
