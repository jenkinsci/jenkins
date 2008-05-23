package hudson.remoting;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

/**
 * {@link InputStream} connected to socket.
 *
 * <p>
 * Unlike plain {@link Socket#getInputStream()}, closing the stream
 * does not close the entire socket, and instead it merely partial-close
 * a socket in the direction.
 *
 * @author Kohsuke Kawaguchi
 */
public class SocketInputStream extends FilterInputStream {
    private final Socket socket;

    public SocketInputStream(Socket socket) throws IOException {
        super(socket.getInputStream());
        this.socket = socket;
    }

    @Override
    public void close() throws IOException {
        socket.shutdownInput();
    }
}
