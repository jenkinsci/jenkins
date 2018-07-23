package jenkins.model.logging.impl;

import hudson.model.StreamBuildListener;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * @author Oleg Nenashev
 * @since TODO
 */
@Restricted(Beta.class)
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
