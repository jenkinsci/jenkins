/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
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

import hudson.util.ByteArrayOutputStream2;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Filtering {@link OutputStream} that buffers text by line, so that the derived class
 * can perform some manipulation based on the contents of the whole line.
 *
 * TODO: Mac is supposed to be CR-only. This class needs to handle that.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.349
 */
public abstract class LineTransformationOutputStream extends OutputStream {
    private ByteArrayOutputStream2 buf = new ByteArrayOutputStream2();

    /**
     * Called for each end of the line.
     *
     * @param b
     *      Contents of the whole line, including the EOL code like CR/LF.
     * @param len
     *      Specifies the length of the valid contents in 'b'. The rest is garbage.
     *      This is so that the caller doesn't have to allocate an array of the exact size. 
     */
    protected abstract void eol(byte[] b, int len) throws IOException;

    public void write(int b) throws IOException {
        buf.write(b);
        if (b==LF) eol();
    }

    private void eol() throws IOException {
        eol(buf.getBuffer(),buf.size());

        // reuse the buffer under normal circumstances, but don't let the line buffer grow unbounded
        if (buf.size()>4096)
            buf = new ByteArrayOutputStream2();
        else
            buf.reset();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        int end = off+len;

        for( int i=off; i<end; i++ )
            write(b[i]);
    }

    @Override
    public void close() throws IOException {
        forceEol();
    }

    /**
     * Forces the EOL behavior.
     *
     * Useful if the caller wants to make sure the buffered content is all processed, but without
     * actually neither flushing nor closing the stream.
     */
    public void forceEol() throws IOException {
        if (buf.size()>0) {
            /*
                because LargeText cuts output at the line end boundary, this is
                possible only for the very end of the console output, if the output ends without NL.
             */
            eol();
        }
    }

    protected String trimEOL(String line) {
        int slen = line.length();
        while (slen>0) {
            char ch = line.charAt(slen-1);
            if (ch=='\r' || ch=='\n') {
                slen--;
                continue;
            }
            break;
        }
        line = line.substring(0,slen);
        return line;
    }

    private static final int LF = 0x0A;
}
