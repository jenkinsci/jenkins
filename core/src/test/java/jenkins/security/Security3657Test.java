package jenkins.security;

import static jenkins.security.Security3657Test.Entry.fileOrDir;
import static jenkins.security.Security3657Test.Entry.symlink;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import hudson.FilePath;
import hudson.Util;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.tools.tar.TarConstants;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;

public class Security3657Test {

    @BeforeEach
    public void setup() throws IllegalAccessException, NoSuchFieldException {
        // Since we're not running "in" a JenkinsJVM for core tests, need to force the flags to behave safely
        for (String fieldName : List.of("ALLOW_UNTAR_SYMLINK_RESOLUTION", "ALLOW_REENTRY_PATH_TRAVERSAL")) {
            final Field escapeHatch = FilePath.class.getDeclaredField(fieldName);
            escapeHatch.setAccessible(true);
            escapeHatch.set(null, Boolean.FALSE);
        }
    }

    @Test
    void tarSymlinkPathTraversal(@TempDir File root) throws Exception {
        assumeFalse("true".equals(System.getenv("DISABLE_SYMLINK_TESTS")));
        final FilePath tarfile = new FilePath(createTarFile(root, symlink("attacker", ".."), fileOrDir("attacker/pwned.txt")));

        FilePath extractDir = new FilePath(new File(root, "extract"));
        extractDir.mkdirs();

        IOException exception = assertThrows(IOException.class, () -> tarfile.untar(extractDir, FilePath.TarCompression.NONE));

        // Symlink was created but file outside extraction dir was not
        assertTrue(extractDir.child("attacker").exists());
        assertFalse(new File(root, "pwned.txt").exists());

        // Verify the error message mentions symlink in path
        String message = exception.getMessage();
        if (exception.getCause() != null) {
            message = exception.getCause().getMessage();
        }
        assertThat(message, containsString("symlink in path"));
    }

    @Test
    void tarSymlinkPathTraversalEscapeHatch(@TempDir File root) throws Exception {
        assumeFalse("true".equals(System.getenv("DISABLE_SYMLINK_TESTS")));
        final Field escapeHatch = FilePath.class.getDeclaredField("ALLOW_UNTAR_SYMLINK_RESOLUTION");
        escapeHatch.setAccessible(true);
        escapeHatch.set(null, Boolean.TRUE);
        try {
            final FilePath tarfile = new FilePath(createTarFile(root, symlink("attacker", ".."), fileOrDir("attacker/pwned.txt")));

            FilePath extractDir = new FilePath(new File(root, "extract"));
            extractDir.mkdirs();

            tarfile.untar(extractDir, FilePath.TarCompression.NONE);

            // Symlink and file outside extraction dir were created
            assertTrue(extractDir.child("attacker").exists());
            assertTrue(new File(root, "pwned.txt").exists());
        } finally {
            escapeHatch.set(null, null);
        }
    }

    @Test
    void recursiveLinks(@TempDir File root) throws Exception {
        assumeFalse("true".equals(System.getenv("DISABLE_SYMLINK_TESTS")));
        final File extractDir = new File(root, "extract-base");
        assertTrue(extractDir.mkdirs());
        final FilePath symlinkTarFile = new FilePath(createTarFile(root, symlink("link-file", "other-link"), symlink("other-link", "link-file")));
        final FilePath extractFilePath = new FilePath(root).child("extract-base");
        extractFilePath.mkdirs();
        symlinkTarFile.untar(extractFilePath, FilePath.TarCompression.NONE);
        assertTrue(Util.isSymlink(extractDir.toPath().resolve("link-file")));
        assertTrue(Util.isSymlink(extractDir.toPath().resolve("other-link")));
    }

    @Test
    void selfLink(@TempDir File root) throws Exception {
        assumeFalse("true".equals(System.getenv("DISABLE_SYMLINK_TESTS")));
        final File extractDir = new File(root, "extract-base");
        assertTrue(extractDir.mkdirs());
        final FilePath symlinkTarFile = new FilePath(createTarFile(root, symlink("link-file", "link-file")));
        final FilePath extractFilePath = new FilePath(root).child("extract-base");
        extractFilePath.mkdirs();
        symlinkTarFile.untar(extractFilePath, FilePath.TarCompression.NONE);
        assertTrue(Util.isSymlink(extractDir.toPath().resolve("link-file")));
    }

