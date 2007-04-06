package hudson.util;

import org.apache.tools.ant.types.Resource;

import java.io.InputStream;
import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class StreamResource extends Resource {
    private final InputStream in;

    public StreamResource(InputStream in) {
        this.in = in;
    }

    public InputStream getInputStream() throws IOException {
        return in;
    }
}
