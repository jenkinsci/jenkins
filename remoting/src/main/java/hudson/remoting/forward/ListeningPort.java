package hudson.remoting.forward;

import java.io.Closeable;
import java.io.IOException;

/**
 * Represents a listening port that forwards a connection
 * via port forwarding.
 *
 * @author Kohsuke Kawaguchi
 */
public interface ListeningPort extends Closeable {
    /**
     * TCP/IP port that is listening.
     */
    int getPort();

    /**
     * Shuts down the port forwarding by removing the server socket.
     * Connections that are already established will not be affected
     * by this operation.
     */
    void close() throws IOException;
}