    @Test
    void selfLink2(@TempDir File root) throws Exception {
        assumeFalse("true".equals(System.getenv("DISABLE_SYMLINK_TESTS")));
        final File extractDir = new File(root, "extract-base");
        assertTrue(extractDir.mkdirs());
        final FilePath symlinkTarFile = new FilePath(createTarFile(root, symlink("link-file", "link-file"), symlink("link-file", "link-file")));
        final FilePath extractFilePath = new FilePath(root).child("extract-base");
        extractFilePath.mkdirs();
        symlinkTarFile.untar(extractFilePath, FilePath.TarCompression.NONE);
        assertTrue(Util.isSymlink(extractDir.toPath().resolve("link-file")));
    }

    @Test
    void allowNonExistentSymlinkTargets(@TempDir File root) throws Exception {
        assumeFalse("true".equals(System.getenv("DISABLE_SYMLINK_TESTS")));
        final File extractDir = new File(root, "extract-base");
        assertTrue(extractDir.mkdirs());
        final FilePath symlinkTarFile = new FilePath(createTarFile(root, symlink("link-file", "src/main/whatever/non-existent-file"), fileOrDir("real-file")));
        final FilePath extractFilePath = new FilePath(root).child("extract-base");
        extractFilePath.mkdirs();
        symlinkTarFile.untar(extractFilePath, FilePath.TarCompression.NONE);
        assertTrue(Util.isSymlink(extractDir.toPath().resolve("link-file")));
        assertTrue(Files.isRegularFile(extractDir.toPath().resolve("real-file")));
    }

    @Issue("JENKINS-67063")
    @Test
    void repeatedExtraction(@TempDir File root) throws Exception {
        assumeFalse("true".equals(System.getenv("DISABLE_SYMLINK_TESTS")));
        final File extractDir = new File(root, "extract-base");
        assertTrue(extractDir.mkdirs());
        final FilePath symlinkTarFile = new FilePath(createTarFile(root, symlink("link-file", "src/main/whatever/some-file"), fileOrDir("src/main/whatever/some-file")));
        final FilePath extractFilePath = new FilePath(root).child("extract-base");
        extractFilePath.mkdirs();
        symlinkTarFile.untar(extractFilePath, FilePath.TarCompression.NONE);
        symlinkTarFile.untar(extractFilePath, FilePath.TarCompression.NONE);
        assertTrue(Util.isSymlink(extractDir.toPath().resolve("link-file")));
        assertTrue(Files.isRegularFile(extractDir.toPath().resolve("src/main/whatever/some-file")));
    }

    @Issue("JENKINS-67063")
    @Test
    void differentExtraction(@TempDir File root) throws Exception {
        assumeFalse("true".equals(System.getenv("DISABLE_SYMLINK_TESTS")));
        final File extractDir = new File(root, "extract-base");
        assertTrue(extractDir.mkdirs());
        final FilePath extractFilePath = new FilePath(root).child("extract-base");
        extractFilePath.mkdirs();

        final FilePath symlinkTarFile = new FilePath(createTarFile(root, symlink("link-file", "src/main/whatever/some-file"), fileOrDir("src/main/whatever/some-file"), fileOrDir("regular-file")));
        symlinkTarFile.untar(extractFilePath, FilePath.TarCompression.NONE);
        assertTrue(Util.isSymlink(extractDir.toPath().resolve("link-file")));
        assertTrue(Files.isRegularFile(extractDir.toPath().resolve("src/main/whatever/some-file")));
        assertTrue(Files.isRegularFile(extractDir.toPath().resolve("regular-file")));

        final FilePath otherTarFile = new FilePath(createTarFile(root, "other.tar", symlink("link-file", "src/main/whatever/some-file")));
        otherTarFile.untar(extractFilePath, FilePath.TarCompression.NONE);
        assertTrue(Util.isSymlink(extractDir.toPath().resolve("link-file")));
        assertTrue(Files.isRegularFile(extractDir.toPath().resolve("src/main/whatever/some-file")));
        assertTrue(Files.isRegularFile(extractDir.toPath().resolve("regular-file")));
    }

