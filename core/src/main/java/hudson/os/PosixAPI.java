package hudson.os;


/**
 * POSIX API wrapper.
 * Formerly used the jna-posix library, but this has been superseded by jnr-posix.
 * @author Kohsuke Kawaguchi
 */
@Deprecated
public class PosixAPI {

    public static synchronized Object jnr() {
        throw new UnsupportedOperationException();
    }

    public static synchronized Object get() {
        throw new UnsupportedOperationException();
    }

    public boolean isNative() {
        return supportsNative();
    }

    public static boolean supportsNative() {
        return false;
    }

}
