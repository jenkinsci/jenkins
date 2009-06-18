/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.remoting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * {@link OutputStream} that sends bits to an exported
 * {@link OutputStream} on a remote machine.
 */
final class ProxyOutputStream extends OutputStream {
    private Channel channel;
    private int oid;

    /**
     * If bytes are written to this stream before it's connected
     * to a remote object, bytes will be stored in this buffer.
     */
    private ByteArrayOutputStream tmp;

    /**
     * Set to true if the stream is closed.
     */
    private boolean closed;

    /**
     * Creates unconnected {@link ProxyOutputStream}.
     * The returned stream accepts data right away, and
     * when it's {@link #connect(Channel,int) connected} later,
     * the data will be sent at once to the remote stream.
     */
    public ProxyOutputStream() {
    }

    /**
     * Creates an already connected {@link ProxyOutputStream}.
     *
     * @param oid
     *      The object id of the exported {@link OutputStream}.
     */
    public ProxyOutputStream(Channel channel, int oid) throws IOException {
        connect(channel,oid);
    }

    /**
     * Connects this stream to the specified remote object.
     */
    synchronized void connect(Channel channel, int oid) throws IOException {
        if(this.channel!=null)
            throw new IllegalStateException("Cannot connect twice");
        if(oid==0)
            throw new IllegalArgumentException("oid=0");
        this.channel = channel;
        this.oid = oid;

        // if we already have bytes to write, do so now.
        if(tmp!=null) {
            channel.send(new Chunk(oid,tmp.toByteArray()));
            tmp = null;
        }
        if(closed)  // already marked closed?
            doClose();
    }

    public void write(int b) throws IOException {
        write(new byte[]{(byte)b},0,1);
    }

    public void write(byte b[], int off, int len) throws IOException {
        if(closed)
            throw new IOException("stream is already closed");
        if(off==0 && len==b.length)
            write(b);
        else {
            byte[] buf = new byte[len];
            System.arraycopy(b,off,buf,0,len);
            write(buf);
        }
    }

    public synchronized void write(byte b[]) throws IOException {
        if(closed)
            throw new IOException("stream is already closed");
        if(channel==null) {
            if(tmp==null)
                tmp = new ByteArrayOutputStream();
            tmp.write(b);
        } else {
            channel.send(new Chunk(oid,b));
        }
    }


    public void flush() throws IOException {
        if(channel!=null)
            channel.send(new Flush(oid));
    }

    public synchronized void close() throws IOException {
        closed = true;
        if(channel!=null)
            doClose();
    }

    private void doClose() throws IOException {
        channel.send(new EOF(oid));
        channel = null;
        oid = -1;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        // if we haven't done so, release the exported object on the remote side.
        if(channel!=null) {
            channel.send(new Unexport(oid));
            channel = null;
            oid = -1;
        }
    }

    /**
     * {@link Command} for sending bytes.
     */
    private static final class Chunk extends Command {
        private final int oid;
        private final byte[] buf;

        public Chunk(int oid, byte[] buf) {
            // to improve the performance when a channel is used purely as a pipe,
            // don't record the stack trace. On FilePath.writeToTar case, the stack trace and the OOS header
            // takes up about 1.5K.
            super(false);
            this.oid = oid;
            this.buf = buf;
        }

        protected void execute(Channel channel) {
            OutputStream os = (OutputStream) channel.getExportedObject(oid);
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
     * {@link Command} for flushing.
     */
    private static final class Flush extends Command {
        private final int oid;

        public Flush(int oid) {
            super(false);
            this.oid = oid;
        }

        protected void execute(Channel channel) {
            OutputStream os = (OutputStream) channel.getExportedObject(oid);
            try {
                os.flush();
            } catch (IOException e) {
                // ignore errors
            }
        }

        public String toString() {
            return "Pipe.Flush("+oid+")";
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * {@link Command} for releasing an export table.
     *
     * <p>
     * Unlike {@link EOF}, this just unexports but not closes the stream.
     */
    private static class Unexport extends Command {
        private final int oid;

        public Unexport(int oid) {
            this.oid = oid;
        }

        protected void execute(Channel channel) {
            channel.unexport(oid);
        }

        public String toString() {
            return "Pipe.Unexport("+oid+")";
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
            OutputStream os = (OutputStream) channel.getExportedObject(oid);
            channel.unexport(oid);
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
}