    @Test
    void directWriteThroughRelative(@TempDir File root) throws Exception {
        assumeFalse("true".equals(System.getenv("DISABLE_SYMLINK_TESTS")));
        // We can only have a link name up to 100 bytes, which won't be enough for local/CI build workspaces, so improvise: relative PT, direct, to existing parent
        assertTrue(new File(root, "plugins").mkdirs());
        final FilePath pathTraversalTarFile = new FilePath(createTarFile(root, symlink("link-file", "../plugins/evil.hpi"), fileOrDir("link-file")));
        final FilePath extractDir = new FilePath(root).child("extract-base");
        extractDir.mkdirs();
        final IOException expected = assertThrows(IOException.class, () -> pathTraversalTarFile.untar(extractDir, FilePath.TarCompression.NONE));
        assertThat(expected.getMessage(), containsString("Failed to extract crafted.tar"));
        assertThat(expected.getCause().getMessage(), containsString("Tar 'crafted.tar' entry 'link-file' would write through existing symlink:"));

        assertFalse(new File(root, "plugins/evil.hpi").exists());
    }

    @Test
    void directWriteThroughAbsolute(@TempDir File root) throws Exception {
        assumeFalse("true".equals(System.getenv("DISABLE_SYMLINK_TESTS")));
        // We can only have a link name up to 100 bytes, which may not be enough for local/CI build workspaces, so improvise with system temp dir.
        // macOS 26.3 has temp dirs that look like /var/folders/sx/123456789012345678901234567890/T/ (49 chars), which is enough for this test.
        // It seems running this test in IntelliJ IDEA fails since that uses a different temp dir, but it works with command line `mvn`.
        final Path dir = Files.createTempDirectory("jenkins-test");
        try {
            final Path linkTargetPath = dir.resolve("evil.hpi");
            final FilePath pathTraversalTarFile = new FilePath(createTarFile(root, symlink("link-file", linkTargetPath.toString()), fileOrDir("link-file")));
            final FilePath extractDir = new FilePath(root).child("extract-base");
            extractDir.mkdirs();
            final IOException expected = assertThrows(IOException.class, () -> pathTraversalTarFile.untar(extractDir, FilePath.TarCompression.NONE));
            assertThat(expected.getMessage(), containsString("Failed to extract crafted.tar"));
            assertThat(expected.getCause().getMessage(), containsString("Tar 'crafted.tar' entry 'link-file' would write through existing symlink:"));

            assertFalse(Files.exists(linkTargetPath));
        } finally {
            try {
                Files.deleteIfExists(dir);
            } catch (IOException ignored) {
            }
        }
    }

    @Test
    void directoryCreationPathTraversal(@TempDir File root) throws Exception {
        assumeFalse("true".equals(System.getenv("DISABLE_SYMLINK_TESTS")));
        final File extractDir = new File(root, "extract-base");
        assertTrue(extractDir.mkdirs());
        final FilePath symlinkTarFile = new FilePath(createTarFile(root, symlink("link-file", ".."), fileOrDir("link-file/bar/")));
        final FilePath extractFilePath = new FilePath(root).child("extract-base");
        extractFilePath.mkdirs();
        final IOException ioException = assertThrows(IOException.class, () -> symlinkTarFile.untar(extractFilePath, FilePath.TarCompression.NONE));
        assertThat(ioException.getMessage(), containsString("Failed to extract crafted.tar"));
        assertThat(ioException.getCause().getMessage(), containsString("Tar crafted.tar attempts to write to file with symlink in path: link-file/bar/"));
        assertFalse(Files.isDirectory(root.toPath().resolve("bar")));
    }

    @Test
    void relativePathLegal(@TempDir Path root) throws Exception {
        // relative path from cwd to temp dir to ensure behavior is as expected with relative base dir
        final File relativeRoot = Path.of("").toAbsolutePath().relativize(root).toFile();

        final File extractDir = new File(relativeRoot, "extract-base");
        assertTrue(extractDir.mkdirs());
        final FilePath symlinkTarFile = new FilePath(createTarFile(relativeRoot, fileOrDir("dir/file")));
        final FilePath extractFilePath = new FilePath(relativeRoot).child("extract-base");
        extractFilePath.mkdirs();
        symlinkTarFile.untar(extractFilePath, FilePath.TarCompression.NONE);
        assertTrue(Files.isDirectory(extractDir.toPath().resolve("dir")));
        assertTrue(Files.isRegularFile(extractDir.toPath().resolve("dir/file")));
    }

