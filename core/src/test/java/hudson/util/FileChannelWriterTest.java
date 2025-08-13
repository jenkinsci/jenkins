package hudson.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileChannelWriterTest {

    private Path file;
    private FileChannelWriter writer;

    @BeforeEach
    void setUp() throws Exception {
        file = Files.createTempFile("junit", null);
        writer = new FileChannelWriter(file, StandardCharsets.UTF_8, true, true,  StandardOpenOption.WRITE);
    }

    @Test
    void write() throws Exception {
        writer.write("helloooo");
        writer.close();

        assertContent("helloooo");
    }

    @Test
    void flush() throws Exception {
        writer.write("hello é è à".toCharArray());

        writer.flush();
        assertContent("hello é è à");
    }

    @Test
    void close() throws Exception {
        writer.write("helloooo");
        writer.close();

        assertThrows(ClosedChannelException.class, () -> writer.write("helloooo"));
    }

    private void assertContent(String string) throws IOException {
        assertThat(Files.readString(file), equalTo(string));
    }
}
