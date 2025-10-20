package hudson.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Execution(ExecutionMode.CONCURRENT)
class HexDumpTest {

    @DisplayName("Test HexDump.toHex(byte[] buf)")
    @ParameterizedTest(name = "{index} => expected: {0}, buf: {1}")
    @MethodSource("testToHex1Sources")
    void testToHex1(String expected, byte[] buf) {
        assertEquals(expected, HexDump.toHex(buf));
    }

    static Stream<Arguments> testToHex1Sources() {
        return Stream.of(
            arguments("'fooBar'", new byte[] {'f', 'o', 'o', 'B', 'a', 'r'}),
            arguments("0xc3", new byte[] {(byte) 'Ã'}),
            arguments("0xac '100'", new byte[] {(byte) '€', '1', '0', '0'}),
            arguments("'1' 0xf7 '2'", new byte[] {'1', (byte) '÷', '2'}),
            arguments("'foo' 0x0a\n'Bar'", new byte[] {'f', 'o', 'o', '\n', 'B', 'a', 'r'})
        );
    }

    @DisplayName("Test HexDump.toHex(byte[] buf, int start, int len)")
    @ParameterizedTest(name = "{index} => expected: {0}, buf: {1}, start: {2}, len: {3}")
    @MethodSource("testToHex2Sources")
    void testToHex2(String expected, byte[] buf, int start, int len) {
        assertEquals(expected, HexDump.toHex(buf, start, len));
    }

    static Stream<Arguments> testToHex2Sources() {
        return Stream.of(
            arguments("'ooBa'", new byte[] {'f', 'o', 'o', 'B', 'a', 'r'}, 1, 4),
            arguments("0xc3", new byte[] {(byte) 'Ã'}, 0, 1),
            arguments("0xac '10'", new byte[] {(byte) '€', '1', '0', '0'}, 0, 3),
            arguments("0xf7 '2'", new byte[] {'1', (byte) '÷', '2'}, 1, 2),
            arguments("'Bar'", new byte[] {'f', 'o', 'o', '\n', 'B', 'a', 'r'}, 4, 3),
            arguments("", new byte[] {'f', 'o', 'o', 'B', 'a', 'r'}, 0, 0)
        );
    }
}
