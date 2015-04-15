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

import java.io.OutputStream;
import java.io.Writer;
import java.io.IOException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CoderResult;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.*;

/**
 * {@link OutputStream} that writes to {@link Writer}
 * by assuming the platform default encoding.
 *
 * @author Kohsuke Kawaguchi
 * @deprecated since 2008-05-28.
 *      Use the one in stapler.
 */
@Deprecated
public class WriterOutputStream extends OutputStream {
    private final Writer writer;
    private final CharsetDecoder decoder;

    private java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(1024);
    private CharBuffer out = CharBuffer.allocate(1024);

    public WriterOutputStream(Writer out) {
        this.writer = out;
        decoder = DEFAULT_CHARSET.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPLACE);
        decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
    }

    public void write(int b) throws IOException {
        if(buf.remaining()==0)
            decode(false);
        buf.put((byte)b);
    }

    public void write(byte b[], int off, int len) throws IOException {
        while(len>0) {
            if(buf.remaining()==0)
                decode(false);
            int sz = Math.min(buf.remaining(),len);
            buf.put(b,off,sz);
            off += sz;
            len -= sz;
        }
    }

    public void flush() throws IOException {
        decode(false);
        flushOutput();
        writer.flush();
    }

    private void flushOutput() throws IOException {
        writer.write(out.array(),0,out.position());
        out.clear();
    }

    public void close() throws IOException {
        decode(true);
        flushOutput();
        writer.close();

        buf.rewind();
    }

    /**
     * Decodes the contents of {@link #buf} as much as possible to {@link #out}.
     * If necessary {@link #out} is further sent to {@link #writer}.
     *
     * <p>
     * When this method returns, the {@link #buf} is back to the 'accumulation'
     * mode.
     *
     * @param last
     *      if true, tell the decoder that all the input bytes are ready.
     */
    private void decode(boolean last) throws IOException {
        buf.flip();
        while(true) {
            CoderResult r = decoder.decode(buf, out, last);
            if(r==CoderResult.OVERFLOW) {
                flushOutput();
                continue;
            }
            if(r==CoderResult.UNDERFLOW) {
                buf.compact();
                return;
            }
            // otherwise treat it as an error
            r.throwException();
        }
    }

    private static final Charset DEFAULT_CHARSET = getDefaultCharset();

    private static Charset getDefaultCharset() {
        try {
            String encoding = System.getProperty("file.encoding");
            return Charset.forName(encoding);
        } catch (UnsupportedCharsetException e) {
            return Charset.forName("UTF-8");
        }
    }
}
