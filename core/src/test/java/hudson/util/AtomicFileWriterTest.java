package hudson.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeThat;

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
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;

public class AtomicFileWriterTest {
    private static final String PREVIOUS = "previous value \n blah";
    /**
     * Provides access to the default permissions given to a new file. (i.e. indirect way to get umask settings).
     * <p><strong>BEWARE: null on non posix systems</strong></p>
     */
    @Nullable
    private static Set<PosixFilePermission> DEFAULT_GIVEN_PERMISSIONS;
    @ClassRule
    public static TemporaryFolder tmp = new TemporaryFolder();
    File af;
    AtomicFileWriter afw;
    String expectedContent = "hello world";

    @BeforeClass
    public static void computePermissions() throws IOException {
        final File tempDir = tmp.newFolder();
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

    @Before
    public void setUp() throws IOException {
        af = tmp.newFile();
        Files.writeString(af.toPath(), PREVIOUS, Charset.defaultCharset());
        afw = new AtomicFileWriter(af.toPath(), Charset.defaultCharset());
    }

    @After
    public void tearDown() throws IOException {
        afw.abort();
    }

    @Test
    public void symlinkToDirectory() throws Exception {
        assumeFalse(Functions.isWindows());
        final File folder = tmp.newFolder();
        final File containingSymlink = tmp.newFolder();
        final Path zeSymlink = Files.createSymbolicLink(Paths.get(containingSymlink.getAbsolutePath(), "ze_symlink"),
                                                         folder.toPath());


        final Path childFileInSymlinkToDir = Paths.get(zeSymlink.toString(), "childFileInSymlinkToDir");

        new AtomicFileWriter(childFileInSymlinkToDir, StandardCharsets.UTF_8).abort();
    }

    @Test
    public void createFile() {
        // Verify the file we created exists
        assertTrue(Files.exists(afw.getTemporaryPath()));
    }

    @Test
    public void writeToAtomicFile() throws Exception {
        // Given
        afw.write(expectedContent, 0, expectedContent.length());
        afw.write(expectedContent);
        afw.write(' ');

        // When
        afw.flush();

        // Then
        assertEquals("File writer did not properly flush to temporary file",
                expectedContent.length() * 2 + 1, Files.size(afw.getTemporaryPath()));
    }

    @Test
    public void commitToFile() throws Exception {
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
    public void abortDeletesTmpFile() throws Exception {
        // Given
        afw.write(expectedContent, 0, expectedContent.length());

        // When
        afw.abort();

        // Then
        assertTrue(Files.notExists(afw.getTemporaryPath()));
        assertEquals(PREVIOUS, Files.readString(af.toPath(), Charset.defaultCharset()));
    }

    @Test
    public void indexOutOfBoundsLeavesOriginalUntouched() throws Exception {
        // Given
        assertThrows(IndexOutOfBoundsException.class, () -> afw.write(expectedContent, 0, expectedContent.length() + 10));
        assertEquals(PREVIOUS, Files.readString(af.toPath(), Charset.defaultCharset()));
    }

    @Test
    public void badPath() throws Exception {
        final File newFile = tmp.newFile();
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
    public void checkPermissionsRespectUmask() throws IOException {

        final File newFile = tmp.newFile();
        boolean posixSupported = isPosixSupported(newFile);

        assumeThat(posixSupported, is(true));

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
}
