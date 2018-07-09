package jenkins.model.logging;

import hudson.model.StreamBuildListener;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * @author Oleg Nenashev
 * @since TODO
 */
public class CloseableStreamBuildListener extends StreamBuildListener implements Closeable {

    private final OutputStream ostream;

    public CloseableStreamBuildListener(OutputStream out, Charset charset) {
        super(out, charset);
        ostream = out;
    }

    @Override
    public void close() throws IOException {
        ostream.close();
    }
}
