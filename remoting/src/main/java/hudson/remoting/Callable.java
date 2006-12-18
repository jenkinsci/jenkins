package hudson.remoting;

import java.io.Serializable;

/**
 * Represents computation to be done on a remote system.
 *
 * @see Channel
 * @author Kohsuke Kawaguchi
 */
public interface Callable<V,T extends Throwable> extends Serializable {
    /**
     * Performs computation and returns the result,
     * or throws some exception.
     */
    V call() throws T;
}
