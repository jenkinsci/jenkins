package hudson.console;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class ConsoleAnnotationOutputStreamTest {

    /**
     * Verifies that flush() drains any partial line buffered in
     * LineTransformationOutputStream even when the line has no trailing newline.
     *
     * Regression test for https://github.com/jenkinsci/jenkins/issues/26739
     * Programs that output lines with a leading newline (e.g. PHP scripts using
     * {@code echo "\nChecked: [$domain]."}) caused live console log streaming to
     * silently drop buffered partial lines because flush() did not call forceEol().
     */
    @Test
    public void flushShouldDrainPartialLineBuffer() throws Exception {
        StringWriter writer = new StringWriter();
        ConsoleAnnotationOutputStream<Void> stream =
                new ConsoleAnnotationOutputStream<>(writer, null, null, StandardCharsets.UTF_8);

        byte[] data = "Checked: [m012345xxxx].".getBytes(StandardCharsets.UTF_8);
        stream.write(data);

        stream.flush();

        assertEquals("Checked: [m012345xxxx].", writer.toString());
    }
}
