package hudson.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Hex-binary decoding stream.
 *
 * @author Kohsuke Kawaguchi
 * @see EncodingStream
 */
public class DecodingStream extends FilterOutputStream {
    private int last = -1;

    public DecodingStream(OutputStream out) {
        super(out);
    }

    public void write(int b) throws IOException {
        if(last==-1) {
            last = b;
            return;
        }

        out.write( Character.getNumericValue(last)*16 + Character.getNumericValue(b) );
        last = -1;
    }


}
