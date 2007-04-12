package hudson.util;

import org.apache.tools.ant.types.Resource;

import java.io.InputStream;
import java.io.IOException;

/**
 * Wraps {@link InputStream} to {@link Resource}.
 * @author Kohsuke Kawaguchi
 */
public class StreamResource extends Resource {
    private final InputStream in;

    /**
     * @param name
     *      Used for display purpose.
     */
    public StreamResource(String name, InputStream in) {
        this.in = in;
        setName(name);
    }

    public InputStream getInputStream() throws IOException {
        return in;
    }
}
