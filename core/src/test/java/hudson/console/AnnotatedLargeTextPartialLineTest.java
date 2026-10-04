/*
 * The MIT License
 *
 * Copyright 2026 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.console;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.framework.io.ByteBuffer;

/**
 * Tests that {@link AnnotatedLargeText#writeLogTo(long, java.io.OutputStream)} does not silently drop
 * a trailing partial line, mirroring the behavior of {@link AnnotatedLargeText#writeHtmlTo(long, java.io.Writer)}.
 */
class AnnotatedLargeTextPartialLineTest {

    private static final String COMPLETE_LINE = "first line\n";
    private static final String PARTIAL_LINE = "second partial";

    @Test
    void completedLogEmitsTrailingPartialLine() throws Exception {
        ByteBuffer memory = buffer(COMPLETE_LINE + PARTIAL_LINE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        long next = new AnnotatedLargeText<>(memory, UTF_8, true, null).writeLogTo(0, out);

        assertThat(out.toString(UTF_8), is(COMPLETE_LINE + PARTIAL_LINE));
        assertThat(next, is(memory.length()));
    }

    @Test
    void completedLogEndingWithNewlineIsUnaffected() throws Exception {
        ByteBuffer memory = buffer(COMPLETE_LINE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        long next = new AnnotatedLargeText<>(memory, UTF_8, true, null).writeLogTo(0, out);

        assertThat(out.toString(UTF_8), is(COMPLETE_LINE));
        assertThat(next, is(memory.length()));
    }

    @Test
    void streamedIncompleteLogWithholdsTrailingPartialLine() throws Exception {
        ByteBuffer memory = buffer(COMPLETE_LINE + PARTIAL_LINE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // The streaming branch of LargeText writes in bulk rather than up to the last newline,
        // so the partial line stays in the line buffer and must not be counted as delivered.
        long next = new StreamingAnnotatedLargeText(memory).writeLogTo(0, out);

        assertThat(out.toString(UTF_8), is(COMPLETE_LINE));
        assertThat(next, is((long) COMPLETE_LINE.length()));
    }

    @Test
    void streamedIncompleteLogResumesFromWithheldOffset() throws Exception {
        ByteBuffer memory = buffer(COMPLETE_LINE + PARTIAL_LINE);
        StreamingAnnotatedLargeText text = new StreamingAnnotatedLargeText(memory);
        long next = text.writeLogTo(0, new ByteArrayOutputStream());

        memory.write("\nthird line\n".getBytes(UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long end = text.writeLogTo(next, out);

        assertThat(out.toString(UTF_8), is(PARTIAL_LINE + "\nthird line\n"));
        assertThat(end, is(memory.length()));
    }

    @Test
    void incompleteLogStopsAtLastNewline() throws Exception {
        ByteBuffer memory = buffer(COMPLETE_LINE + PARTIAL_LINE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        long next = new AnnotatedLargeText<>(memory, UTF_8, false, null).writeLogTo(0, out);

        assertThat(out.toString(UTF_8), is(COMPLETE_LINE));
        assertThat(next, is((long) COMPLETE_LINE.length()));
    }

    private static ByteBuffer buffer(String text) throws IOException {
        ByteBuffer memory = new ByteBuffer();
        memory.write(text.getBytes(UTF_8));
        return memory;
    }

    /** Simulates {@code Accept: multipart/form-data} without a Stapler request being bound to the thread. */
    private static final class StreamingAnnotatedLargeText extends AnnotatedLargeText<Void> {

        StreamingAnnotatedLargeText(ByteBuffer memory) {
            super(memory, UTF_8, false, null);
        }

        @Override
        public boolean isStreamingRequest(StaplerRequest2 req) {
            return true;
        }
    }
}
