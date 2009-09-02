package hudson.remoting;

import hudson.remoting.Channel.Mode;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;

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
        this(0);
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

    /**
     * Writes out the capacity preamble.
     */
    void writePreamble(OutputStream os) throws IOException {
        os.write(PREAMBLE);
        ObjectOutputStream oos = new ObjectOutputStream(Mode.TEXT.wrap(os));
        oos.writeObject(this);
        oos.flush();
    }

    /**
     * The opposite operation of {@link #writePreamble(OutputStream)}.
     */
    public static Capability read(InputStream is) throws IOException {
        try {
            ObjectInputStream ois = new ObjectInputStream(Mode.TEXT.wrap(is));
            return (Capability)ois.readObject();
        } catch (ClassNotFoundException e) {
            throw (Error)new NoClassDefFoundError(e.getMessage()).initCause(e);
        }
    }

    private static final long serialVersionUID = 1L;

    static final byte[] PREAMBLE;

    static {
        try {
            PREAMBLE = "<===[HUDSON REMOTING CAPACITY]===>".getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }
}