    @Test
    void relativeBaseAllowedSymlinks(@TempDir Path root) throws Exception {
        assumeFalse("true".equals(System.getenv("DISABLE_SYMLINK_TESTS")));
        // relative path from cwd to temp dir to ensure behavior is as expected with relative base dir
        final File relativeRoot = Path.of("").toAbsolutePath().relativize(root).toFile();

        final File extractDir = new File(relativeRoot, "extract-base");
        assertTrue(extractDir.mkdirs());
        final FilePath symlinkTarFile = new FilePath(createTarFile(relativeRoot, symlink("path/to/link-file", "../../file.txt"), symlink("path/to/other-file", "../../path/to/link-file")));
        final FilePath extractFilePath = new FilePath(relativeRoot).child("extract-base");
        extractFilePath.mkdirs();
        symlinkTarFile.untar(extractFilePath, FilePath.TarCompression.NONE);
        assertTrue(Util.isSymlink(extractDir.toPath().resolve("path/to/link-file")));
        assertTrue(Util.isSymlink(extractDir.toPath().resolve("path/to/other-file")));
    }

    @Test
    void relativeBaseDirectWriteThroughRelative(@TempDir Path root) throws Exception {
        assumeFalse("true".equals(System.getenv("DISABLE_SYMLINK_TESTS")));
        // relative path from cwd to temp dir to ensure behavior is as expected with relative base dir
        final File relativeRoot = Path.of("").toAbsolutePath().relativize(root).toFile();

        // We can only have a link name up to 100 bytes, which won't be enough for local/CI build workspaces, so improvise: relative PT, direct, to existing parent
        assertTrue(new File(relativeRoot, "plugins").mkdirs());
        final FilePath pathTraversalTarFile = new FilePath(createTarFile(relativeRoot, symlink("link-file", "../plugins/evil.hpi"), fileOrDir("link-file")));
        final FilePath extractDir = new FilePath(relativeRoot).child("extract-base");
        extractDir.mkdirs();
        final IOException expected = assertThrows(IOException.class, () -> pathTraversalTarFile.untar(extractDir, FilePath.TarCompression.NONE));
        assertThat(expected.getMessage(), containsString("Failed to extract crafted.tar"));
        assertThat(expected.getCause().getMessage(), containsString("Tar 'crafted.tar' entry 'link-file' would write through existing symlink:"));

        assertFalse(new File(relativeRoot, "plugins/evil.hpi").exists());
    }

    @Test
    void relativeBaseBasicRelativePathTraversal(@TempDir Path root) throws Exception {
        // relative path from cwd to temp dir to ensure behavior is as expected with relative base dir
        final File relativeRoot = Path.of("").toAbsolutePath().relativize(root).toFile();

        final File extractDir = new File(relativeRoot, "extract-base");
        assertTrue(extractDir.mkdirs());
        final FilePath symlinkTarFile = new FilePath(createTarFile(relativeRoot, fileOrDir("../file.txt")));
        final FilePath extractFilePath = new FilePath(relativeRoot).child("extract-base");
        extractFilePath.mkdirs();
        final IOException ioException = assertThrows(IOException.class, () -> symlinkTarFile.untar(extractFilePath, FilePath.TarCompression.NONE));
        assertThat(ioException.getMessage(), containsString("Failed to extract crafted.tar"));
        assertFalse(Files.exists(extractDir.toPath().resolve("file.txt")));
    }

    @Test
    void relativePathTraversalThroughSymlink(@TempDir Path root) throws Exception {
        assumeFalse("true".equals(System.getenv("DISABLE_SYMLINK_TESTS")));
        // relative path from cwd to temp dir to ensure behavior is as expected with relative base dir
        final File relativeRoot = Path.of("").toAbsolutePath().relativize(root).toFile();

        final File extractDir = new File(relativeRoot, "extract-base");
        assertTrue(extractDir.mkdirs());
        final FilePath symlinkTarFile = new FilePath(createTarFile(relativeRoot, symlink("link-file", ".."), fileOrDir("link-file/bar/")));
        final FilePath extractFilePath = new FilePath(relativeRoot).child("extract-base");
        extractFilePath.mkdirs();
        final IOException ioException = assertThrows(IOException.class, () -> symlinkTarFile.untar(extractFilePath, FilePath.TarCompression.NONE));
        assertThat(ioException.getMessage(), containsString("Failed to extract crafted.tar"));
        assertThat(ioException.getCause().getMessage(), containsString("Tar crafted.tar attempts to write to file with symlink in path: link-file/bar/"));
        assertFalse(Files.isDirectory(relativeRoot.toPath().resolve("bar")));
    }

