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

package hudson.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FlightRecorderInputStreamTest {

    @Test
    void skipAtEndOfStreamReturnsZeroNotMinusOne() throws Exception {
        FlightRecorderInputStream in = new FlightRecorderInputStream(new ByteArrayInputStream(new byte[0]));
        assertEquals(0, in.skip(10));
    }

    @Test
    void skipOnNonEmptyStreamReturnsNonNegativeWithinRequest() throws Exception {
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        FlightRecorderInputStream in = new FlightRecorderInputStream(new ByteArrayInputStream(data));
        long skipped = in.skip(3);
        assertTrue(skipped >= 0 && skipped <= 3, "skip should return a value in [0, n], was " + skipped);
    }
}
