package hudson.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hudson.console.AnnotatedLargeText;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kohsuke.stapler.framework.io.ByteBuffer;

class ConsoleCommandTailTest {

    private ConsoleCommand command;
    private FreeStyleBuild build;
    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() {
        FreeStyleProject project = mock(FreeStyleProject.class);
        build = mock(FreeStyleBuild.class);
        when(project.getBuildByNumber(1)).thenReturn(build);
        when(build.getCharset()).thenReturn(UTF_8);

        command = new ConsoleCommand();
        command.job = project;
        command.build = "1";
        command.stdout = new PrintStream(stdout, false, UTF_8);
        command.setClientCharset(UTF_8);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tailCases")
    void lastNLines(String description, String log, int lines, String expected) throws Exception {
        logSnapshot(log);
        command.n = lines;

        assertEquals(0, command.run());
        assertEquals(expected, stdout.toString(UTF_8));
    }

    private static Stream<Arguments> tailCases() {
        return Stream.of(
                Arguments.of("zero lines from an empty log", "", 0, ""),
                Arguments.of("zero lines from a terminated log", "first\nsecond\n", 0, ""),
                Arguments.of("zero lines from an unterminated log", "first\nsecond", 0, ""),
                Arguments.of("empty log", "", 1, ""),
                Arguments.of("empty log with an oversized request", "", 10, ""),
                Arguments.of("single unterminated line", "first", 1, "first"),
                Arguments.of("single terminated line", "first\n", 1, "first\n"),
                Arguments.of("short log", "first", 10, "first"),
                Arguments.of("request equal to the line count", "first\nsecond\nthird\n", 3, "first\nsecond\nthird\n"),
                Arguments.of("request larger than the line count", "first\nsecond\nthird\n", 10, "first\nsecond\nthird\n"),
                Arguments.of("last terminated line", "first\nsecond\nthird\n", 1, "third\n"),
                Arguments.of("last unterminated lines", "first\nsecond\nthird", 2, "second\nthird"),
                Arguments.of("CRLF line endings", "first\r\nsecond\r\nthird\r\n", 2, "second\r\nthird\r\n"),
                Arguments.of("bare CR line endings", "first\rsecond\rthird\r", 2, "second\rthird\r"),
                Arguments.of("consecutive LF blank lines", "first\n\n\nlast\n", 3, "\n\nlast\n"),
                Arguments.of("consecutive CRLF blank lines", "first\r\n\r\nlast\r\n", 2, "\r\nlast\r\n"),
                Arguments.of("consecutive bare CR blank lines", "first\r\rlast\r", 2, "\rlast\r"),
                Arguments.of("leading blank line", "\nlast\n", 2, "\nlast\n"),
                Arguments.of("log containing only blank lines", "\n\n", 1, "\n"),
                Arguments.of("UTF-8 byte offsets", "पहले\n最后\n", 1, "最后\n"),
                Arguments.of("CRLF across the read boundary", "x".repeat(4095) + "\r\nlast\r\n", 1, "last\r\n"),
                Arguments.of("LF at the read boundary", "x".repeat(4095) + "\nlast", 1, "last"));
    }

    @Test
    void defaultShowsWholeLog() throws Exception {
        String log = "first\n\nlast";
        logSnapshot(log);

        assertEquals(-1, command.n);
        assertEquals(0, command.run());
        assertEquals(log, stdout.toString(UTF_8));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("followCases")
    void zeroLinesAndFollowStartsAtCurrentEnd(String description, String initialLog, String appended, String expected) throws Exception {
        logSnapshot(initialLog);
        try (ByteBuffer completedLog = new ByteBuffer()) {
            completedLog.write((initialLog + appended).getBytes(UTF_8));
            when(build.getLogText()).thenReturn(new AnnotatedLargeText<>(completedLog, UTF_8, true, null));
            command.n = 0;
            command.follow = true;

            assertEquals(0, command.run());
            assertEquals(expected, stdout.toString(UTF_8));
        }
    }

    private static Stream<Arguments> followCases() {
        return Stream.of(
                Arguments.of("append after existing output", "before\n", "after\n", "after\n"),
                Arguments.of("append to an empty log", "", "after\n", "after\n"),
                Arguments.of("UTF-8 snapshot offset", "पहले\n", "最后\n", "最后\n"),
                Arguments.of("completed log without appended output", "before\n", "", ""));
    }

    @Test
    void seekingAndCopyingCloseTheirStreams() throws Exception {
        byte[] log = "first\nlast\n".getBytes(UTF_8);
        ByteArrayInputStream seeking = spy(new ByteArrayInputStream(log));
        ByteArrayInputStream copying = spy(new ByteArrayInputStream(log));
        when(build.getLogInputStream()).thenReturn(seeking, copying);
        command.n = 1;

        assertEquals(0, command.run());
        assertEquals("last\n", stdout.toString(UTF_8));
        verify(seeking).close();
        verify(copying).close();
    }

    private void logSnapshot(String log) throws IOException {
        byte[] contents = log.getBytes(UTF_8);
        when(build.getLogInputStream()).thenAnswer(ignored -> new ByteArrayInputStream(contents));
    }
}
