/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class LineTransformationOutputStreamTest {

    @Test
    void nl() throws Exception {
        test("\n");
    }

    @Test
    void crnl() throws Exception {
        test("\r\n");
    }

    @Test
    void cr() throws Exception {
        test("\r");
    }

    private void test(String linefeed) throws Exception {
        var count = new AtomicLong();
        long max = 1_000_000; // to see OOME in cr without fix: 1_000_000_000
        try (var counter = new LineTransformationOutputStream() {
            @Override protected void eol(byte[] b, int len) throws IOException {
                var line = new String(b, 0, len);
                assertThat(line, endsWith(linefeed));
                count.addAndGet(Integer.parseInt(trimEOL(line)));
            }
        }) {
            for (long i = 0; i < max; i++) {
                counter.write((i + linefeed).getBytes(StandardCharsets.UTF_8));
            }
        }
        assertThat(count.get(), is((max * (max - 1)) / 2));
    }

}
