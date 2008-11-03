package hudson.util.jna;

/**
 * Exception in the registry code.
 *
 * @author Kohsuke Kawaguchi
 */
public class JnaException extends RuntimeException {
    public JnaException(int errorCode) {
        super("Win32 error: "+errorCode);
    }
}
