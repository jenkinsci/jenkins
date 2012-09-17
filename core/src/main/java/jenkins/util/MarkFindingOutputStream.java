package jenkins.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

/**
 * Filtering {@link OutputStream} that looks for {@link #MARK} in the output stream and notifies the callback.
 *
 * The mark itself will be removed from the stream.
 * 
 * @author Kohsuke Kawaguchi
 * @since 1.458
 */
public abstract class MarkFindingOutputStream extends OutputStream {
    private final OutputStream base;

    public MarkFindingOutputStream(OutputStream base) {
        this.base = base;
    }

    /**
     * Position in {@link #MARK} if we are currently suspecting a match.
     */
    private int match = 0;

    public synchronized void write(int b) throws IOException {
        if (MBYTES[match] == b) {// another byte matched. Good. Keep going...
            match++;
            if (match == MBYTES.length) {
                // don't send MARK to the output, but instead notify the callback
                onMarkFound();
                match = 0;
            }
        } else {
            if (match > 0) {
                // only matched partially. send the partial match that we held off down the pipe
                base.write(MBYTES, 0, match);
                match = 0;

                // this might match the first byte in MARK, so retry.
                write(b);
            } else {
                base.write(b);
            }
        }
    }

    public void write(byte b[], int off, int len) throws IOException {
        final int start = off; 
        final int end = off + len;
        for (int i=off; i<end; ) {
            if (MBYTES[match] == b[i]) {// another byte matched. Good. Keep going...
                match++;
                i++;
                if (match == MBYTES.length) {
                    base.write(b,off,i-off-MBYTES.length);    // flush the portion up to MARK
                    // don't send MARK to the output, but instead notify the callback
                    onMarkFound();
                    match = 0;
                    off = i;
                    len = end-i;
                }
            } else {
                if (match > 0) {
                    // only matched partially.
                    // if a part of the partial match spans into the previous write, we need to fake that write.
                    int extra = match-(i-start);
                    if (extra>0) {
                        base.write(MBYTES,0,extra);
                    }
                    match = 0;

                    // this b[i] might be a fast byte in MARK, so we'll retry
                } else {
                    // irrelevant byte.
                    i++;
                }
            }

        }

        // if we are partially matching, can't send that portion yet.
        if (len-match>0)
            base.write(b, off, len-match);
    }

    public void flush() throws IOException {
        flushPartialMatch();
        base.flush();
    }

    public void close() throws IOException {
        flushPartialMatch();
        base.close();
    }

    private void flushPartialMatch() throws IOException {
        if (match>0) {
            base.write(MBYTES,0,match);
            match = 0;
        }
    }

    protected  abstract void onMarkFound();

    // having a new line in the end makes it work better with line-buffering transformation
    public static final String MARK = "[Jenkins:SYNC-MARK]\n";
    private static final byte[] MBYTES = toUTF8(MARK);
    
    private static byte[] toUTF8(String s) {
        try {
            return s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }
}
