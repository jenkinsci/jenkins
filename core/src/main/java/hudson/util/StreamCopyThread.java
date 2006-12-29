package hudson.util;

import hudson.Util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * {@link Thread} that copies {@link InputStream} to {@link OutputStream}.
 *
 * @author Kohsuke Kawaguchi
 */
public class StreamCopyThread extends Thread {
    private final InputStream in;
    private final OutputStream out;

    public StreamCopyThread(String threadName, InputStream in, OutputStream out) {
        super(threadName);
        this.in = in;
        this.out = out;
    }

    public void run() {
        try {
            Util.copyStream(in,out);
            in.close();
        } catch (IOException e) {
            // TODO: what to do?
        }
    }
}
