package hudson.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Functions;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;

class AtomicFileWriterTest {
    private static final String PREVIOUS = "previous value \n blah";
    /**
     * Provides access to the default permissions given to a new file. (i.e. indirect way to get umask settings).
     * <p><strong>BEWARE: null on non posix systems</strong></p>
     */
    @Nullable
    private static Set<PosixFilePermission> DEFAULT_GIVEN_PERMISSIONS;
    @TempDir
    private static File tmp;
    private File af;
    private AtomicFileWriter afw;
    private String expectedContent = "hello world";

    @BeforeAll
    static void computePermissions() throws IOException {
        final File tempDir = newFolder(tmp, "junit");
        final File newFile = new File(tempDir, "blah");
        assertThat(newFile.createNewFile(), is(true));
        if (!isPosixSupported(newFile)) {
            return;
        }
        DEFAULT_GIVEN_PERMISSIONS = Files.getPosixFilePermissions(newFile.toPath());
    }

    private static boolean isPosixSupported(File newFile) throws IOException {
        // Check Posix calls are supported (to avoid running this test on Windows for instance)
        boolean posixSupported = true;
        try {
            Files.getPosixFilePermissions(newFile.toPath());
        } catch (UnsupportedOperationException e) {
            posixSupported = false;
        }
        return posixSupported;
    }

    @BeforeEach
    void setUp() throws IOException {
        af = File.createTempFile("junit", null, tmp);
        Files.writeString(af.toPath(), PREVIOUS, Charset.defaultCharset());
        afw = new AtomicFileWriter(af.toPath(), Charset.defaultCharset());
    }

    @AfterEach
    void tearDown() throws IOException {
        afw.abort();
    }

    @Test
    void symlinkToDirectory() throws Exception {
        assumeFalse(Functions.isWindows());
        final File folder = newFolder(tmp, "junit");
        final File containingSymlink = newFolder(tmp, "junit");
        final Path zeSymlink = Files.createSymbolicLink(Paths.get(containingSymlink.getAbsolutePath(), "ze_symlink"),
                                                         folder.toPath());


        final Path childFileInSymlinkToDir = Paths.get(zeSymlink.toString(), "childFileInSymlinkToDir");

        new AtomicFileWriter(childFileInSymlinkToDir, StandardCharsets.UTF_8).abort();
    }

    @Test
    void createFile() {
        // Verify the file we created exists
        assertTrue(Files.exists(afw.getTemporaryPath()));
    }

    @Test
    void writeToAtomicFile() throws Exception {
        // Given
        afw.write(expectedContent, 0, expectedContent.length());
        afw.write(expectedContent);
        afw.write(' ');

        // When
        afw.flush();

        // Then
        assertEquals(expectedContent.length() * 2 + 1, Files.size(afw.getTemporaryPath()), "File writer did not properly flush to temporary file");
    }

    @Test
    void commitToFile() throws Exception {
        // Given
        afw.write(expectedContent, 0, expectedContent.length());
        afw.write(new char[]{'h', 'e', 'y'}, 0, 3);

        // When
        afw.commit();

        // Then
        assertEquals(expectedContent.length() + 3, Files.size(af.toPath()));
        assertEquals(expectedContent + "hey", Files.readString(af.toPath(), Charset.defaultCharset()));
    }

    @Test
    void abortDeletesTmpFile() throws Exception {
        // Given
        afw.write(expectedContent, 0, expectedContent.length());

        // When
        afw.abort();

        // Then
        assertTrue(Files.notExists(afw.getTemporaryPath()));
        assertEquals(PREVIOUS, Files.readString(af.toPath(), Charset.defaultCharset()));
    }

    @Test
    void indexOutOfBoundsLeavesOriginalUntouched() throws Exception {
        // Given
        assertThrows(IndexOutOfBoundsException.class, () -> afw.write(expectedContent, 0, expectedContent.length() + 10));
        assertEquals(PREVIOUS, Files.readString(af.toPath(), Charset.defaultCharset()));
    }

    @Test
    void badPath() throws Exception {
        final File newFile = File.createTempFile("junit", null, tmp);
        File parentExistsAndIsAFile = new File(newFile, "badChild");

        assertTrue(newFile.exists());
        assertFalse(parentExistsAndIsAFile.exists());

        final IOException e = assertThrows(IOException.class,
                () -> new AtomicFileWriter(parentExistsAndIsAFile.toPath(), StandardCharsets.UTF_8));
        assertThat(e.getMessage(),
                   containsString("exists and is neither a directory nor a symlink to a directory"));
    }

    @Issue("JENKINS-48407")
    @Test
    void checkPermissionsRespectUmask() throws IOException {

        final File newFile = File.createTempFile("junit", null, tmp);
        boolean posixSupported = isPosixSupported(newFile);

        assumeTrue(posixSupported);

        // given
        Path filePath = newFile.toPath();

        // when
        AtomicFileWriter w = new AtomicFileWriter(filePath, StandardCharsets.UTF_8);
        w.write("whatever");
        w.commit();

        // then
        assertFalse(w.getTemporaryPath().toFile().exists());
        assertTrue(filePath.toFile().exists());

        assertThat(Files.getPosixFilePermissions(filePath), equalTo(DEFAULT_GIVEN_PERMISSIONS));
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
