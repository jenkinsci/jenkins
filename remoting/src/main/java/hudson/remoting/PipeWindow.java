/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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

import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.*;

/**
 * Keeps track of the number of bytes that the sender can send without overwhelming the receiver of the pipe.
 *
 * <p>
 * {@link OutputStream} is a blocking operation in Java, so when we send byte[] to the remote to write to
 * {@link OutputStream}, it needs to be done in a separate thread (or else we'll fail to attend to the channel
 * in timely fashion.) This in turn means the byte[] being sent needs to go to a queue between a
 * channel reader thread and I/O processing thread, and thus in turn means we need some kind of throttling
 * mechanism, or else the queue can grow too much.
 *
 * <p>
 * This implementation solves the problem by using TCP/IP like window size tracking. The sender allocates
 * a fixed length window size. Every time the sender sends something we reduce this value. When the receiver
 * writes data to {@link OutputStream}, it'll send back the "ack" command, which adds to this value, allowing
 * the sender to send more data.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class PipeWindow {
    abstract void increase(int delta);

    abstract int peek();

    /**
     * Blocks until some space becomes available.
     */
    abstract int get() throws InterruptedException;

    abstract void decrease(int delta);

    /**
     * Fake implementation used when the receiver side doesn't support throttling.
     */
    static final PipeWindow FAKE = new PipeWindow() {
        void increase(int delta) {
        }

        int peek() {
            return Integer.MAX_VALUE;
        }

        int get() throws InterruptedException {
            return Integer.MAX_VALUE;
        }

        void decrease(int delta) {
        }
    };

    static final class Key {
        public final int oid;

        Key(int oid) {
            this.oid = oid;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            return oid == ((Key) o).oid;
        }

        @Override
        public int hashCode() {
            return oid;
        }
    }

    static class Real extends PipeWindow {
        private int available;
        private long written;
        private long acked;
        private final int oid;
        /**
         * The only strong reference to the key, which in turn
         * keeps this object accessible in {@link Channel#pipeWindows}.
         */
        private final Key key;

        Real(Key key, int initialSize) {
            this.key = key;
            this.oid = key.oid;
            this.available = initialSize;
        }

        public synchronized void increase(int delta) {
            if (LOGGER.isLoggable(INFO))
                LOGGER.info(String.format("increase(%d,%d)->%d",oid,delta,delta+available));
            available += delta;
            acked += delta;
            notifyAll();
        }

        public synchronized int peek() {
            return available;
        }

        /**
         * Blocks until some space becomes available.
         *
         * <p>
         * If the window size is empty, induce some delay outside the synchronized block,
         * to avoid fragmenting the window size. That is, if a bunch of small ACKs come in a sequence,
         * bundle them up into a bigger size before making a call.
         */
        public int get() throws InterruptedException {
            synchronized (this) {
                if (available>0)
                    return available;

                while (available==0) {
                    wait();
                }
            }

            Thread.sleep(10);

            synchronized (this) {
                return available;
            }
        }

        public synchronized void decrease(int delta) {
            if (LOGGER.isLoggable(INFO))
                LOGGER.info(String.format("decrease(%d,%d)->%d",oid,delta,available-delta));
            available -= delta;
            written+= delta;
            if (available<0)
                throw new AssertionError();
        }
    }

    private static final Logger LOGGER = Logger.getLogger(PipeWindow.class.getName());
}
