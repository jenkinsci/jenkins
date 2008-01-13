package hudson.remoting;

import java.io.ObjectStreamException;

/**
 * Used internally in the remoting code to have the proxy object
 * implement readResolve.
 *
 * @author Kohsuke Kawaguchi
 */
public interface IReadResolve {
    Object readResolve() throws ObjectStreamException;
}