    @Test
    void directoryCreationDirect(@TempDir File root) throws Exception {
        assumeFalse("true".equals(System.getenv("DISABLE_SYMLINK_TESTS")));
        final File extractDir = new File(root, "extract-base");
        assertTrue(extractDir.mkdirs());
        final FilePath symlinkTarFile = new FilePath(createTarFile(root, symlink("link-file", "../bar"), fileOrDir("link-file/")));
        final FilePath extractFilePath = new FilePath(root).child("extract-base");
        extractFilePath.mkdirs();
        final IOException ioException = assertThrows(IOException.class, () -> symlinkTarFile.untar(extractFilePath, FilePath.TarCompression.NONE));
        assertThat(ioException.getMessage(), containsString("Failed to extract crafted.tar"));
        assertFalse(Files.exists(root.toPath().resolve("bar")));
    }

    @Test
    void basicAbsolutePathTraversal(@TempDir File root) throws Exception {
        // This test is weird since we cannot really assert anything based on java.io.File Javadoc and actual observed behavior. So just assert that the bad file doesn't get created.
        // We can only have a link name up to 100 bytes, which may not be enough for local/CI build workspaces, so improvise with system temp dir.
        // macOS 26.3 has temp dirs that look like /var/folders/sx/123456789012345678901234567890/T/ (49 chars), which is enough for this test.
        // It seems running this test in IntelliJ IDEA fails since that uses a different temp dir, but it works with command line `mvn`.
        final Path dir = Files.createTempDirectory("jenkins-test");
        try {
            final File extractDir = new File(root, "extract-base");
            assertTrue(extractDir.mkdirs());
            final FilePath symlinkTarFile = new FilePath(createTarFile(root, fileOrDir(dir.resolve("file.txt").toString())));
            final FilePath extractFilePath = new FilePath(root).child("extract-base");
            extractFilePath.mkdirs();
            try {
                symlinkTarFile.untar(extractFilePath, FilePath.TarCompression.NONE);
            } catch (Exception ignore) {
            }
            assertFalse(Files.exists(dir.resolve("file.txt")));
        } finally {
            try {
                Files.deleteIfExists(dir);
            } catch (IOException ignored) {
            }
        }
    }

    @Test
    void basicRelativePathTraversal(@TempDir File root) throws Exception {
        final File extractDir = new File(root, "extract-base");
        assertTrue(extractDir.mkdirs());
        final FilePath symlinkTarFile = new FilePath(createTarFile(root, fileOrDir("../file.txt")));
        final FilePath extractFilePath = new FilePath(root).child("extract-base");
        extractFilePath.mkdirs();
        final IOException ioException = assertThrows(IOException.class, () -> symlinkTarFile.untar(extractFilePath, FilePath.TarCompression.NONE));
        assertThat(ioException.getMessage(), containsString("Failed to extract crafted.tar"));
        assertFalse(Files.exists(extractDir.toPath().resolve("file.txt")));
    }

    @Test
    void allowedSymlinks(@TempDir File root) throws Exception {
        assumeFalse("true".equals(System.getenv("DISABLE_SYMLINK_TESTS")));
        final File extractDir = new File(root, "extract-base");
        assertTrue(extractDir.mkdirs());
        final FilePath symlinkTarFile = new FilePath(createTarFile(root, symlink("path/to/link-file", "../../file.txt"), symlink("path/to/other-file", "../../path/to/link-file")));
        final FilePath extractFilePath = new FilePath(root).child("extract-base");
        extractFilePath.mkdirs();
        symlinkTarFile.untar(extractFilePath, FilePath.TarCompression.NONE);
        assertTrue(Util.isSymlink(extractDir.toPath().resolve("path/to/link-file")));
        assertTrue(Util.isSymlink(extractDir.toPath().resolve("path/to/other-file")));
    }

    @Test
    void allowedPathTraversal(@TempDir File root) throws Exception {
        final File extractDir = new File(root, "extract-base");
        assertTrue(extractDir.mkdirs());
        final FilePath symlinkTarFile = new FilePath(createTarFile(root, fileOrDir("path/"), fileOrDir("path/../file")));
        final FilePath extractFilePath = new FilePath(root).child("extract-base");
        extractFilePath.mkdirs();
        symlinkTarFile.untar(extractFilePath, FilePath.TarCompression.NONE);
        assertTrue(Files.isRegularFile(extractDir.toPath().resolve("file")));
    }

