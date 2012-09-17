package hudson.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * {@link ByteArrayOutputStream} with access to its raw buffer.
 * @since 1.349
 */
public class ByteArrayOutputStream2 extends ByteArrayOutputStream {
    public ByteArrayOutputStream2() {
    }

    public ByteArrayOutputStream2(int size) {
        super(size);
    }

    public byte[] getBuffer() {
        return buf;
    }

    /**
     * Reads the given {@link InputStream} completely into the buffer.
     */
    public void readFrom(InputStream is) throws IOException {
        while(true) {
            if(count==buf.length) {
                // realllocate
                byte[] data = new byte[buf.length*2];
                System.arraycopy(buf,0,data,0,buf.length);
                buf = data;
            }

            int sz = is.read(buf,count,buf.length-count);
            if(sz<0)     return;
            count += sz;
        }
    }
}
