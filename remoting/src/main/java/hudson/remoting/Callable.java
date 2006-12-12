package hudson.remoting;

import java.io.Serializable;

/**
 * @author Kohsuke Kawaguchi
 */
public interface Callable<V,T extends Throwable> extends Serializable {
    V call() throws T;
}
