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
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * {@link ByteArrayOutputStream} re-implementation.
 *
 * <p>
 * This version allows one to read while writing is in progress. 
 *
 * @author Kohsuke Kawaguchi
 * @deprecated since 2008-05-28. Moved to stapler
 */
@Deprecated
public class ByteBuffer extends OutputStream {
    private byte[] buf = new byte[8192];
    /**
     * Size of the data.
     */
    private int size = 0;


    public synchronized void write(byte b[], int off, int len) throws IOException {
        ensureCapacity(len);
        System.arraycopy(b,off,buf,size,len);
        size+=len;
    }

    public synchronized void write(int b) throws IOException {
        ensureCapacity(1);
        buf[size++] = (byte)b;
    }

    public synchronized long length() {
        return size;
    }

    private void ensureCapacity(int len) {
        if(buf.length-size>len)
            return;

        byte[] n = new byte[Math.max(buf.length*2, size+len)];
        System.arraycopy(buf,0,n,0,size);
        this.buf = n;
    }

    public synchronized String toString() {
        return new String(buf,0,size);
    }

    /**
     * Writes the contents of this buffer to another OutputStream.
     */
    public synchronized void writeTo(OutputStream os) throws IOException {
        os.write(buf,0,size);        
    }

    /**
     * Creates an {@link InputStream} that reads from the underlying buffer.
     */
    public InputStream newInputStream() {
        return new InputStream() {
            private int pos = 0;
            public int read() throws IOException {
                synchronized(ByteBuffer.this) {
                    if(pos>=size)   return -1;
                    return buf[pos++];
                }
            }

            public int read(byte b[], int off, int len) throws IOException {
                synchronized(ByteBuffer.this) {
                    if(size==pos)
                        return -1;

                    int sz = Math.min(len,size-pos);
                    System.arraycopy(buf,pos,b,off,sz);
                    pos+=sz;
                    return sz;
                }
            }


            public int available() throws IOException {
                synchronized(ByteBuffer.this) {
                    return size-pos;
                }
            }

            public long skip(long n) throws IOException {
                synchronized(ByteBuffer.this) {
                    int diff = (int) Math.min(n,size-pos);
                    pos+=diff;
                    return diff;
                }
            }
        };
    }
}