    @Test
    void escapeThenReEnterPathTraversal(@TempDir File root) throws Exception {
        final File extractDir = new File(root, "extract-base");
        assertTrue(extractDir.mkdirs());
        final FilePath symlinkTarFile = new FilePath(createTarFile(root, fileOrDir("../extract-base/foo")));
        final FilePath extractFilePath = new FilePath(root).child("extract-base");
        extractFilePath.mkdirs();
        final IOException ioException = assertThrows(IOException.class, () -> symlinkTarFile.untar(extractFilePath, FilePath.TarCompression.NONE));
        assertThat(ioException.getMessage(), containsString("Failed to extract crafted.tar"));
        assertThat(ioException.getCause().getMessage(), containsString("Tar crafted.tar contains entry that escapes destination directory: ../extract-base/foo"));

        assertFalse(Files.exists(extractDir.toPath().resolve("foo")));
    }

    @Test
    void escapeThenReEnterPathTraversalAllowed(@TempDir File root) throws Exception {
        final File extractDir = new File(root, "extract-base");
        assertTrue(extractDir.mkdirs());
        final FilePath symlinkTarFile = new FilePath(createTarFile(root, fileOrDir("../extract-base/foo")));
        final FilePath extractFilePath = new FilePath(root).child("extract-base");
        extractFilePath.mkdirs();
        final Field escapeHatch = FilePath.class.getDeclaredField("ALLOW_REENTRY_PATH_TRAVERSAL");
        escapeHatch.setAccessible(true);
        escapeHatch.set(null, Boolean.TRUE);
        try {
            symlinkTarFile.untar(extractFilePath, FilePath.TarCompression.NONE);
            assertTrue(Files.exists(extractDir.toPath().resolve("foo")));
        } finally {
            escapeHatch.set(null, null);
        }
    }

    @Test
    void escapeThenReEnterPathTraversalAllowedRelativeBase(@TempDir Path root) throws Exception {
        // relative path from cwd to temp dir to ensure behavior is as expected with relative base dir
        final File relativeRoot = Path.of("").toAbsolutePath().relativize(root).toFile();

        final File extractDir = new File(relativeRoot, "extract-base");
        assertTrue(extractDir.mkdirs());
        final FilePath symlinkTarFile = new FilePath(createTarFile(relativeRoot, fileOrDir("../extract-base/foo")));
        final FilePath extractFilePath = new FilePath(relativeRoot).child("extract-base");
        extractFilePath.mkdirs();
        final Field escapeHatch = FilePath.class.getDeclaredField("ALLOW_REENTRY_PATH_TRAVERSAL");
        escapeHatch.setAccessible(true);
        escapeHatch.set(null, Boolean.TRUE);
        try {
            symlinkTarFile.untar(extractFilePath, FilePath.TarCompression.NONE);
            assertTrue(Files.exists(extractDir.toPath().resolve("foo")));
        } finally {
            escapeHatch.set(null, null);
        }
    }

    private static File createTarFile(File base, Entry... entries) throws IOException {
        return createTarFile(base, "crafted.tar", entries);
    }

    private static File createTarFile(File base, String fileName, Entry... entries) throws IOException {
        File tarFile = new File(base, fileName);

        try (TarOutputStream tar = new TarOutputStream(Files.newOutputStream(tarFile.toPath()))) {
            for (Entry entry : entries) {
                entry.add(tar);
            }
        }
        return tarFile;
    }

    interface Entry {
        void add(TarOutputStream tar) throws IOException;

        /**
         * @param name the name of the file or folder. For folder, add trailing /
         */
        static Entry fileOrDir(String name) {
            return new FileOrDirEntry(name);
        }

        static Entry symlink(String name, String target) {
            return new SymlinkEntry(name, target);
        }

    }

    record FileOrDirEntry(String name) implements Entry {
        @Override
        public void add(TarOutputStream tar) throws IOException {
            TarEntry fileEntry = new TarEntry(name, true);
            byte[] content = "You've been pwned!".getBytes(StandardCharsets.UTF_8);
            if (!fileEntry.isDirectory()) {
                fileEntry.setSize(content.length);
            }
            tar.putNextEntry(fileEntry);
            if (!fileEntry.isDirectory()) {
                tar.write(content);
            }
            tar.closeEntry();
        }
    }

    record SymlinkEntry(String name, String target) implements Entry {
        @Override
        public void add(TarOutputStream tar) throws IOException {
            TarEntry symlinkEntry = new TarEntry(name, true);
            symlinkEntry.setLinkFlag(TarConstants.LF_SYMLINK);
            symlinkEntry.setLinkName(target);
            tar.putNextEntry(symlinkEntry);
            tar.closeEntry();
        }
    }
}
