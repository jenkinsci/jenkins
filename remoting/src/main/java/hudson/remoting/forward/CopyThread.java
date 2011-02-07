package hudson.remoting.forward;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Copies a stream and close them at EOF.
 *
 * @author Kohsuke Kawaguchi
 */
final class CopyThread extends Thread {
    private final InputStream in;
    private final OutputStream out;

    public CopyThread(String threadName, InputStream in, OutputStream out) {
        super(threadName);
        this.in = in;
        this.out = out;
    }

    public void run() {
        try {
            try {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0)
                    out.write(buf, 0, len);
            } finally {
                in.close();
                out.close();
            }
        } catch (IOException e) {
            // TODO: what to do?
        }
    }
}
