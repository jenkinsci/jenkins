package hudson.util.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import hudson.FilePath;
import hudson.Functions;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;

class ZipArchiverTest {

    @TempDir
    private File tmp;

    @Issue("JENKINS-9942")
    @Test
    void backwardsSlashesOnWindows() throws IOException {
        // create foo/bar/baz/Test.txt
        Path baz = newFolder(tmp, "junit").toPath().resolve("foo").resolve("bar").resolve("baz");
        Files.createDirectories(baz);
        Path tmpFile = baz.resolve("Test.txt");
        Files.createFile(tmpFile);

        // a file to store the zip archive in
        Path zipFile = Files.createTempFile(tmp.toPath(), "test", ".zip");

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
    void huge64bitFile() throws IOException {
        // create huge64bitFileTest.txt
        Path hugeFile = newFolder(tmp, "junit").toPath().resolve("huge64bitFileTest.txt");
        long length = 4L * 1024 * 1024 * 1024 + 2;
        try (RandomAccessFile largeFile = new RandomAccessFile(hugeFile.toFile(), "rw")) {
            largeFile.setLength(length);
        } catch (IOException e) {
            // We probably don't have enough free disk space. That's ok, we'll skip this test...
            assumeTrue(false, e.toString());
        }

        // a file to store the zip archive in
        Path zipFile = Files.createTempFile(tmp.toPath(), "test", ".zip");

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

    @Issue("https://github.com/jenkinsci/jenkins/issues/27188")
    @Test
    void unreadableFileDoesNotCreateEntry() throws Exception {
        assumeFalse(Functions.isWindows());

        File unreadable = new File(tmp, "unreadable.txt");
        Files.writeString(unreadable.toPath(), "contents", StandardCharsets.UTF_8);
        assumeTrue(unreadable.setReadable(false));
        assumeFalse(unreadable.canRead());

        Path zipFile = Files.createTempFile(tmp.toPath(), "test", ".zip");
        ZipArchiver archiver = new ZipArchiver(Files.newOutputStream(zipFile));
        AccessDeniedException failure;
        try {
            failure = assertThrows(AccessDeniedException.class, () -> archiver.visit(unreadable, unreadable.getName()));
        } finally {
            unreadable.setReadable(true);
            archiver.close();
        }

        assertEquals(0, failure.getSuppressed().length);
        try (ZipFile zipFileVerify = new ZipFile(zipFile.toFile())) {
            assertEquals(0, zipFileVerify.size());
        }
    }

    @Disabled("TODO fails to add empty directories to archive")
    @Issue("JENKINS-49296")
    @Test
    void emptyDirectory() throws Exception {
        Path zip = File.createTempFile("test.zip", null, tmp).toPath();
        Path root = newFolder(tmp, "junit").toPath();
        Files.createDirectory(root.resolve("foo"));
        Files.createDirectory(root.resolve("bar"));
        Files.writeString(root.resolve("bar/file.txt"), "foobar", StandardCharsets.UTF_8);
        try (OutputStream out = Files.newOutputStream(zip)) {
            new FilePath(root.toFile()).zip(out, "**");
        }
        Set<String> names = new HashSet<>();
        try (InputStream is = Files.newInputStream(zip);
             ZipInputStream zis = new ZipInputStream(is, StandardCharsets.UTF_8)) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                names.add(ze.getName());
            }
        }
        assertEquals(Set.of("foo/", "bar/", "bar/file.txt"), names);
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
