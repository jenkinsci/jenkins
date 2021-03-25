package hudson.util;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

public class FileChannelWriterTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    File file;
    FileChannelWriter writer;

    @Before
    public void setUp() throws Exception {
        file = temporaryFolder.newFile();
        writer = new FileChannelWriter(file.toPath(), StandardCharsets.UTF_8, true, true,  StandardOpenOption.WRITE);
    }

    @Test
    public void write() throws Exception {
        writer.write("helloooo");
        writer.close();

        assertContent("helloooo");
    }


    @Test
    public void flush() throws Exception {
        writer.write("hello é è à".toCharArray());

        writer.flush();
        assertContent("hello é è à");
    }

    @Test
    public void close() throws Exception {
        writer.write("helloooo");
        writer.close();

        assertThrows(ClosedChannelException.class, () -> writer.write("helloooo"));
    }


    private void assertContent(String string) throws IOException {
        assertThat(FileUtils.readFileToString(file, StandardCharsets.UTF_8), equalTo(string));
    }
}
