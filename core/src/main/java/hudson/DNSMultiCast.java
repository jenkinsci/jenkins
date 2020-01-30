package hudson;

import jenkins.util.SystemProperties;

import java.io.Closeable;
import java.io.IOException;

/**
 * Registers a DNS multi-cast service-discovery support.
 *
 * @author Kohsuke Kawaguchi
 * @deprecated
 */
@Deprecated
public class DNSMultiCast implements Closeable {
    public static boolean disabled = SystemProperties.getBoolean(DNSMultiCast.class.getName()+".disabled", true);

    @Override
    public void close() throws IOException {
    }
}
