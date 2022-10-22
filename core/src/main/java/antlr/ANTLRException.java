package antlr;

import org.antlr.v4.runtime.misc.ParseCancellationException;

/**
 * This class is for binary compatibility for older plugins that
 * import ANTLRException.
 */
public class ANTLRException extends ParseCancellationException {
    public ANTLRException() {
        // nothing needs to be done here
    }

    public ANTLRException(String msg) {
        super(msg);
    }
}
