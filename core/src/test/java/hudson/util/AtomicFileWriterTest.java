package hudson.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class AtomicFileWriterTest {
    File af;
    AtomicFileWriter afw;
    String expectedContent = "hello world";


    @Before
    public void setUp() throws IOException {
        af = File.createTempFile("AtomicFileWriter", ".tmp");
        afw = new AtomicFileWriter(af, Charset.defaultCharset());
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(af.toPath());
        Files.deleteIfExists(afw.getTemporaryPath());
    }

    @Test
    public void createFile() throws Exception {
        // Verify the file we created exists
        assertTrue(Files.exists(afw.getTemporaryPath()));
    }

    @Test
    public void writeToAtomicFile() throws Exception {
        // Given
        afw.write(expectedContent, 0, expectedContent.length());

        // When
        afw.flush();

        // Then
        assertTrue("File writer did not properly flush to temporary file",
                Files.size(afw.getTemporaryPath()) == expectedContent.length());
    }

    @Test
    public void commitToFile() throws Exception {
        // Given
        afw.write(expectedContent, 0, expectedContent.length());

        // When
        afw.commit();

        // Then
        assertTrue(Files.size(af.toPath()) == expectedContent.length());
    }

    @Test
    public void abortDeletesTmpFile() throws Exception {
        // Given
        afw.write(expectedContent, 0, expectedContent.length());

        // When
        afw.abort();

        // Then
        assertTrue(Files.notExists(afw.getTemporaryPath()));
    }
}