package hudson.util.jna;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

class GNUCLibraryTest {

    private static final int O_CREAT = "Linux".equals(System.getProperty("os.name")) ? 64 : 512;
    private static final int O_RDWR = 2;

    private boolean isGlibcSupported() {
        return hudson.Functions.isGlibcSupported();
    }

    @Test
    @EnabledIf(value = "isGlibcSupported", disabledReason = "GNU libc not supported")
    void openTest() throws IOException {
        int fd = GNUCLibrary.LIBC.open("/dev/null", 0);
        assertNotEquals(-1, fd);

        int result = GNUCLibrary.LIBC.close(fd);
        assertEquals(0, result);

        Path tmpFile = Files.createTempFile("openTest", null);
        Files.delete(tmpFile);
        assertFalse(Files.exists(tmpFile));
        try {
            fd = GNUCLibrary.LIBC.open(tmpFile.toString(), O_CREAT | O_RDWR);
            assertTrue(Files.exists(tmpFile));

            result = GNUCLibrary.LIBC.close(fd);
            assertEquals(0, result);
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }

    @Test
    @EnabledIf(value = "isGlibcSupported", disabledReason = "GNU libc not supported")
    void closeTest() {
        int fd = GNUCLibrary.LIBC.open("/dev/null", 0);
        assertNotEquals(-1, fd);

        int result = GNUCLibrary.LIBC.close(fd);
        assertEquals(0, result);
    }

    @Test
    @EnabledIf(value = "isGlibcSupported", disabledReason = "GNU libc not supported")
    void fcntlTest() {
        int fd = GNUCLibrary.LIBC.open("/dev/null", 0);
        assertNotEquals(-1, fd);
        try {
            int flags = GNUCLibrary.LIBC.fcntl(fd, GNUCLibrary.F_GETFD);
            assertEquals(0, flags);
            int result =
                    GNUCLibrary.LIBC.fcntl(fd, GNUCLibrary.F_SETFD, flags | GNUCLibrary.FD_CLOEXEC);
            assertEquals(0, result);
            result = GNUCLibrary.LIBC.fcntl(fd, GNUCLibrary.F_GETFD);
            assertEquals(GNUCLibrary.FD_CLOEXEC, result);
        } finally {
            GNUCLibrary.LIBC.close(fd);
        }
    }

    @Test
    @EnabledIf(value = "isGlibcSupported", disabledReason = "GNU libc not supported")
    void renameTest() throws IOException {
        Path oldFile = Files.createTempFile("renameTest", null);
        Path newFile = Files.createTempFile("renameTest", null);
        Files.delete(newFile);
        assertTrue(Files.exists(oldFile));
        assertFalse(Files.exists(newFile));
        try {
            GNUCLibrary.LIBC.rename(oldFile.toString(), newFile.toString());

            assertFalse(Files.exists(oldFile));
            assertTrue(Files.exists(newFile));
        } finally {
            Files.deleteIfExists(oldFile);
            Files.deleteIfExists(newFile);
        }
    }
}
