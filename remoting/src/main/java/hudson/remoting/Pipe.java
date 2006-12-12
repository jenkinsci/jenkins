package hudson.remoting;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.BufferedOutputStream;
import java.io.PipedOutputStream;

/**
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
            IStream proxy = Channel.current().export(IStream.class, new IStream() {
                PipedOutputStream pos = new PipedOutputStream((PipedInputStream)in);
                public void write(byte[] buf) throws IOException {
                    pos.write(buf);
                }

                public void close() throws IOException {
                    pos.close();
                }
            });

            oos.writeBoolean(true); // marker
            oos.writeObject(proxy);
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
            final IStream proxy = (IStream)ois.readObject();
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
                    proxy.write(b);
                }

                public void close() throws IOException {
                    proxy.close();
                }
            });
        } else {
            // TODO
            throw new UnsupportedOperationException();
        }
    }

    private static interface IStream {
        void write(byte[] buf) throws IOException;
        void close() throws IOException;
    }

    private static final long serialVersionUID = 1L;
}
