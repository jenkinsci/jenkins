package hudson.util;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.MemoryAssert;

public class AtomicFileWriterTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Rule
    public LoggerRule logging =
            new LoggerRule().record(AtomicFileWriter.class, Level.WARNING).capture(100);

    @Test
    public void noResourceLeak() throws IOException {
        Path destPath = tmp.newFolder().toPath().resolve("file");
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
    public void resourceLeak() throws IOException {
        Path destPath = tmp.newFolder().toPath().resolve("file");
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
}
