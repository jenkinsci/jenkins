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

    public int read() throws IOException {
        int i = in.read();
        if(i>=0 && space()>0)
            side.write(i);
        return i;
    }

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
