package hudson.maven.agent;

import java.io.OutputStream;
import java.io.IOException;
import java.io.FilterOutputStream;

/**
 * JDK's {@link FilterOutputStream} has some real issues. 
 *
 * @author Kohsuke Kawaguchi
 */
class RealFilterOutputStream extends FilterOutputStream {
    public RealFilterOutputStream(OutputStream core) {
        super(core);
    }

    public void write(byte[] b) throws IOException {
        out.write(b);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    public void close() throws IOException {
        out.close();
    }
}
