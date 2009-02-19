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
        if(core==null)
            throw new IllegalArgumentException();
        this.core = core;
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        int id = Channel.current().export(core,false); // this export is unexported in ProxyOutputStream.finalize() 
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
