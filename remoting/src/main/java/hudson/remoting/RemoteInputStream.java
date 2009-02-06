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
