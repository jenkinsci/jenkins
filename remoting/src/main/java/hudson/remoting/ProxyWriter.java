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

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

/**
 * {@link Writer} that sends bits to an exported
 * {@link Writer} on a remote machine.
 */
final class ProxyWriter extends Writer {
    private Channel channel;
    private int oid;

    /**
     * If bytes are written to this stream before it's connected
     * to a remote object, bytes will be stored in this buffer.
     */
    private CharArrayWriter tmp;

    /**
     * Set to true if the stream is closed.
     */
    private boolean closed;

    /**
     * Creates unconnected {@link ProxyWriter}.
     * The returned stream accepts data right away, and
     * when it's {@link #connect(Channel,int) connected} later,
     * the data will be sent at once to the remote stream.
     */
    public ProxyWriter() {
    }

    /**
     * Creates an already connected {@link ProxyWriter}.
     *
     * @param oid
     *      The object id of the exported {@link OutputStream}.
     */
    public ProxyWriter(Channel channel, int oid) throws IOException {
        connect(channel,oid);
    }

    /**
     * Connects this stream to the specified remote object.
     */
    synchronized void connect(Channel channel, int oid) throws IOException {
        if(this.channel!=null)
            throw new IllegalStateException("Cannot connect twice");
        this.channel = channel;
        this.oid = oid;

        // if we already have bytes to write, do so now.
        if(tmp!=null) {
            write(tmp.toCharArray());
            tmp = null;
        }
        if(closed)  // already marked closed?
            close();
    }

    public void write(int c) throws IOException {
        write(new char[]{(char)c},0,1);
    }

    public void write(char[] cbuf, int off, int len) throws IOException {
        if(closed)
            throw new IOException("stream is already closed");
        if(off==0 && len==cbuf.length)
            write(cbuf);
        else {
            char[] buf = new char[len];
            System.arraycopy(cbuf,off,buf,0,len);
            write(buf);
        }
    }



    public synchronized void write(char[] cbuf) throws IOException {
        if(closed)
            throw new IOException("stream is already closed");
        if(channel==null) {
            if(tmp==null)
                tmp = new CharArrayWriter();
            tmp.write(cbuf);
        } else {
            channel.send(new Chunk(oid,cbuf));
        }
    }

    public void flush() throws IOException {
        // noop
    }

    public synchronized void close() throws IOException {
        closed = true;
        if(channel!=null) {
            channel.send(new EOF(oid));
            channel = null;
            oid = -1;
        }
    }

    protected void finalize() throws Throwable {
        super.finalize();
        close();
    }

    /**
     * {@link Command} for sending bytes.
     */
    private static final class Chunk extends Command {
        private final int oid;
        private final char[] buf;

        public Chunk(int oid, char[] buf) {
            this.oid = oid;
            this.buf = buf;
        }

        protected void execute(Channel channel) {
            Writer os = (Writer) channel.getExportedObject(oid);
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
