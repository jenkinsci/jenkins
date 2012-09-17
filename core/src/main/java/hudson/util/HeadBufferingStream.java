/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.util;

import org.apache.commons.io.output.ByteArrayOutputStream;

import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;

/**
 * {@link FilterInputStream} that buffers the first N bytes to a byte array on the side.
 * This byte array can be then accessed later.
 *
 * <p>
 * Useful for sniffing the content of the stream after the error is discovered.
 *
 * @author Kohsuke Kawaguchi
 */
public class HeadBufferingStream extends FilterInputStream {
    private final ByteArrayOutputStream side;
    private final int sideBufferSize;

    public HeadBufferingStream(InputStream in, int sideBufferSize) {
        super(in);
        this.sideBufferSize = sideBufferSize;
        this.side = new ByteArrayOutputStream(sideBufferSize);
    }

    @Override
    public int read() throws IOException {
        int i = in.read();
        if(i>=0 && space()>0)
            side.write(i);
        return i;
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {
        int r = in.read(b, off, len);
        if(r>0) {
            int sp = space();
            if(sp>0)
                side.write(b,off,Math.min(r, sp));
        }
        return r;
    }

    /**
     * Available space in the {@link #side} buffer.
     */
    private int space() {
        return sideBufferSize-side.size();
    }

    /**
     * Read until we fill up the side buffer.
     */
    public void fillSide() throws IOException {
        byte[] buf = new byte[space()];
        while(space()>0) {
            if(read(buf)<0)
                return;
        }
    }

    /**
     * Gets the side buffer content.
     */
    public byte[] getSideBuffer() {
        return side.toByteArray();
    }
}
