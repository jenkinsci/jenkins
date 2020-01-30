package hudson;

import jenkins.util.SystemProperties;

import java.io.Closeable;
import java.io.IOException;

/**
 * Registers a DNS multi-cast service-discovery support.
 *
 * @author Kohsuke Kawaguchi
 * @deprecated No longer does anything.
 */
@Deprecated
public class DNSMultiCast implements Closeable {
    /** retained for compatibility with {@code jenkins-test-harness} and {@code jenkinsfile-runner} */
    public static boolean disabled = SystemProperties.getBoolean(DNSMultiCast.class.getName()+".disabled", true);

    @Override
    public void close() throws IOException {
    }
}
