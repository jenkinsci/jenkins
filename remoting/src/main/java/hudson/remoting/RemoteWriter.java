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
import java.io.Serializable;
import java.io.Writer;

/**
 * {@link Writer} that can be sent over to the remote {@link Channel},
 * so that the remote {@link Callable} can write to a local {@link Writer}.
 *
 * <h2>Usage</h2>
 * <pre>
 * final Writer out = new RemoteWriter(w);
 *
 * channel.call(new Callable() {
 *   public Object call() {
 *     // this will write to 'w'.
 *     out.write(...);
 *   }
 * });
 * </pre>
 *
 * @see RemoteInputStream
 * @author Kohsuke Kawaguchi
 */
public final class RemoteWriter extends Writer implements Serializable {
    /**
     * On local machine, this points to the {@link Writer} where
     * the data will be sent ultimately.
     *
     * On remote machine, this points to {@link ProxyOutputStream} that
     * does the network proxy.
     */
    private transient Writer core;

    public RemoteWriter(Writer core) {
        this.core = core;
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        int id = Channel.current().export(core);
        oos.writeInt(id);
    }

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        final Channel channel = Channel.current();
        assert channel !=null;

        this.core = new ProxyWriter(channel, ois.readInt());
    }

    private static final long serialVersionUID = 1L;


//
//
// delegation to core
//
//
    public void write(int c) throws IOException {
        core.write(c);
    }

    public void write(char[] cbuf) throws IOException {
        core.write(cbuf);
    }

    public void write(char[] cbuf, int off, int len) throws IOException {
        core.write(cbuf, off, len);
    }

    public void write(String str) throws IOException {
        core.write(str);
    }

    public void write(String str, int off, int len) throws IOException {
        core.write(str, off, len);
    }

    public Writer append(CharSequence csq) throws IOException {
        return core.append(csq);
    }

    public Writer append(CharSequence csq, int start, int end) throws IOException {
        return core.append(csq, start, end);
    }

    public Writer append(char c) throws IOException {
        return core.append(c);
    }

    public void flush() throws IOException {
        core.flush();
    }

    public void close() throws IOException {
        core.close();
    }
}
