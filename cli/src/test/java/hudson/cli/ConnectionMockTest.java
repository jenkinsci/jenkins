/*
 * The MIT License
 *
 * Copyright (c) 2013 Ericsson
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

import hudson.remoting.FastPipedInputStream;
import hudson.remoting.FastPipedOutputStream;

import java.io.DataInputStream;
import java.io.IOException;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.powermock.api.mockito.PowerMockito.*;

import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * @author marco.miller@ericsson.com
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({Connection.class})
public class ConnectionMockTest {

    @Test
    public void shouldTolerateEmptyByteArrayUponStreamZeroValue() throws IOException {
        DataInputStream din = mock(DataInputStream.class);
        //when(din.readInt()).thenReturn(0) does not work; mock always return 0 for some TBD reason
        Connection c = new Connection(din, new FastPipedOutputStream(new FastPipedInputStream()));
        assertTrue(c.readByteArray().length == 0);
    }
}
