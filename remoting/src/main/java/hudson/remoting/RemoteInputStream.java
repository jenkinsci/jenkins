package hudson.remoting;

import java.io.InputStream;
import java.io.Serializable;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * Wraps {@link InputStream} so that it can be sent over the remoting channel.
 *
 * <p>
 * Note that this class by itself does not perform buffering.
 *
 * @author Kohsuke Kawaguchi
 */
public class RemoteInputStream extends InputStream implements Serializable {
    private transient InputStream core;

    public RemoteInputStream(InputStream core) {
        this.core = core;
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        int id = Channel.current().export(core);
        oos.writeInt(id);
    }

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        final Channel channel = Channel.current();
        assert channel !=null;

        this.core = new ProxyInputStream(channel, ois.readInt());
    }

    private static final long serialVersionUID = 1L;

//
//
// delegation to core
//
//

    public int read() throws IOException {
        return core.read();
    }

    public int read(byte[] b) throws IOException {
        return core.read(b);
    }

    public int read(byte[] b, int off, int len) throws IOException {
        return core.read(b, off, len);
    }

    public long skip(long n) throws IOException {
        return core.skip(n);
    }

    public int available() throws IOException {
        return core.available();
    }

    public void close() throws IOException {
        core.close();
    }

    public void mark(int readlimit) {
        core.mark(readlimit);
    }

    public void reset() throws IOException {
        core.reset();
    }

    public boolean markSupported() {
        return core.markSupported();
    }
}
