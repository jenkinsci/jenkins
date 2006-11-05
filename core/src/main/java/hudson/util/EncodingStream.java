package hudson.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Hex-binary encoding stream.
 *
 * TODO: use base64binary.
 *
 * @author Kohsuke Kawaguchi
 * @see DecodingStream
 */
public class EncodingStream extends FilterOutputStream {
    public EncodingStream(OutputStream out) {
        super(out);
    }

    public void write(int b) throws IOException {
        out.write(chars.charAt(b/16));
        out.write(chars.charAt(b%16));
    }

    private static final String chars = "0123456789ABCDEF";
}
