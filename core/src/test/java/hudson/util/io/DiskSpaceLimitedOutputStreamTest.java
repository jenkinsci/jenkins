package hudson.util.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for DiskSpaceLimitedOutputStream.
 */
class DiskSpaceLimitedOutputStreamTest {

    static class FakeFile extends File {
        private final long usable;

        FakeFile(String path, long usable) {
            super(path);
            this.usable = usable;
        }

        @Override
        public long getUsableSpace() {
            return usable;
        }
    }

    // Large buffer to trigger the immediate check path (>= typical 1MB check interval).
    private static final int LARGE_WRITE = 2 * 1024 * 1024;

    @Test
    void abortsWhenNotEnoughSpace() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Simulate only 1 MiB usable on target FS
        File fake = new FakeFile("dummy", 1L * 1024 * 1024);
        // threshold 2 MiB -> a LARGE_WRITE should trigger immediate check and abort
        DiskSpaceLimitedOutputStream d = new DiskSpaceLimitedOutputStream(baos, fake, 2L * 1024 * 1024);

        assertThrows(IOException.class, () -> d.write(new byte[LARGE_WRITE]));
    }

    @Test
    void allowsWhenEnoughSpace() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Simulate 4 MiB usable
        File fake = new FakeFile("dummy", 4L * 1024 * 1024);
        // threshold 1 KiB -> LARGE_WRITE should succeed
        DiskSpaceLimitedOutputStream d = new DiskSpaceLimitedOutputStream(baos, fake, 1024L);
        d.write(new byte[LARGE_WRITE]);
        assertEquals(LARGE_WRITE, baos.size());
    }

    @Test
    void forControllerReturnsWrapper() throws Exception {
        String prop = "jenkins.controller.diskspace.threshold";
        System.setProperty(prop, "2MB");
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DiskSpaceLimitedOutputStream wrapper = DiskSpaceLimitedOutputStream.forController(baos);
            assertNotNull(wrapper);
            // Use a small write that SHOULD succeed on any normal disk
            wrapper.write(new byte[1024]);
            assertEquals(1024, baos.size());
        } finally {
            System.clearProperty(prop);
        }
    }
}