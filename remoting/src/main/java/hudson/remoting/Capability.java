package hudson.remoting;

import java.io.Serializable;

/**
 * Represents additional features implemented on {@link Channel}.
 *
 * <p>
 * Each {@link Channel} exposes its capability to {@link Channel#getProperty(Object)}.
 *
 * <p>
 * This mechanism allows two different versions of <tt>remoting.jar</tt> to talk to each other.
 *
 * @author Kohsuke Kawaguchi
 * @see Channel#remoteCapability
 */
final class Capability implements Serializable {
    /**
     * Bit mask of optional capabilities.
     */
    private final long mask;

    Capability(long mask) {
        this.mask = mask;
    }

    Capability() {
        this(1);
    }

    /**
     * Does this implementation supports multi-classloader serialization in
     * {@link UserRequest}?
     *
     * @see MultiClassLoaderSerializer
     */
    boolean supportsMultiClassLoaderRPC() {
        return (mask&1)!=0;
    }

    private static final long serialVersionUID = 1L;
}
