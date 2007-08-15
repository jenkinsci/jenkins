package hudson.remoting;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;

/**
 * {@link OutputStream} that can be sent over to the remote {@link Channel},
 * so that the remote {@link Callable} can write to a local {@link OutputStream}.
 *
 * <h2>Usage</h2>
 * <p>
 * To have a remote machine write to a local {@link OutputStream}:
 * <pre>
 * final OutputStream out = new RemoteOutputStream(os);
 *
 * channel.call(new Callable() {
 *   public Object call() {
 *     // this will write to 'os'.
 *     out.write(...);
 *   }
 * });
 * </pre>
 *
 * <p>
 * To have a local machine write to a remote {@link OutputStream}:
 *
 * <pre>
 * OutputStream os = channel.call(new Callable() {
 *   public Object call() {
 *       OutputStream os = new FileOutputStream(...); // or any other OutputStream
 *       return new RemoteOutputStream(os);
 *   }
 * });
 * </pre>
 *
 * @see RemoteInputStream
 * @author Kohsuke Kawaguchi
 */
public final class RemoteOutputStream extends OutputStream implements Serializable {
    /**
     * On local machine, this points to the {@link OutputStream} where
     * the data will be sent ultimately.
     *
     * On remote machine, this points to {@link ProxyOutputStream} that
     * does the network proxy.
     */
    private transient OutputStream core;

    public RemoteOutputStream(OutputStream core) {
        this.core = core;
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        int id = Channel.current().export(core,false);
        oos.writeInt(id);
    }

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        final Channel channel = Channel.current();
        assert channel !=null;

        this.core = new ProxyOutputStream(channel, ois.readInt());
    }

    private static final long serialVersionUID = 1L;


//
//
// delegation to core
//
//
    public void write(int b) throws IOException {
        core.write(b);
    }

    public void write(byte[] b) throws IOException {
        core.write(b);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        core.write(b, off, len);
    }

    public void flush() throws IOException {
        core.flush();
    }

    public void close() throws IOException {
        core.close();
    }
}
