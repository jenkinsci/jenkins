package hudson.remoting;

import java.io.IOException;

/**
 * Used when the exception thrown by the remoted code cannot be serialized.
 *
 * <p>
 * This exception captures the part of the information of the original exception
 * so that the caller can get some information about the problem that happened.
 *
 * @author Kohsuke Kawaguchi
 */
public class ProxyException extends IOException {
    public ProxyException(Throwable cause) {
        super(cause.toString()); // use toString() to capture the class name and error message
        setStackTrace(cause.getStackTrace());

        // wrap all the chained exceptions
        if(cause.getCause()!=null)
            initCause(new ProxyException(getCause()));
    }
}
