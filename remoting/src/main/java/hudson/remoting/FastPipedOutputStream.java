/*
 * @(#)$Id: FastPipedOutputStream.java 3619 2008-03-26 07:23:03Z yui $
 *
 * Copyright 2006-2008 Makoto YUI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Makoto YUI - initial implementation
 */
package hudson.remoting;

import java.io.OutputStream;
import java.io.IOException;

/**
 * This class is equivalent to <code>java.io.PipedOutputStream</code>. In the
 * interface it only adds a constructor which allows for specifying the buffer
 * size. Its implementation, however, is much simpler and a lot more efficient
 * than its equivalent. It doesn't rely on polling. Instead it uses proper
 * synchronization with its counterpart {@link FastPipedInputStream}.
 *
 * @author WD
 * @link http://developer.java.sun.com/developer/bugParade/bugs/4404700.html
 * @see FastPipedOutputStream
 */
public class FastPipedOutputStream extends OutputStream {

    FastPipedInputStream sink;

    /**
     * Creates an unconnected PipedOutputStream.
     */
    public FastPipedOutputStream() {
        super();
    }

    /**
     * Creates a PipedOutputStream with a default buffer size and connects it to
     * <code>sink</code>.
     * @exception IOException It was already connected.
     */
    public FastPipedOutputStream(FastPipedInputStream sink) throws IOException {
        this(sink, 65536);
    }

    /**
     * Creates a PipedOutputStream with buffer size <code>bufferSize</code> and
     * connects it to <code>sink</code>.
     * @exception IOException It was already connected.
     */
    public FastPipedOutputStream(FastPipedInputStream sink, int bufferSize) throws IOException {
        super();
        if(sink != null) {
            connect(sink);
            sink.buffer = new byte[bufferSize];
        }
    }

    /**
     * @exception IOException The pipe is not connected.
     */
    @Override
    public void close() throws IOException {
        if(sink == null) {
            throw new IOException("Unconnected pipe");
        }
        synchronized(sink.buffer) {
            sink.closed = true;
            flush();
        }
    }

    /**
     * @exception IOException The pipe is already connected.
     */
    public void connect(FastPipedInputStream sink) throws IOException {
        if(this.sink != null) {
            throw new IOException("Pipe already connected");
        }
        this.sink = sink;
        sink.source = this;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        close();
    }

    @Override
    public void flush() throws IOException {
        synchronized(sink.buffer) {
            // Release all readers.
            sink.buffer.notifyAll();
        }
    }

    public void write(int b) throws IOException {
        write(new byte[] { (byte) b });
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    /**
     * @exception IOException The pipe is not connected or a reader has closed it.
     */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if(sink == null) {
            throw new IOException("Unconnected pipe");
        }
        if(sink.closed) {
            throw new IOException("Broken pipe");
        }

        while (len>0) {
            synchronized(sink.buffer) {
                if(sink.writePosition == sink.readPosition && sink.writeLaps > sink.readLaps) {
                    // The circular buffer is full, so wait for some reader to consume
                    // something.
                    try {
                        sink.buffer.wait();
                    } catch (InterruptedException e) {
                        throw new IOException(e.getMessage());
                    }
                    // Try again.
                    continue;
                }

                // Don't write more than the capacity indicated by len or the space
                // available in the circular buffer.
                int amount = Math.min(len, (sink.writePosition < sink.readPosition ? sink.readPosition
                        : sink.buffer.length)
                        - sink.writePosition);
                System.arraycopy(b, off, sink.buffer, sink.writePosition, amount);
                sink.writePosition += amount;

                if(sink.writePosition == sink.buffer.length) {
                    sink.writePosition = 0;
                    ++sink.writeLaps;
                }

                off += amount;
                len -= amount;

                sink.buffer.notifyAll();
            }
        }
    }
}
