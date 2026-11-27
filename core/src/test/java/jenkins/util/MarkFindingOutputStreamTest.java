package jenkins.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.util.StreamCopyThread;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * @author Kohsuke Kawaguchi
 */
class MarkFindingOutputStreamTest {

    private String mark = MarkFindingOutputStream.MARK;
    private String markHead = mark.substring(0, 5);
    private String markTail = mark.substring(5);

    private ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private MarkCountingOutputStream m = new MarkCountingOutputStream(baos);

    @Test
    void findTwice() throws IOException {
        write("foo" + mark + "bar" + mark);
        assertCount(2);
        assertOutput("foobar");
    }

    @Test
    void partialMatchTurnsOutToBeWrongIn2ndWrite() throws IOException {
        write("bar" + markHead);
        assertOutput("bar"); // at this point we should just see 'bar'

        write("foo"); // this needs to write what was held off during the first write
        assertCount(0);
        assertOutput("bar" + markHead + "foo");
    }

    /**
     * If a stream closes without completing a match, the partial match should be sent to the output.
     */
    @Test
    void closeInTheMiddle() throws IOException {
        write("foo" + markHead);
        m.close();
        assertCount(0);
        assertOutput("foo" + markHead);
    }

    @Test
    void oneByOne() throws IOException {
        m.write('1');
        writeOneByOne(mark);
        m.write('2');
        assertCount(1);
        assertOutput("12");
    }

    @Test
    void writeOneHoldOff() throws IOException {
        writeOneByOne(markHead);
        assertOutput("");
        writeOneByOne("x");
        assertOutput(markHead + "x");
        assertCount(0);
    }

    @ParameterizedTest
    @MethodSource("markerSplitPoints")
    void findMarkerAcrossTwoWrites(int split) throws IOException {
        byte[] bytes = mark.getBytes(StandardCharsets.UTF_8);
        write("before");
        m.write(bytes, 0, split);
        m.write(bytes, split, bytes.length - split);
        write("after");
        assertCount(1);
        assertOutput("beforeafter");
    }

    private static IntStream markerSplitPoints() {
        return IntStream.range(1, MarkFindingOutputStream.MARK.length());
    }

    @Test
    void findSplitMarkerWithOffsetsAndSurroundingOutput() throws IOException {
        byte[] first = ("__before" + markHead + "__").getBytes(StandardCharsets.UTF_8);
        byte[] second = ("__" + markTail + "after__").getBytes(StandardCharsets.UTF_8);
        m.write(first, 2, first.length - 4);
        m.write(second, 2, second.length - 4);
        assertCount(1);
        assertOutput("beforeafter");
    }

    @Test
    void findMarkerAcrossSingleByteAndBulkWrites() throws IOException {
        byte[] bytes = mark.getBytes(StandardCharsets.UTF_8);
        m.write(bytes[0]);
        m.write(bytes, 1, bytes.length - 1);
        assertCount(1);
        assertOutput("");
    }

    @Test
    void findMarkerAcrossThreeWrites() throws IOException {
        byte[] bytes = mark.getBytes(StandardCharsets.UTF_8);
        m.write(bytes, 0, 5);
        m.write(bytes, 5, 4);
        m.write(bytes, 9, bytes.length - 9);
        assertCount(1);
        assertOutput("");
    }

    @Test
    void findMarkerCopiedInSeparateChunks() {
        ByteArrayInputStream input = new ByteArrayInputStream(("before" + mark + "after").getBytes(StandardCharsets.UTF_8)) {
            @Override
            public synchronized int read(byte[] buffer, int offset, int length) {
                return super.read(buffer, offset, Math.min(length, 7));
            }
        };
        new StreamCopyThread("marker regression", input, m).run();
        assertCount(1);
        assertOutput("beforeafter");
    }

    private void assertOutput(String s) {
        assertEquals(s, baos.toString(StandardCharsets.UTF_8));
    }

    private void assertCount(int n) {
        assertEquals(n, m.count);
    }

    private void write(String s) throws IOException {
        m.write(s.getBytes(StandardCharsets.UTF_8));
    }

    private void writeOneByOne(String s) throws IOException {
        for (int i = 0; i < s.length(); i++)
            m.write(s.charAt(i));
    }

    static class MarkCountingOutputStream extends MarkFindingOutputStream {
        int count = 0;

        MarkCountingOutputStream(OutputStream base) {
            super(base);
        }

        @Override
        protected void onMarkFound() {
            count++;
        }
    }
}
