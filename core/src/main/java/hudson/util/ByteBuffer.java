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
 * @deprecated Moved to stapler
 */
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
