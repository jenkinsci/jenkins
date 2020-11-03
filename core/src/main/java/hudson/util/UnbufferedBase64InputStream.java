package hudson.util;

import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

/**
 * Filter InputStream that decodes base64 without doing any buffering.
 *
 * <p>
 * This is slower implementation, but it won't consume unnecessary bytes from the underlying {@link InputStream},
 * allowing the reader to switch between the unencoded bytes and base64 bytes.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.349
 * @deprecated Use {@link java.util.Base64.Decoder#wrap}.
 */
@Deprecated
public class UnbufferedBase64InputStream extends FilterInputStream {
    private byte[] encoded = new byte[4];
    private byte[] decoded;
    private int pos;
    private final DataInputStream din;
    private static final Base64.Decoder decoder = Base64.getDecoder();

    public UnbufferedBase64InputStream(InputStream in) {
        super(in);
        din = new DataInputStream(in);
        // initial placement to force the decoding in the next read()
        pos = 4;
        decoded = encoded;
    }

    @Override
    public int read() throws IOException {
        if (decoded.length==0)
            return -1; // EOF

        if (pos==decoded.length) {
            din.readFully(encoded);
            decoded = decoder.decode(encoded);
            if (decoded.length==0)  return -1; // EOF
            pos = 0;
        }

        return (decoded[pos++])&0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int i;
        for (i=0; i<len; i++) {
            int ch = read();
            if (ch<0)   break;
            b[off+i] = (byte)ch;
        }
        return i==0 ? -1 : i;
    }

    @Override
    public long skip(long n) throws IOException {
        long r=0;
        while (n>0) {
            int ch = read();
            if (ch<0)   break;
            n--; r++;
        }
        return r;
    }
}
