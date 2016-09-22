package hudson.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class AtomicFileWriterTest {
    File af;
    AtomicFileWriter afw;
    String expectedContent = "hello world";
    @Rule public TemporaryFolder tmp = new TemporaryFolder();


    @Before
    public void setUp() throws IOException {
        af = tmp.newFile();
        afw = new AtomicFileWriter(af, Charset.defaultCharset());
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
        assertEquals("File writer did not properly flush to temporary file",
                expectedContent.length(), Files.size(afw.getTemporaryPath()));
    }

    @Test
    public void commitToFile() throws Exception {
        // Given
        afw.write(expectedContent, 0, expectedContent.length());

        // When
        afw.commit();

        // Then
        assertEquals(expectedContent.length(), Files.size(af.toPath()));
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