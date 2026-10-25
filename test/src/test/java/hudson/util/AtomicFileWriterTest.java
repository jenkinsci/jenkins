package hudson.util;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.MemoryAssert;

class AtomicFileWriterTest {

    @TempDir(cleanup = CleanupMode.NEVER)
    private File tmp;

    private final LogRecorder logging =
            new LogRecorder().record(AtomicFileWriter.class, Level.WARNING).capture(100);

    @Test
    void noResourceLeak() throws IOException {
        Path destPath = newFolder(tmp, "junit").toPath().resolve("file");
        AtomicFileWriter writer = new AtomicFileWriter(destPath, StandardCharsets.UTF_8);
        Path tmpPath = writer.getTemporaryPath();
        assertTrue(Files.exists(tmpPath));
        assertFalse(Files.exists(destPath));
        try {
            writer.commit();
        } finally {
            writer.abort();
        }
        assertFalse(Files.exists(tmpPath));
        assertTrue(Files.exists(destPath));
        assertThat(logging.getMessages(), empty());
    }

    @Test
    void resourceLeak() throws IOException {
        Path destPath = newFolder(tmp, "junit").toPath().resolve("file");
        WeakReference<AtomicFileWriter> ref =
                new WeakReference<>(new AtomicFileWriter(destPath, StandardCharsets.UTF_8));
        Path tmpPath = ref.get().getTemporaryPath();
        assertTrue(Files.exists(tmpPath));
        assertFalse(Files.exists(destPath));
        MemoryAssert.assertGC(ref, false);
        await().atMost(30, TimeUnit.SECONDS).until(() -> !Files.exists(tmpPath));
        assertFalse(Files.exists(destPath));
        assertThat(
                logging.getMessages(),
                contains("AtomicFileWriter for " + destPath + " was not closed before being released"));
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
