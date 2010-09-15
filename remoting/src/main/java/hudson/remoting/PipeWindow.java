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


    static class Real extends PipeWindow {
        private int available;

        Real(int initialSize) {
            this.available = initialSize;
        }

        public synchronized void increase(int delta) {
            available += delta;
            notifyAll();
        }

        public synchronized int peek() {
            return available;
        }

        /**
         * Blocks until some space becomes available.
         */
        public synchronized int get() throws InterruptedException {
            while (available==0)
                wait();
            return available;
        }

        public synchronized void decrease(int delta) {
            available -= delta;
            if (available<0)
                throw new AssertionError();
        }
    }
}
