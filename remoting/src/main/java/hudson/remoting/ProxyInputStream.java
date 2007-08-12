package hudson.remoting;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;

/**
 * {@link InputStream} that reads bits from an exported
 * {@link InputStream} on a remote machine.
 *
 * <p>
 * TODO: pre-fetch bytes in advance
 *
 * @author Kohsuke Kawaguchi
 */
final class ProxyInputStream extends InputStream {
    private Channel channel;
    private int oid;

    /**
     * Creates an already connected {@link ProxyOutputStream}.
     *
     * @param oid
     *      The object id of the exported {@link OutputStream}.
     */
    public ProxyInputStream(Channel channel, int oid) throws IOException {
        this.channel = channel;
        this.oid = oid;
    }

    @Override
    public int read() throws IOException {
        try {
            Buffer buf = new Chunk(oid, 1).call(channel);
            if(buf.len==1)
                return buf.buf[0];
            else
                return -1;
        } catch (InterruptedException e) {
            // pretend EOF
            Thread.currentThread().interrupt(); // process interrupt later
            close();
            return -1;
        }
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {
        try {
            Buffer buf = new Chunk(oid,len).call(channel);
            if(buf.len==-1) return -1;
            System.arraycopy(buf.buf,0,b,off,buf.len);
            return buf.len;
        } catch (InterruptedException e) {
            // pretend EOF
            Thread.currentThread().interrupt(); // process interrupt later
            close();
            return -1;
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if(channel!=null) {
            channel.send(new EOF(oid));
            channel = null;
            oid = -1;
        }
    }

    private static final class Buffer implements Serializable {
        byte[] buf;
        int len;

        public Buffer(int len) {
            this.buf = new byte[len];
        }

        public void read(InputStream in) throws IOException {
            len = in.read(buf,0,buf.length);
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Command to fetch bytes.
     */
    private static final class Chunk extends Request<Buffer,IOException> {
        private final int oid;
        private final int len;

        public Chunk(int oid, int len) {
            this.oid = oid;
            this.len = len;
        }

        protected Buffer perform(Channel channel) throws IOException {
            InputStream in = (InputStream) channel.getExportedObject(oid);

            Buffer buf = new Buffer(len);
            buf.read(in);
            return buf;
        }
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
            InputStream in = (InputStream) channel.getExportedObject(oid);
            channel.unexport(oid);
            try {
                in.close();
            } catch (IOException e) {
                // ignore errors
            }
        }

        public String toString() {
            return "EOF("+oid+")";
        }

        private static final long serialVersionUID = 1L;
    }
}
