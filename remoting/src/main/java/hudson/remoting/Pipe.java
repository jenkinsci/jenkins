package hudson.remoting;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Serializable;

/**
 * Pipe for the remote {@link Callable} and the local program to talk to each other.
 *
 *
 *
 * <h2>Implementation Note</h2>
 * <p>
 * For better performance, {@link Pipe} uses lower-level {@link Command} abstraction
 * to send data, instead of typed proxy object. This allows the writer to send data
 * without blocking until the arrival of the data is confirmed.
 *
 * @author Kohsuke Kawaguchi
 */
public final class Pipe implements Serializable {
    private InputStream in;
    private OutputStream out;

    private Pipe(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
    }

    public InputStream getIn() {
        return in;
    }

    public OutputStream getOut() {
        return out;
    }

    public static Pipe create() {
        // OutputStream will be created on the target
        return new Pipe(new PipedInputStream(),null);
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        if(in!=null && out==null) {
            // remote will write to local
            PipedOutputStream pos = new PipedOutputStream((PipedInputStream)in);
            int oid = Channel.current().exportedObjects.intern(pos);

            oos.writeBoolean(true); // marker
            oos.writeInt(oid);
        } else {
            // TODO: remote will read from local
            throw new UnsupportedOperationException();
        }
    }

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        final Channel channel = Channel.current();
        assert channel !=null;

        if(ois.readBoolean()) {
            // local will write to remote
            final int oid = ois.readInt();

            in = null;
            out = new BufferedOutputStream(new OutputStream() {
                public void write(int b) throws IOException {
                    write(new byte[]{(byte)b},0,1);
                }

                public void write(byte b[], int off, int len) throws IOException {
                    if(off==0 && len==b.length)
                        write(b);
                    else {
                        byte[] buf = new byte[len];
                        System.arraycopy(b,off,buf,0,len);
                        write(buf);
                    }
                }

                public void write(byte b[]) throws IOException {
                    channel.send(new Chunk(oid,b));
                }

                public void close() throws IOException {
                    channel.send(new EOF(oid));
                }
            });
        } else {
            // TODO
            throw new UnsupportedOperationException();
        }
    }

    /**
     * {@link Command} for sending bytes.
     */
    private static final class Chunk extends Command {
        private final int oid;
        private final byte[] buf;

        public Chunk(int oid, byte[] buf) {
            this.oid = oid;
            this.buf = buf;
        }

        protected void execute(Channel channel) {
            OutputStream os = (OutputStream) channel.exportedObjects.get(oid);
            try {
                os.write(buf);
            } catch (IOException e) {
                // ignore errors
            }
        }

        public String toString() {
            return "Pipe.Chunk("+oid+","+buf.length+")";
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * {@link Command} for sending EOF.
     */
    private static final class EOF extends Command {
        private final int oid;

        public EOF(int oid) {
            this.oid = oid;
        }


        protected void execute(Channel channel) {
            OutputStream os = (OutputStream) channel.exportedObjects.get(oid);
            try {
                os.close();
            } catch (IOException e) {
                // ignore errors
            }
        }

        public String toString() {
            return "Pipe.EOF("+oid+")";
        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
