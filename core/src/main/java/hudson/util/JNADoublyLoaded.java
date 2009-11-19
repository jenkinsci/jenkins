package hudson.util;

/**
 * Indicates that JNA is already loaded in another class loader.
 *
 * @author Kohsuke Kawaguchi
 */
public class JNADoublyLoaded extends HudsonFailedToLoad {
    public JNADoublyLoaded(Throwable exception) {
        super(exception);
    }
}
